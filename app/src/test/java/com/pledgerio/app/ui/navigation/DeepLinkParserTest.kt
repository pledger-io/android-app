package com.pledgerio.app.ui.navigation

import com.pledgerio.app.util.BudgetAlertLogic
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkParserTest {

    @Test
    fun `parses budgets uri with year and month`() {
        val link = DeepLinkParser.parse("pledger://budgets?year=2026&month=7")
        assertTrue(link is DeepLink.Budgets)
        assertEquals(YearMonth.of(2026, 7), (link as DeepLink.Budgets).yearMonth)
    }

    @Test
    fun `parses budgets uri without query as null month`() {
        val link = DeepLinkParser.parse("pledger://budgets")
        assertTrue(link is DeepLink.Budgets)
        assertNull((link as DeepLink.Budgets).yearMonth)
    }

    @Test
    fun `notification deep link uri matches parser`() {
        val yearMonth = YearMonth.of(2026, 7)
        val link = DeepLinkParser.parse(BudgetAlertLogic.budgetsDeepLinkUri(yearMonth))
        assertEquals(DeepLink.Budgets(yearMonth), link)
    }

    @Test
    fun `parses transaction and account uris`() {
        assertEquals(
            DeepLink.Transaction(42),
            DeepLinkParser.parse("pledger://transaction/42"),
        )
        assertEquals(
            DeepLink.Account(9),
            DeepLinkParser.parse("pledger://account/9"),
        )
    }

    @Test
    fun `rejects unknown scheme or host`() {
        assertNull(DeepLinkParser.parse("https://example.com/budgets?year=2026&month=7"))
        assertNull(DeepLinkParser.parse("pledger://unknown"))
        assertNull(DeepLinkParser.parse(null as String?))
    }
}
