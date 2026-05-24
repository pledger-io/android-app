package com.pledgerio.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class PledgerDatabaseMigrationsTest {

    @Test
    fun `migration 5 to 6 creates account_types and sync_metadata tables`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        PledgerDatabaseMigrations.MIGRATION_5_6.migrate(db)

        verify {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `account_types` (
                    `code` TEXT NOT NULL,
                    PRIMARY KEY(`code`)
                )
                """.trimIndent(),
            )
        }
        verify {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_metadata` (
                    `key` TEXT NOT NULL,
                    `lastSyncedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent(),
            )
        }
    }
}
