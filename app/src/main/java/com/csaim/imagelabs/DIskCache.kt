package com.csaim.imagelabs

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

object DiskCache {

    private lateinit var dir: File

    var diskHits = 0
    var networkBytes = 0L   // total bytes downloaded from the internet

    // call once at app start
    fun init(context: Context) {
        dir = File(context.cacheDir, "images")
        dir.mkdirs()
    }

    // turns a URL into a safe, unique file name
    private fun fileFor(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, hash)
    }

    fun get(url: String): ByteArray? {
        val file = fileFor(url)
        if (!file.exists()) return null
        diskHits++
        logStats()
        return file.readBytes()
    }

    fun put(url: String, bytes: ByteArray) {
        fileFor(url).writeBytes(bytes)
        networkBytes += bytes.size
        logStats()
    }

    private fun logStats() {
        Log.d("DiskCache", "diskHits=$diskHits networkKB=${networkBytes / 1024}")
    }
}