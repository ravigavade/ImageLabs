package com.csaim.imagelabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp


private val imageUrls = List(200){ index ->
    "https://picsum.photos/seed/$index/4000/3000"

}


class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {



            ImageList(imageUrls)




        }
    }
}

@Composable
fun ImageList(
    urls: List<String>,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = WindowInsets.systemBars.asPaddingValues(),
//                modifier = TODO(),
//                state = TODO(),
//                contentPadding = 2.dp,
//                reverseLayout = TODO(),
//                verticalArrangement = TODO(),
//                horizontalArrangement = TODO(),
//                flingBehavior = TODO(),
//                userScrollEnabled = TODO(),
//                overscrollEffect = TODO()
        modifier = Modifier
            .fillMaxSize()
    ) {
        items(urls, key = {it}){ url ->
//            Image(
//                painter = painterResource(R.drawable.ic_launcher_background),
//                contentDescription = null,
//                modifier = Modifier
//                    .padding(8.dp)
//                    .aspectRatio(1f)
//            )
//            NaiveImageCell(url)
            DownsampledImageCell(url)
        }
    }
}
