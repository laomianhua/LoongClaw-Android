package com.littlehelper.util

object DebugLog {
    fun d(tag: String, message: String) {
        try {
            android.util.Log.d(tag, message)
        } catch (_: Throwable) {
            // JVM unit tests: android.util.Log is not mocked
        }
    }

    fun w(tag: String, message: String) {
        try {
            android.util.Log.w(tag, message)
        } catch (_: Throwable) {
            // JVM unit tests: android.util.Log is not mocked
        }
    }
}
