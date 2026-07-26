package com.pledgerio.app.ui.navigation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDeletionNavigationTest {

    @Test
    fun `publish and consume round-trips a positive transaction id`() {
        val handle = SavedStateHandle()

        assertTrue(TransactionDeletionResultContract.publish(handle, 55L))
        assertEquals(55L, TransactionDeletionResultContract.consume(handle)?.transactionId)
        assertNull(TransactionDeletionResultContract.consume(handle))
    }

    @Test
    fun `publish ignores missing target and non-positive ids`() {
        assertFalse(TransactionDeletionResultContract.publish(null, 12L))
        assertFalse(TransactionDeletionResultContract.publish(SavedStateHandle(), 0L))
        assertFalse(TransactionDeletionResultContract.publish(SavedStateHandle(), -3L))
    }
}
