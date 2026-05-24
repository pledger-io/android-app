package com.pledgerio.app.di

import android.content.Context
import androidx.room.Room
import com.pledgerio.app.data.local.PledgerDatabase
import com.pledgerio.app.data.local.PledgerDatabaseMigrations
import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.data.local.dao.AccountTypeDao
import com.pledgerio.app.data.local.dao.BudgetDao
import com.pledgerio.app.data.local.dao.CategoryDao
import com.pledgerio.app.data.local.dao.ContractDao
import com.pledgerio.app.data.local.dao.CurrencyDao
import com.pledgerio.app.data.local.dao.ExpenseGroupDao
import com.pledgerio.app.data.local.dao.SyncMetadataDao
import com.pledgerio.app.data.local.dao.TagDao
import com.pledgerio.app.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PledgerDatabase =
        Room.databaseBuilder(
            context,
            PledgerDatabase::class.java,
            "pledger_database"
        )
            .addMigrations(*PledgerDatabaseMigrations.ALL)
            .build()

    @Provides
    fun provideAccountDao(db: PledgerDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideTransactionDao(db: PledgerDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideBudgetDao(db: PledgerDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideCategoryDao(db: PledgerDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTagDao(db: PledgerDatabase): TagDao = db.tagDao()

    @Provides
    fun provideCurrencyDao(db: PledgerDatabase): CurrencyDao = db.currencyDao()

    @Provides
    fun provideContractDao(db: PledgerDatabase): ContractDao = db.contractDao()

    @Provides
    fun provideExpenseGroupDao(db: PledgerDatabase): ExpenseGroupDao = db.expenseGroupDao()

    @Provides
    fun provideAccountTypeDao(db: PledgerDatabase): AccountTypeDao = db.accountTypeDao()

    @Provides
    fun provideSyncMetadataDao(db: PledgerDatabase): SyncMetadataDao = db.syncMetadataDao()
}
