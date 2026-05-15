package com.pledgerio.app.domain.model

data class PagedAccounts(
    val items: List<Account>,
    val totalRecords: Long,
    val offset: Int,
    val pageSize: Int,
) {
    val hasMore: Boolean get() = offset + items.size < totalRecords
}
