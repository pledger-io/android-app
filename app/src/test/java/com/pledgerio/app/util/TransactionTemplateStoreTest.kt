package com.pledgerio.app.util

import com.pledgerio.app.domain.model.TransactionTemplate
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionTemplateStoreTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `template round-trips through moshi adapter`() {
        val listType = com.squareup.moshi.Types.newParameterizedType(
            List::class.java,
            TransactionTemplate::class.java,
        )
        val adapter = moshi.adapter<List<TransactionTemplate>>(listType)
        val templates = listOf(
            TransactionTemplate(
                id = "1",
                name = "Rent",
                description = "Monthly rent",
                amount = "1200",
                type = "CREDIT",
                currency = "EUR",
                tags = listOf("home"),
            ),
        )
        val json = adapter.toJson(templates)
        val parsed = adapter.fromJson(json)!!
        assertEquals(1, parsed.size)
        assertEquals("Rent", parsed.first().name)
        assertEquals(listOf("home"), parsed.first().tags)
    }
}
