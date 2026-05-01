package com.dev1lroot.aapps.observer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FFmpegStreamer(
    private val context: Context,
    private val onState: (StreamingState, String) -> Unit,
) {
    private val cameraThread = HandlerThread("Camera").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val imageThread = HandlerThread("ImageConvert").also { it.start() }
    private val imageHandler = Handler(imageThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: android.hardware.camera2.CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var audioRecord: AudioRecord? = null

    private var videoPipePath: String? = null
    private var audioPipePath: String? = null

    private val frameQueue = LinkedBlockingQueue<ByteArray>(3)

    var capturedWidth = 0; private set
    var capturedHeight = 0; private set
    var sensorOrientation = 90; private set

    @Volatile var isPreviewing = false; private set
    @Volatile var isStreaming = false; private set

    // ── Overlay ──────────────────────────────────────────────────────────────

    @Volatile var overlayConfig: OverlayConfig = OverlayConfig()

    @Volatile private var currentLat: Double? = null
    @Volatile private var currentLon: Double? = null
    @Volatile private var currentCompass: Float? = null

    private val overlayRenderer = OverlayRenderer()
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val sensorManager = context.getSystemService(SensorManager::class.java)

    private val locationListener = LocationListener { loc ->
        currentLat = loc.latitude
        currentLon = loc.longitude
    }

    private val rotVecListener = object : SensorEventListener {
        private val rotMatrix = FloatArray(9)
        private val orientation = FloatArray(3)
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
            SensorManager.getOrientation(rotMatrix, orientation)
            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            currentCompass = (azimuth + 360f) % 360f
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    @SuppressLint("MissingPermission")
    private fun startSensors() {
        val rotVec = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotVec != null) {
            sensorManager.registerListener(
                rotVecListener, rotVec, SensorManager.SENSOR_DELAY_UI, imageHandler
            )
        }
        try {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
            provider?.let {
                locationManager.requestLocationUpdates(
                    it, 1000L, 0f, locationListener, Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {}
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(rotVecListener)
        try { locationManager.removeUpdates(locationListener) } catch (_: Exception) {}
    }

    // ── Camera open + preview ────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun openCamera(textureView: TextureView, reqWidth: Int, reqHeight: Int) {
        val manager = context.getSystemService(CameraManager::class.java)
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.first()

        val chars = manager.getCameraCharacteristics(cameraId)
        sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val bestSize = chooseBestSize(
            map.getOutputSizes(ImageFormat.YUV_420_888), reqWidth, reqHeight
        )
        capturedWidth = bestSize.width
        capturedHeight = bestSize.height

        configureTransform(textureView)

        val st = textureView.surfaceTexture ?: return
        st.setDefaultBufferSize(capturedWidth, capturedHeight)
        val previewSurface = Surface(st)

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startPreviewSession(camera, previewSurface)
                startSensors()
            }
            override fun onDisconnected(camera: CameraDevice) = camera.close()
            override fun onError(camera: CameraDevice, error: Int) = camera.close()
        }, cameraHandler)
    }

    private fun startPreviewSession(camera: CameraDevice, previewSurface: Surface) {
        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            .apply { addTarget(previewSurface) }
        camera.createCaptureSession(
            listOf(previewSurface),
            object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(req.build(), null, cameraHandler)
                    isPreviewing = true
                }
                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {}
            }, cameraHandler
        )
    }

    // ── Stream start ─────────────────────────────────────────────────────────

    fun startStream(
        textureView: TextureView,
        url: String,
        fps: Int,
        videoBitrateKbps: Int,
        audioBitrateKbps: Int,
    ) {
        if (isStreaming) return
        onState(StreamingState.Connecting, "Connecting…")

        val vPipe = FFmpegKitConfig.registerNewFFmpegPipe(context)
        val aPipe = FFmpegKitConfig.registerNewFFmpegPipe(context)
        videoPipePath = vPipe
        audioPipePath = aPipe

        val reader = ImageReader.newInstance(capturedWidth, capturedHeight, ImageFormat.YUV_420_888, 4)
        imageReader = reader

        val st = textureView.surfaceTexture!!
        st.setDefaultBufferSize(capturedWidth, capturedHeight)
        val previewSurface = Surface(st)
        val readerSurface = reader.surface

        captureSession?.close()
        captureSession = null

        val cam = cameraDevice ?: run {
            onState(StreamingState.Error("Camera not ready"), "Camera not ready")
            return
        }

        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(previewSurface)
            addTarget(readerSurface)
        }

        cam.createCaptureSession(
            listOf(previewSurface, readerSurface),
            object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(req.build(), null, cameraHandler)
                    isStreaming = true

                    reader.setOnImageAvailableListener({ r ->
                        val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val frame = image.toI420()
                        image.close()

                        val cfg = overlayConfig
                        if (cfg.showCrosshair || cfg.showTimestamp || cfg.showGps || cfg.showCompass) {
                            overlayRenderer.renderOntoI420(
                                frame, capturedWidth, capturedHeight, sensorOrientation, cfg,
                                if (cfg.showTimestamp) sdf.format(Date()) else "",
                                if (cfg.showGps) currentLat else null,
                                if (cfg.showGps) currentLon else null,
                                if (cfg.showCompass) currentCompass else null,
                            )
                        }

                        frameQueue.offer(frame)
                    }, imageHandler)

                    startVideoWriter(vPipe)
                    startAudioCapture(aPipe)
                    launchFFmpeg(url, fps, videoBitrateKbps, audioBitrateKbps, vPipe, aPipe)
                }
                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                    onState(StreamingState.Error("Session failed"), "Session failed")
                }
            }, cameraHandler
        )
    }

    // ── Stream stop ──────────────────────────────────────────────────────────

    fun stopStream(textureView: TextureView) {
        isStreaming = false
        FFmpegKit.cancel()
        closePipes()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        imageReader?.close()
        imageReader = null
        frameQueue.clear()

        val cam = cameraDevice ?: return
        val st = textureView.surfaceTexture ?: return
        st.setDefaultBufferSize(capturedWidth, capturedHeight)
        val previewSurface = Surface(st)
        captureSession?.close()
        startPreviewSession(cam, previewSurface)
    }

    fun release() {
        isStreaming = false
        isPreviewing = false
        FFmpegKit.cancel()
        closePipes()
        captureSession?.close()
        cameraDevice?.close()
        audioRecord?.release()
        imageReader?.close()
        cameraThread.quitSafely()
        imageThread.quitSafely()
        stopSensors()
        overlayRenderer.release()
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun startVideoWriter(pipePath: String) {
        Thread {
            try {
                FileOutputStream(pipePath).use { pipe ->
                    while (isStreaming) {
                        val frame = frameQueue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                        pipe.write(frame)
                    }
                }
            } catch (_: IOException) {}
        }.apply { isDaemon = true; start() }
    }

    private fun startAudioCapture(pipePath: String) {
        val minBuf = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val ar = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER,
            44100,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 4,
        )
        audioRecord = ar
        ar.startRecording()

        Thread {
            try {
                FileOutputStream(pipePath).use { pipe ->
                    val buf = ByteArray(minBuf)
                    while (isStreaming) {
                        val n = ar.read(buf, 0, buf.size)
                        if (n > 0) pipe.write(buf, 0, n)
                    }
                }
            } catch (_: IOException) {}
        }.apply { isDaemon = true; start() }
    }

    private fun launchFFmpeg(
        url: String,
        fps: Int,
        vBitrateKbps: Int,
        aBitrateKbps: Int,
        vPipe: String,
        aPipe: String,
    ) {
        val transpose = "" // DO NOT FUCKING TOUCH
        val cmd = buildString {
            append("-f rawvideo -pixel_format yuv420p")
            append(" -video_size ${capturedWidth}x${capturedHeight}")
            append(" -framerate $fps -thread_queue_size 512 -i $vPipe")
            append(" -f s16le -ar 44100 -ac 2 -thread_queue_size 512 -i $aPipe")
            if (transpose.isNotEmpty()) append(" $transpose")
            append(" -c:v libx264 -preset ultrafast -tune zerolatency")
            append(" -b:v ${vBitrateKbps}k -minrate ${vBitrateKbps}k")
            append(" -maxrate ${vBitrateKbps}k -bufsize ${vBitrateKbps * 2}k")
            append(" -x264-params nal-hrd=cbr:force-cfr=1")
            append(" -c:a aac -b:a ${aBitrateKbps}k -ar 44100 -ac 2")
            append(" -f flv $url")
        }

        var connectionReported = false
        FFmpegKit.executeAsync(
            cmd,
            FFmpegSessionCompleteCallback { session ->
                isStreaming = false
                val rc = session.returnCode
                when {
                    ReturnCode.isCancel(rc) -> onState(StreamingState.Idle, "")
                    ReturnCode.isSuccess(rc) -> onState(StreamingState.Idle, "Disconnected")
                    else -> {
                        val tail = session.logsAsString.trimEnd().lines().takeLast(3).joinToString(" | ")
                        onState(StreamingState.Error(tail), "Error — check logs")
                    }
                }
            },
            LogCallback { log ->
                if (!connectionReported) {
                    val msg = log.getMessage()
                    if (msg.contains("Output #0") || msg.contains("muxing overhead")) {
                        connectionReported = true
                        onState(StreamingState.Streaming, "Live")
                    }
                }
            },
            null, // StatisticsCallback not needed
        )
    }

    private fun closePipes() {
        videoPipePath?.let { FFmpegKitConfig.closeFFmpegPipe(it) }
        audioPipePath?.let { FFmpegKitConfig.closeFFmpegPipe(it) }
        videoPipePath = null
        audioPipePath = null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun chooseBestSize(sizes: Array<Size>, reqW: Int, reqH: Int): Size {
        val (tw, th) = if (sensorOrientation % 180 != 0)
            Pair(max(reqW, reqH), min(reqW, reqH))
        else
            Pair(reqW, reqH)
        val targetArea = tw.toLong() * th
        return sizes.minByOrNull { abs(it.width.toLong() * it.height - targetArea) }
            ?: Size(1280, 720)
    }

    fun configureTransform(textureView: TextureView) {
        val vw = textureView.width.takeIf { it > 0 } ?: return
        val vh = textureView.height.takeIf { it > 0 } ?: return
        val cx = vw / 2f
        val cy = vh / 2f
        val matrix = Matrix()
        if (sensorOrientation % 180 != 0) {
            //matrix.postRotate(90f, cx, cy) // DO NOT FUCKING TOUCH
            val scale = max(vw.toFloat() / capturedHeight, vh.toFloat() / capturedWidth)
            matrix.postScale(scale, scale, cx, cy)
        }
        textureView.setTransform(matrix)
    }

    // YUV_420_888 → contiguous I420 (yuv420p) for FFmpeg
    private fun Image.toI420(): ByteArray {
        val w = width
        val h = height
        val chromaH = h / 2
        val chromaW = w / 2
        val out = ByteArray(w * h + 2 * chromaW * chromaH)
        var offset = 0

        val yPlane = planes[0]
        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        for (row in 0 until h) {
            yBuf.position(row * yRowStride)
            yBuf.get(out, offset, w)
            offset += w
        }

        for (planeIdx in 1..2) {
            val plane = planes[planeIdx]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            for (row in 0 until chromaH) {
                if (pixelStride == 1) {
                    buf.position(row * rowStride)
                    buf.get(out, offset, chromaW)
                    offset += chromaW
                } else {
                    for (col in 0 until chromaW) {
                        out[offset++] = buf[row * rowStride + col * pixelStride]
                    }
                }
            }
        }
        return out
    }
}
