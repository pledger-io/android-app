package com.pledgerio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiSessionMaskTest {

    @Test
    fun `maskApiToken hides all but last four`() {
        assertEquals("••••wxyz", maskApiToken("abcdefghijklwxyz"))
    }

    @Test
    fun `maskApiToken blanks and short values`() {
        assertEquals("••••", maskApiToken(null))
        assertEquals("••••", maskApiToken(""))
        assertEquals("••••", maskApiToken("abcd"))
    }
}
