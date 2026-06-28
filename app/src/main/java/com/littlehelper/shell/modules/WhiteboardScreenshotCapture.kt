package com.littlehelper.shell.modules

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object WhiteboardScreenshotCapture {

    suspend fun captureRegion(view: View, boundsInWindow: Rect): Bitmap? {
        if (boundsInWindow.width() <= 0 || boundsInWindow.height() <= 0) return null
        val window = view.context.findWindow() ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureWithPixelCopy(window, boundsInWindow)
        } else {
            captureWithCanvasDraw(view, boundsInWindow)
        }
    }

    private suspend fun captureWithPixelCopy(
        window: Window,
        boundsInWindow: Rect
    ): Bitmap? = suspendCancellableCoroutine { continuation ->
        val bitmap = Bitmap.createBitmap(
            boundsInWindow.width(),
            boundsInWindow.height(),
            Bitmap.Config.ARGB_8888
        )
        PixelCopy.request(
            window,
            boundsInWindow,
            bitmap,
            { result ->
                if (continuation.isActive) {
                    if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap)
                    } else {
                        bitmap.recycle()
                        continuation.resume(null)
                    }
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    private fun captureWithCanvasDraw(view: View, boundsInWindow: Rect): Bitmap? {
        return runCatching {
            val root = view.rootView
            val bitmap = Bitmap.createBitmap(
                boundsInWindow.width(),
                boundsInWindow.height(),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            canvas.translate(-boundsInWindow.left.toFloat(), -boundsInWindow.top.toFloat())
            root.draw(canvas)
            bitmap
        }.getOrNull()
    }

    private fun Context.findWindow(): Window? {
        val activity = findActivity() ?: return null
        return activity.window
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
