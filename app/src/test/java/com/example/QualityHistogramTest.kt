package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.components.QualityHistogramChart
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QualityHistogramTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testQualityHistogramChart_rendersSuccessfully() {
        val sampleScores = listOf(0.95f, 0.88f, 0.82f, 0.75f, 0.60f, 0.50f, 0.40f, 0.30f)
        var selectedPct = 35

        composeTestRule.setContent {
            MyApplicationTheme {
                QualityHistogramChart(
                    sortedScores = sampleScores,
                    selectedPercentage = selectedPct,
                    onPercentageChange = { selectedPct = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("quality_histogram_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quality_histogram_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("score_threshold_slider").assertIsDisplayed()
    }
}
