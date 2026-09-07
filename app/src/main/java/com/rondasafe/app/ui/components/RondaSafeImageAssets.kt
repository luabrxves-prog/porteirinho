package com.rondasafe.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.rondasafe.app.R

@Composable
fun RondaSafeHomeImage(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.rondasafe_home_bg),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun RondaSafeCondoImage(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.rondasafe_condo_card),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun RondaSafeAppLogoImage(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.rondasafe_app_icon),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
