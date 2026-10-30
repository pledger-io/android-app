package com.pledgerio.app.di

import com.pledgerio.app.sync.work.OutboxFlushScheduler
import com.pledgerio.app.sync.work.SyncWorkScheduler
import com.pledgerio.app.sync.work.WorkManagerOutboxFlushScheduler
import com.pledgerio.app.sync.work.WorkManagerSyncWorkScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    @Singleton
    abstract fun bindSyncWorkScheduler(
        implementation: WorkManagerSyncWorkScheduler,
    ): SyncWorkScheduler

    @Binds
    @Singleton
    abstract fun bindOutboxFlushScheduler(
        implementation: WorkManagerOutboxFlushScheduler,
    ): OutboxFlushScheduler
}
