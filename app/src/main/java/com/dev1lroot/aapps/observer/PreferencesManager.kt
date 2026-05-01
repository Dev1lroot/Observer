package com.dev1lroot.aapps.observer

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("observer_prefs", Context.MODE_PRIVATE)

    var rtmpUrl: String
        get() = prefs.getString(KEY_RTMP_URL, DEFAULT_RTMP_URL) ?: DEFAULT_RTMP_URL
        set(value) { prefs.edit().putString(KEY_RTMP_URL, value).apply() }

    var videoWidth: Int
        get() = prefs.getInt(KEY_VIDEO_WIDTH, DEFAULT_WIDTH)
        set(value) { prefs.edit().putInt(KEY_VIDEO_WIDTH, value).apply() }

    var videoHeight: Int
        get() = prefs.getInt(KEY_VIDEO_HEIGHT, DEFAULT_HEIGHT)
        set(value) { prefs.edit().putInt(KEY_VIDEO_HEIGHT, value).apply() }

    var fps: Int
        get() = prefs.getInt(KEY_FPS, DEFAULT_FPS)
        set(value) { prefs.edit().putInt(KEY_FPS, value).apply() }

    var videoBitrateKbps: Int
        get() = prefs.getInt(KEY_VIDEO_BITRATE, DEFAULT_VIDEO_BITRATE)
        set(value) { prefs.edit().putInt(KEY_VIDEO_BITRATE, value).apply() }

    var audioBitrateKbps: Int
        get() = prefs.getInt(KEY_AUDIO_BITRATE, DEFAULT_AUDIO_BITRATE)
        set(value) { prefs.edit().putInt(KEY_AUDIO_BITRATE, value).apply() }

    var showTimestamp: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TIMESTAMP, true)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_TIMESTAMP, value).apply() }

    var showGps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_GPS, false)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_GPS, value).apply() }

    var showCompass: Boolean
        get() = prefs.getBoolean(KEY_SHOW_COMPASS, false)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_COMPASS, value).apply() }

    var showCrosshair: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CROSSHAIR, false)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_CROSSHAIR, value).apply() }

    companion object {
        private const val KEY_RTMP_URL = "rtmp_url"
        private const val KEY_VIDEO_WIDTH = "video_width"
        private const val KEY_VIDEO_HEIGHT = "video_height"
        private const val KEY_FPS = "fps"
        private const val KEY_VIDEO_BITRATE = "video_bitrate_kbps"
        private const val KEY_AUDIO_BITRATE = "audio_bitrate_kbps"
        private const val KEY_SHOW_TIMESTAMP = "show_timestamp"
        private const val KEY_SHOW_GPS = "show_gps"
        private const val KEY_SHOW_COMPASS = "show_compass"
        private const val KEY_SHOW_CROSSHAIR = "show_crosshair"

        const val DEFAULT_RTMP_URL = "rtmp://your.server.com/live/stream"
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_FPS = 30
        const val DEFAULT_VIDEO_BITRATE = 4000
        const val DEFAULT_AUDIO_BITRATE = 128
    }
}
