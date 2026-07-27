package com.pledgerio.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTransactionsRouteTest {

    @Test
    fun `createRoute defaults encode sentinel values`() {
        val route = Screen.Transactions.createRoute()
        assertEquals(
            "transactions?expenseId=-1&expenseName=&categoryId=-1&categoryName=&year=-1&month=-1",
            route,
        )
    }

    @Test
    fun `createRoute encodes category year and month`() {
        val route = Screen.Transactions.createRoute(
            categoryId = 42L,
            categoryName = "Groceries & more",
            year = 2026,
            month = 7,
        )
        assertTrue(route.contains("categoryId=42"))
        assertTrue(route.contains("categoryName=Groceries%20%26%20more"))
        assertTrue(route.contains("year=2026"))
        assertTrue(route.contains("month=7"))
        assertTrue(route.contains("expenseId=-1"))
    }

    @Test
    fun `createRoute encodes expense with month window`() {
        val route = Screen.Transactions.createRoute(
            expenseId = 9L,
            expenseName = "Rent",
            year = 2026,
            month = 5,
        )
        assertEquals(
            "transactions?expenseId=9&expenseName=Rent&categoryId=-1&categoryName=&year=2026&month=5",
            route,
        )
    }
}
