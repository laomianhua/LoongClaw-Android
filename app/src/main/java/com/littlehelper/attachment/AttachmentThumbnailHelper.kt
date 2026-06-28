package com.littlehelper.attachment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

object AttachmentThumbnailHelper {

    private const val MAX_EDGE_PX = 160

    fun createThumbnail(bytes: ByteArray): ByteArray? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null

        val scale = minOf(
            MAX_EDGE_PX.toFloat() / decoded.width,
            MAX_EDGE_PX.toFloat() / decoded.height,
            1f
        )
        val scaled = if (scale < 1f) {
            val w = max(1, (decoded.width * scale).toInt())
            val h = max(1, (decoded.height * scale).toInt())
            Bitmap.createScaledBitmap(decoded, w, h, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        return ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            if (scaled !== decoded) scaled.recycle()
            stream.toByteArray()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / sampleSize >= maxEdge && halfHeight / sampleSize >= maxEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
