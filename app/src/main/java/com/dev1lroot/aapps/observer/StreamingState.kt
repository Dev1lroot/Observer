package com.dev1lroot.aapps.observer

sealed class StreamingState {
    object Idle : StreamingState()
    object Connecting : StreamingState()
    object Streaming : StreamingState()
    data class Error(val message: String) : StreamingState()
}
