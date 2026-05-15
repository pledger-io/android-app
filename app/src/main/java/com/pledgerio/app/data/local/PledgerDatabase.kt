package com.pledgerio.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.data.local.dao.BudgetDao
import com.pledgerio.app.data.local.dao.CategoryDao
import com.pledgerio.app.data.local.dao.CurrencyDao
import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.local.entity.AccountEntity
import com.pledgerio.app.data.local.entity.BudgetEntity
import com.pledgerio.app.data.local.entity.CategoryEntity
import com.pledgerio.app.data.local.entity.CurrencyEntity
import com.pledgerio.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        CategoryEntity::class,
        CurrencyEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PledgerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun currencyDao(): CurrencyDao
}
