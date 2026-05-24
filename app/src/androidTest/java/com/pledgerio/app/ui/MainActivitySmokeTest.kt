package com.pledgerio.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.pledgerio.app.MainActivity
import com.pledgerio.app.R
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndShowsServerSetup() {
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.server_setup_url_label),
        ).assertIsDisplayed()
    }
}
