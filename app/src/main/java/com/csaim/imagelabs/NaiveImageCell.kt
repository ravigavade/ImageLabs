package com.csaim.imagelabs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun NaiveImageCell(url: String) {
    // starts as null, becomes the bitmap once the load finishes
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = loadBitmapNaive(url)
    }
//    produceState launches a coroutine when the cell enters the composition,
//    and cancels it when the cell leaves. Whatever you assign to value becomes state,
//    so the UI updates when the bitmap arrives.


    val size = Modifier
        .padding(8.dp)
        .aspectRatio(1f)

    val loaded = bitmap
    if (loaded == null) {
        // still loading, or failed
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