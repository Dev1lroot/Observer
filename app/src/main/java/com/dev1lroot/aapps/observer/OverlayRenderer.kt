package com.dev1lroot.aapps.observer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

class OverlayRenderer {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }
    private val bgPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
    }

    private var textBitmap: Bitmap? = null
    private var lastLinesKey = ""

    fun renderOntoI420(
        i420: ByteArray,
        frameW: Int,
        frameH: Int,
        sensorOrientation: Int,
        config: OverlayConfig,
        timestamp: String,
        lat: Double?,
        lon: Double?,
        compass: Float?,
    ) {
        if (config.showCrosshair) drawCrosshair(i420, frameW, frameH)

        val lines = mutableListOf<String>()
        if (config.showTimestamp && timestamp.isNotEmpty()) lines.add(timestamp)
        if (config.showGps) lines.add(
            if (lat != null && lon != null) "%.5f, %.5f".format(lat, lon) else "GPS: acquiring…"
        )
        if (config.showCompass) lines.add(
            if (compass != null) "%.1f° %s".format(compass, bearingToCardinal(compass)) else "Compass: …"
        )

        if (lines.isEmpty()) return

        val key = lines.joinToString("\n")
        if (key != lastLinesKey || textBitmap == null) {
            lastLinesKey = key
            textBitmap?.recycle()
            textBitmap = renderLines(lines)
        }

        val bmp = textBitmap ?: return
        val margin = 16

        // FFmpeg transpose mapping to portrait bottom-left:
        //   sensorOrientation=90  → transpose=2 (90° CCW): top-left of landscape → bottom-left of portrait
        //   sensorOrientation=270 → transpose=1 (90° CW):  bottom-right of landscape → bottom-left of portrait
        //   sensorOrientation=0/180 → no transpose: bottom-left of frame → bottom-left of output
        val (dx, dy) = when (sensorOrientation) {
            90  -> Pair(margin, margin)
            270 -> Pair(frameW - bmp.width - margin, frameH - bmp.height - margin)
            else -> Pair(margin, frameH - bmp.height - margin)
        }

        blitArgbToI420(i420, frameW, frameH, bmp, dx, dy)
    }

    private fun renderLines(lines: List<String>): Bitmap {
        val fm = textPaint.fontMetrics
        val lineH = (-fm.ascent + fm.descent + 4f).toInt()
        val textBaseline = -fm.ascent
        val pad = 10
        val bmpW = lines.maxOf { textPaint.measureText(it).toInt() } + pad * 2
        val bmpH = lines.size * lineH + pad * 2

        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawRect(0f, 0f, bmpW.toFloat(), bmpH.toFloat(), bgPaint)
        for ((i, line) in lines.withIndex()) {
            canvas.drawText(line, pad.toFloat(), pad + i * lineH + textBaseline, textPaint)
        }
        return bmp
    }

    // Writes crosshair directly into the Y plane — no color conversion needed.
    private fun drawCrosshair(i420: ByteArray, w: Int, h: Int) {
        val cx = w / 2
        val cy = h / 2
        val halfLen = 40
        val gap = 6
        val yW: Byte = 235.toByte()
        val yB: Byte = 16.toByte()

        // Horizontal arm
        for (x in max(0, cx - halfLen)..min(w - 1, cx + halfLen)) {
            if (x in (cx - gap)..(cx + gap)) continue
            if (cy - 1 >= 0) i420[(cy - 1) * w + x] = yB
            if (cy + 1 < h)  i420[(cy + 1) * w + x] = yB
            i420[cy * w + x] = yW
        }

        // Vertical arm
        for (y in max(0, cy - halfLen)..min(h - 1, cy + halfLen)) {
            if (y in (cy - gap)..(cy + gap)) continue
            if (cx - 1 >= 0) i420[y * w + (cx - 1)] = yB
            if (cx + 1 < w)  i420[y * w + (cx + 1)] = yB
            i420[y * w + cx] = yW
        }
    }

    // Alpha-composite an ARGB bitmap into a planar I420 frame.
    private fun blitArgbToI420(
        i420: ByteArray,
        fw: Int, fh: Int,
        bmp: Bitmap,
        destX: Int, destY: Int,
    ) {
        val bw = bmp.width
        val bh = bmp.height
        val pixels = IntArray(bw * bh)
        bmp.getPixels(pixels, 0, bw, 0, 0, bw, bh)

        val chromaBase = fw * fh
        val chromaW = fw / 2
        val chromaH = fh / 2

        for (row in 0 until bh) {
            for (col in 0 until bw) {
                val fx = destX + col
                val fy = destY + row
                if (fx < 0 || fx >= fw || fy < 0 || fy >= fh) continue

                val p = pixels[row * bw + col]
                val a = (p ushr 24) and 0xFF
                if (a < 8) continue

                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                // BT.601 studio swing RGB→Y
                val newY = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val yIdx = fy * fw + fx
                val existingY = i420[yIdx].toInt() and 0xFF
                i420[yIdx] = ((existingY * (255 - a) + newY * a) / 255).toByte()

                // Update chroma once per 2×2 block
                if (fx and 1 == 0 && fy and 1 == 0) {
                    val uvRow = fy / 2
                    val uvCol = fx / 2
                    if (uvRow < chromaH && uvCol < chromaW) {
                        val uIdx = chromaBase + uvRow * chromaW + uvCol
                        val vIdx = chromaBase + chromaW * chromaH + uvRow * chromaW + uvCol

                        val newU = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                        val newV = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                        val existingU = i420[uIdx].toInt() and 0xFF
                        val existingV = i420[vIdx].toInt() and 0xFF
                        i420[uIdx] = ((existingU * (255 - a) + newU * a) / 255).toByte()
                        i420[vIdx] = ((existingV * (255 - a) + newV * a) / 255).toByte()
                    }
                }
            }
        }
    }

    private fun bearingToCardinal(bearing: Float): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[((bearing + 22.5f) / 45f).toInt() % 8]
    }

    fun release() {
        textBitmap?.recycle()
        textBitmap = null
    }
}
