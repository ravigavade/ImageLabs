package com.csaim.imagelabs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun CachedImageCell(url: String) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val bitmap by produceState<Bitmap?>(initialValue = null, url, boxSize) {
        if (boxSize.width > 0) {
            // 1. look in the cache first
            val cached = BitmapCache.get(url)
            if (cached != null) {
                value = cached
            } else {
                // 2. miss: load like Stage 2, then store it

//                val loaded = loadBitmapDownsampled(url, boxSize.width, boxSize.height)
                val loaded = loadBitmapWithDisk(url, boxSize.width, boxSize.height)
                if (loaded != null) {
                    BitmapCache.put(url, loaded)
                }
                value = loaded
            }
        }
    }

    val size = Modifier
        .padding(8.dp)
        .aspectRatio(1f)
        .onSizeChanged { boxSize = it }

    val loaded = bitmap
    if (loaded == null) {
        Box(modifier = size.background(Color.LightGray))
    } else {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = size
        )
    }
}