package com.pledgerio.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PledgerDatabaseMigrations {

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `account_types` (
                    `code` TEXT NOT NULL,
                    PRIMARY KEY(`code`)
                )
                """.trimIndent(),
            )
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

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `transaction_outbox` (
                    `localId` TEXT NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `lastError` TEXT,
                    `attemptCount` INTEGER NOT NULL,
                    `date` TEXT NOT NULL,
                    `currency` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `sourceAccountId` INTEGER NOT NULL,
                    `destinationAccountId` INTEGER NOT NULL,
                    `categoryId` INTEGER,
                    `expenseId` INTEGER,
                    `contractId` INTEGER,
                    `tagsJson` TEXT,
                    `displaySourceName` TEXT,
                    `displayDestinationName` TEXT,
                    `displayCategoryName` TEXT,
                    `type` TEXT,
                    PRIMARY KEY(`localId`)
                )
                """.trimIndent(),
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7)
}
