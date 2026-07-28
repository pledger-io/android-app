package com.pledgerio.app.ui.util

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DatePickerDatesTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT-08:00"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `UTC picker millis retain selected day west of UTC`() {
        val selectedDate = LocalDate.of(2026, 7, 28)
        val pickerMillis = selectedDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        assertEquals(selectedDate, datePickerMillisToLocalDate(pickerMillis))
    }

    @Test
    fun `initial picker millis use UTC midnight`() {
        val selectedDate = LocalDate.of(2026, 7, 28)
        val expectedMillis = selectedDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedMillis, selectedDate.toDatePickerMillis())
    }
}
