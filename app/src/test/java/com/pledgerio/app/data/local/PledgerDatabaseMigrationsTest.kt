package com.pledgerio.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PledgerDatabaseMigrationsTest {

    @Test
    fun `every previous version can be upgraded to the current one`() {
        val migrated = PledgerDatabaseMigrations.ALL.associate { it.startVersion to it.endVersion }
        val legacy = PledgerDatabaseMigrations.LEGACY_VERSIONS.toSet()

        val unreachable = (1 until PledgerDatabaseMigrations.VERSION).filter { version ->
            version !in legacy && version !in migrated
        }

        assertTrue("No upgrade path from version(s) $unreachable", unreachable.isEmpty())
    }

    @Test
    fun `migrations form a chain ending at the current version`() {
        val chained = PledgerDatabaseMigrations.ALL.sortedBy { it.startVersion }

        chained.zipWithNext { current, next ->
            assertEquals(next.startVersion, current.endVersion)
        }
        assertEquals(PledgerDatabaseMigrations.VERSION, chained.last().endVersion)
    }
}
