package com.rondasafe.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.rondasafe.app.R

@Composable
fun EntryBuildingBackground(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.rondasafe_entry_building),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
