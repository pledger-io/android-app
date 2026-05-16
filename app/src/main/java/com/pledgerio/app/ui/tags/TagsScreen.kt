package com.pledgerio.app.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.Tag
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.theme.ExpenseRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    uiState.editor?.let { editor ->
        TagEditorDialog(
            state = editor,
            isSaving = uiState.isSaving,
            onNameChange = viewModel::onEditorNameChanged,
            onDismiss = viewModel::dismissEditor,
            onConfirm = viewModel::saveEditor,
        )
    }

    uiState.pendingDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = { Text("Delete tag?") },
            text = {
                Text(
                    "This removes \"${tag.name}\" from your tag list. " +
                        "Existing transactions keep their tag until you edit them.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    enabled = !uiState.isSaving,
                ) {
                    Text("Delete", color = ExpenseRed)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissDeleteDialog,
                    enabled = !uiState.isSaving,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = "Manage tags",
                subtitle = uiState.subtitle,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openCreateEditor,
                containerColor = EmeraldGreen,
                text = { Text("New tag") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading && uiState.tags.isEmpty() -> LoadingScreen()
                uiState.error != null && uiState.tags.isEmpty() -> ErrorScreen(
                    message = uiState.error ?: "",
                    onRetry = viewModel::refresh,
                )
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TagSearchField(
                            query = uiState.searchQuery,
                            onQueryChange = viewModel::onSearchQueryChanged,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TagsSummaryCard(total = uiState.tags.size)
                        uiState.error?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            if (uiState.tags.isEmpty()) {
                                EmptyScreen(
                                    icon = Icons.Default.Tag,
                                    title = if (uiState.searchQuery.isBlank()) {
                                        "No tags yet"
                                    } else {
                                        "No tags found"
                                    },
                                    message = if (uiState.searchQuery.isBlank()) {
                                        "Create tags to group and filter transactions."
                                    } else {
                                        "Try a different search term or add a new tag."
                                    },
                                    actionLabel = if (uiState.searchQuery.isBlank()) {
                                        "Create tag"
                                    } else {
                                        null
                                    },
                                    onAction = if (uiState.searchQuery.isBlank()) {
                                        viewModel::openCreateEditor
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(
                                        items = uiState.tags,
                                        key = { tag -> tag.name },
                                    ) { tag ->
                                        TagListItem(
                                            tag = tag,
                                            onEdit = { viewModel.openEditEditor(tag) },
                                            onDelete = { viewModel.askDelete(tag) },
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(88.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagsSummaryCard(total: Int) {
    PledgerCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tag library",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (total == 1) "1 tag" else "$total tags",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Short names work best when adding tags on transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = EmeraldGreen.copy(alpha = 0.16f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    tint = EmeraldGreen,
                )
            }
        }
    }
}

@Composable
private fun TagSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search tags…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun TagListItem(
    tag: Tag,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PledgerCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = tag.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename tag",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete tag",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TagEditorDialog(
    state: TagEditorUiState,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    placeholder = { Text("vacation, business, recurring…") },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isEditing) {
                    Text(
                        text = "Renaming creates the new tag and removes the old one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                state.serverError?.let { serverError ->
                    Text(
                        text = serverError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(state.confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
            ) {
                Text("Cancel")
            }
        },
    )
}
