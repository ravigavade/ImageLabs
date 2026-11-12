package com.csaim.imagelabs

import android.graphics.Bitmap
import android.util.LruCache

object BitmapCache {

    // use 1/8 of the app's max memory, in KB
//    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    private const val maxKb = 64 * 1024   //64 mb
//    private const val maxKb = 24 * 1024   //24 mb
    init { android.util.Log.d("BitmapCache", "maxKb=$maxKb") }

    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    var hits = 0
    var misses = 0

    fun get(url: String): Bitmap? {
        val bitmap = cache.get(url)
        if (bitmap != null) hits++ else misses++
        android.util.Log.d("BitmapCache", "hits=$hits misses=$misses")
        return bitmap
    }

    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }
}