package com.littlehelper.shell.model

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    ONLINE,
    DEGRADED
}

enum class CapturePhase {
    IDLE,
    RECORDING,
    UPLOADING,
    PROCESSING
}

enum class ModuleLoadState {
    IDLE,
    PRELOADING,
    READY
}
