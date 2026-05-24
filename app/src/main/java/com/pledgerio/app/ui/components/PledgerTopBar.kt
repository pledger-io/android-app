package com.pledgerio.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import com.pledgerio.app.ui.theme.HeaderGradientBottomDark
import com.pledgerio.app.ui.theme.HeaderGradientBottomLight
import com.pledgerio.app.ui.theme.HeaderGradientTopDark
import com.pledgerio.app.ui.theme.HeaderGradientTopLight
import com.pledgerio.app.ui.theme.PledgerBlue
import com.pledgerio.app.ui.theme.PledgerGreen
import com.pledgerio.app.ui.theme.PledgerNavy
import com.pledgerio.app.ui.theme.PledgerNavyDark
import com.pledgerio.app.ui.theme.HeaderOnGradient
import com.pledgerio.app.ui.theme.HeaderOnGradientMuted
import com.pledgerio.app.ui.theme.LocalPledgerDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PledgerTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    branded: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val darkTheme = LocalPledgerDarkTheme.current
    val barHeight = 64.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val gradient = if (darkTheme) {
                    Brush.verticalGradient(
                        colors = listOf(
                            HeaderGradientTopDark,
                            PledgerNavyDark,
                            HeaderGradientBottomDark,
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            HeaderGradientTopLight,
                            PledgerNavy,
                            HeaderGradientBottomLight,
                        ),
                    )
                }
                drawRect(gradient)
                drawCircle(
                    color = PledgerGreen.copy(alpha = if (darkTheme) 0.2f else 0.22f),
                    radius = size.width * 0.42f,
                    center = Offset(size.width * 0.92f, size.height * 0.15f),
                )
                drawCircle(
                    color = PledgerBlue.copy(alpha = if (darkTheme) 0.12f else 0.18f),
                    radius = size.width * 0.28f,
                    center = Offset(size.width * 0.12f, size.height * 0.85f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = if (darkTheme) 0.06f else 0.12f),
                    radius = size.width * 0.38f,
                    center = Offset(size.width * 0.08f, size.height * 1.05f),
                )
            },
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        TopAppBar(
            title = {
                if (branded) {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = HeaderOnGradient,
                        )
                        subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = HeaderOnGradientMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                } else {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = HeaderOnGradient,
                        )
                        subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = HeaderOnGradientMuted,
                            )
                        }
                    }
                }
            },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                titleContentColor = HeaderOnGradient,
                navigationIconContentColor = HeaderOnGradient,
                actionIconContentColor = HeaderOnGradient,
            ),
            windowInsets = WindowInsets(0),
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
        )
    }
}
