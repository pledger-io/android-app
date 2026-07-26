package com.pledgerio.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.flow.filter

data class TransactionDeletedResult(val transactionId: Long)

object TransactionDeletionResultContract {
    private const val KEY = "transaction_deleted_result"
    private const val EMPTY_ID = -1L

    fun publish(target: SavedStateHandle?, transactionId: Long): Boolean {
        if (target == null || transactionId <= 0) return false
        target[KEY] = transactionId
        return true
    }

    fun consume(source: SavedStateHandle): TransactionDeletedResult? {
        val transactionId = source.remove<Long>(KEY)?.takeIf { it > 0 } ?: return null
        return TransactionDeletedResult(transactionId)
    }

    internal fun resultFlow(source: SavedStateHandle) =
        source.getStateFlow(KEY, EMPTY_ID).filter { it > 0 }
}

/**
 * Consumes a deletion result once, only while its destination is active.
 */
@Composable
fun TransactionDeletionResultEffect(
    backStackEntry: NavBackStackEntry,
    onDeleted: (Long) -> Unit,
) {
    LaunchedEffect(backStackEntry, onDeleted) {
        backStackEntry.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            TransactionDeletionResultContract.resultFlow(backStackEntry.savedStateHandle)
                .collect {
                    TransactionDeletionResultContract.consume(backStackEntry.savedStateHandle)
                        ?.let { result -> onDeleted(result.transactionId) }
                }
        }
    }
}
