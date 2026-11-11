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
fun DownsampledImageCell(url: String) {
    // real size of the image box in pixels, filled in after layout
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val bitmap by produceState<Bitmap?>(initialValue = null, url, boxSize) {
        if (boxSize.width > 0) {
            value = loadBitmapDownsampled(url, boxSize.width, boxSize.height)
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