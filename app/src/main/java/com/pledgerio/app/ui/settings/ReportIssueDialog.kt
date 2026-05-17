package com.pledgerio.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pledgerio.app.R

@Composable
fun ReportIssueDialog(
    state: IssueReportUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    if (!state.showDialog) return

    AlertDialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.report_issue_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.report_issue_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.report_issue_summary_label)) },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text(stringResource(R.string.report_issue_description_label)) },
                    enabled = !state.isSubmitting,
                )
                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !state.isSubmitting,
            ) {
                Text(
                    if (state.isSubmitting) {
                        stringResource(R.string.report_issue_submitting)
                    } else {
                        stringResource(R.string.report_issue_submit)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSubmitting,
            ) {
                Text(stringResource(R.string.report_issue_cancel))
            }
        },
    )
}
