package com.pledgerio.app.util

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppLogCollectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `logcat and exported buffer receive only sanitized text`() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        val logcatMessages = mutableListOf<String>()
        val collector = AppLogCollector(context) { _, _, message ->
            logcatMessages += message
        }

        collector.log(
            priority = 4,
            tag = "Http",
            message = "GET /search?description=Salary&amount=1234.56",
        )

        assertEquals(1, logcatMessages.size)
        assertFalse(logcatMessages.single().contains("Salary"))
        assertFalse(logcatMessages.single().contains("1234.56"))
        assertFalse(collector.export().contains("Salary"))
        assertFalse(collector.export().contains("1234.56"))
    }

    @Test
    fun `clear removes memory and disk diagnostics`() {
        val cacheDir = temporaryFolder.newFolder("clear-cache")
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        val collector = AppLogCollector(context) { _, _, _ -> }
        collector.log(priority = 4, tag = "Test", message = "old session")

        collector.clear()

        assertEquals("", collector.export())
        assertFalse(File(cacheDir, "logs/app.log").exists())
    }
}
