package com.csaim.imagelabs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

fun calculateSampleSize(
    width: Int,
    height: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    var sampleSize = 1
    while (width / (sampleSize * 2) >= targetWidth &&
        height / (sampleSize * 2) >= targetHeight
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

suspend fun loadBitmapDownsampled(
    url: String,
    targetWidth: Int,
    targetHeight: Int
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        // download the compressed file once
        val bytes = URL(url).readBytes()

        // pass 1: read only the width and height, no pixels
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        // work out the shrink factor
        val options = BitmapFactory.Options()
        options.inSampleSize = calculateSampleSize(
            bounds.outWidth, bounds.outHeight, targetWidth, targetHeight
        )

        // pass 2: decode again, shrunk
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also {
            Log.d("DownsampledLoader",
                "sample ${options.inSampleSize}: ${it.width}x${it.height} = ${it.allocationByteCount / 1024} KB")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}