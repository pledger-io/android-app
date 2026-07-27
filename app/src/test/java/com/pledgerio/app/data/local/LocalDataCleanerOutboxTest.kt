package com.pledgerio.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalDataCleanerOutboxTest {

    @Test
    fun `schema export v7 includes transaction_outbox for clearAllTables wipe`() {
        val schema = File("schemas/com.pledgerio.app.data.local.PledgerDatabase/7.json")
        assertTrue("Room schema v7 must be exported", schema.exists())
        val text = schema.readText()
        assertTrue(text.contains("\"version\": 7") || text.contains("\"version\":7"))
        assertTrue(text.contains("transaction_outbox"))
        assertEquals(7, PledgerDatabaseMigrations.MIGRATION_6_7.endVersion)
    }
}
