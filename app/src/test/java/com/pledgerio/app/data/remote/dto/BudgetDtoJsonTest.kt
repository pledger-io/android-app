package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetDtoJsonTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(BudgetDto::class.java)

    @Test
    fun `parses budget when period endDate is null`() {
        val json = """
            {
              "income": 3500.0,
              "period": {
                "startDate": "2026-05-01",
                "endDate": null
              },
              "expenses": [
                { "id": 1, "name": "Groceries", "expected": 400.0 }
              ]
            }
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertEquals(3500.0, dto.income, 0.001)
        assertEquals("2026-05-01", dto.period?.startDate)
        assertNull(dto.period?.endDate)
        assertEquals(1, dto.expenses.size)
    }
}
