package com.pledgerio.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pledgerio.app.R
import java.time.Duration
import java.time.Instant

@Composable
fun LastUpdatedIndicator(
    lastUpdatedAtMillis: Long?,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = when {
        isRefreshing -> stringResource(R.string.last_updated_refreshing)
        lastUpdatedAtMillis == null -> null
        else -> {
            val minutes = Duration.between(Instant.ofEpochMilli(lastUpdatedAtMillis), Instant.now()).toMinutes()
            when {
                minutes < 1 -> stringResource(R.string.last_updated_just_now)
                minutes < 60 -> stringResource(R.string.last_updated_minutes, minutes)
                else -> {
                    val hours = minutes / 60
                    if (hours < 24) {
                        stringResource(R.string.last_updated_hours, hours)
                    } else {
                        stringResource(R.string.last_updated_days, hours / 24)
                    }
                }
            }
        }
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
