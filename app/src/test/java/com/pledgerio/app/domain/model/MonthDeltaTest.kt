package com.pledgerio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthDeltaTest {

    @Test
    fun `prior zero yields null percent`() {
        val delta = monthDelta(current = 100.0, prior = 0.0)
        assertEquals(100.0, delta.absolute, 0.0)
        assertNull(delta.percent)
    }

    @Test
    fun `normal percent is absolute over abs prior`() {
        val delta = monthDelta(current = 150.0, prior = 100.0)
        assertEquals(50.0, delta.absolute, 0.0)
        assertEquals(0.5, delta.percent!!, 0.0001)
    }

    @Test
    fun `negative change keeps signed absolute and percent`() {
        val delta = monthDelta(current = 50.0, prior = 100.0)
        assertEquals(-50.0, delta.absolute, 0.0)
        assertEquals(-0.5, delta.percent!!, 0.0001)
        assertTrue(delta.absolute < 0)
    }

    @Test
    fun `negative prior baseline uses absolute value for percent`() {
        val delta = monthDelta(current = -50.0, prior = -100.0)
        assertEquals(50.0, delta.absolute, 0.0)
        assertEquals(0.5, delta.percent!!, 0.0001)
    }
}
