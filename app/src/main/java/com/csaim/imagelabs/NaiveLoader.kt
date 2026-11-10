package com.csaim.imagelabs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "NaiveLoader"

/**
 * Stage 1: deliberately naive.
 * Downloads the full image, decodes it at full size, caches nothing.
 * Returns null if anything goes wrong.
 */
suspend fun loadBitmapNaive(url: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
            }?.also { bitmap ->
                Log.d(
                    TAG,
                    "decoded ${bitmap.width}x${bitmap.height} = " +
                            "${bitmap.allocationByteCount / 1024 / 1024} MB"
                )
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "failed to load $url", e)
        null
    }
}