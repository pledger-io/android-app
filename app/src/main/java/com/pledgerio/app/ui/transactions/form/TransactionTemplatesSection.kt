package com.pledgerio.app.ui.transactions.form

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.TransactionTemplate

@Composable
fun TransactionTemplatesSection(
    templates: List<TransactionTemplate>,
    onApplyTemplate: (TransactionTemplate) -> Unit,
    onSaveAsTemplate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val showEndIndicator by remember {
        derivedStateOf {
            scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue
        }
    }
    val showStartIndicator by remember {
        derivedStateOf {
            scrollState.value > 0
        }
    }
    val scrollHint = if (showEndIndicator) {
        stringResource(R.string.transaction_templates_scroll_hint)
    } else {
        null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.transaction_templates),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onSaveAsTemplate) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(stringResource(R.string.transaction_save_template))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .then(
                        if (scrollHint != null) {
                            Modifier.semantics { contentDescription = scrollHint }
                        } else {
                            Modifier
                        },
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                templates.forEach { template ->
                    FilterChip(
                        selected = false,
                        onClick = { onApplyTemplate(template) },
                        label = {
                            Text(
                                text = template.name,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }

            if (showStartIndicator) {
                HorizontalScrollFadeOverlay(
                    align = Alignment.CenterStart,
                    fadeToStart = true,
                )
            }
            if (showEndIndicator) {
                HorizontalScrollFadeOverlay(
                    align = Alignment.CenterEnd,
                    fadeToStart = false,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                        .size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.HorizontalScrollFadeOverlay(
    align: Alignment,
    fadeToStart: Boolean,
) {
    val fadeColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .align(align)
            .fillMaxHeight()
            .width(40.dp)
            .background(
                Brush.horizontalGradient(
                    colors = if (fadeToStart) {
                        listOf(fadeColor, Color.Transparent)
                    } else {
                        listOf(Color.Transparent, fadeColor)
                    },
                ),
            ),
    )
}
