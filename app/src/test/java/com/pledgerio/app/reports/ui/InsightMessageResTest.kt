package com.pledgerio.app.reports.ui

import com.pledgerio.app.reports.ui.insightMessageRes
import com.pledgerio.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightMessageResTest {

    @Test
    fun `known backend keys map to string resources`() {
        val expected = mapOf(
            "computed.insight.amount.high" to R.string.insight_message_amount_high,
            "computed.insight.amount.low" to R.string.insight_message_amount_low,
            "computed.insight.frequency.high" to R.string.insight_message_frequency_high,
            "computed.insight.frequency.low" to R.string.insight_message_frequency_low,
            "computed.insight.merchant.unusual" to R.string.insight_message_merchant_unusual,
            "computed.insight.spending.spike" to R.string.insight_message_spending_spike,
        )

        expected.forEach { (key, resId) ->
            assertEquals("key $key", resId, insightMessageRes(key))
        }
    }

    @Test
    fun `unknown or blank messages pass through`() {
        assertNull(insightMessageRes("A plain text message"))
        assertNull(insightMessageRes(""))
        assertNull(insightMessageRes("computed.insight.unknown.key"))
    }
}
