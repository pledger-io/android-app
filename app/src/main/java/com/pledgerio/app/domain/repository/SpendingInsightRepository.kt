package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.SpendingInsights
import com.pledgerio.app.util.Resource
import java.time.YearMonth

interface SpendingInsightRepository {

    /**
     * Detected insights and patterns for [month], served from the in-memory cache while it is
     * fresh. [forceRefresh] always goes to the network and replaces the cached snapshot.
     */
    suspend fun getInsights(month: YearMonth, forceRefresh: Boolean = false): Resource<SpendingInsights>
}
