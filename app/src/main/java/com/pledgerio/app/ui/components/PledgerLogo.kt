package com.pledgerio.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pledgerio.app.R

/**
 * Pledger.io brand mark — full logo with wordmark for onboarding and empty states.
 */
@Composable
fun PledgerLogo(
    modifier: Modifier = Modifier,
    width: Dp = 220.dp,
) {
    Image(
        painter = painterResource(R.drawable.ic_pledger_brand),
        contentDescription = "Pledger.io",
        modifier = modifier.width(width),
        contentScale = ContentScale.FillWidth,
    )
}

/**
 * Calculator icon only (matches the app launcher).
 */
@Composable
fun PledgerMark(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
) {
    Image(
        painter = painterResource(R.drawable.ic_pledger_mark),
        contentDescription = "Pledger.io",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
