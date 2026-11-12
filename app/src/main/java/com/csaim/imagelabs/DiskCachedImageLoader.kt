package com.csaim.imagelabs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

suspend fun loadBitmapWithDisk(
    url: String,
    targetWidth: Int,
    targetHeight: Int
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        // 1. disk first, 2. network if the file isn't there
        var bytes = DiskCache.get(url)
        if (bytes == null) {
            bytes = URL(url).readBytes()
            DiskCache.put(url, bytes)
        }

        // same two-pass decode as Stage 2
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options()
        options.inSampleSize = calculateSampleSize(
            bounds.outWidth, bounds.outHeight, targetWidth, targetHeight
        )

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("DiskLoader", "failed to load $url", e)
        null
    }
}