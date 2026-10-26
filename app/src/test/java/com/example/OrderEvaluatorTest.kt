package com.example

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrderEvaluatorTest {

    private val evaluator = OrderEvaluator()
    private val defaultSettings = FilterSettings(
        isMasterEnabled = true,
        isRapidoEnabled = true,
        isUberEnabled = true,
        isOlaEnabled = true,
        minFareAmount = 80,
        maxPickupDistance = 5.0f
    )

    @Test
    fun testCombinationTrigger_missingFare_returnsFalse() {
        val dummyNode = AccessibilityNodeInfo.obtain()
        val order = ParsedOrder(
            packageName = AppConstants.PACKAGE_UBER_DRIVER,
            fareAmount = null,
            distanceKm = 3.0f,
            acceptButtonNode = dummyNode,
            matchedKeyword = "Accept",
            extractedTexts = emptyList()
        )

        val result = evaluator.evaluate(order, defaultSettings, "com.example")
        assertFalse(result.isAccepted)
        assertTrue(result.reason.contains("Fare"))
    }

    @Test
    fun testCombinationTrigger_missingDistance_returnsFalse() {
        val dummyNode = AccessibilityNodeInfo.obtain()
        val order = ParsedOrder(
            packageName = AppConstants.PACKAGE_UBER_DRIVER,
            fareAmount = 150,
            distanceKm = null,
            acceptButtonNode = dummyNode,
            matchedKeyword = "Accept",
            extractedTexts = emptyList()
        )

        val result = evaluator.evaluate(order, defaultSettings, "com.example")
        assertFalse(result.isAccepted)
        assertTrue(result.reason.contains("Distance"))
    }

    @Test
    fun testCombinationTrigger_missingAcceptButton_returnsFalse() {
        val order = ParsedOrder(
            packageName = AppConstants.PACKAGE_UBER_DRIVER,
            fareAmount = 150,
            distanceKm = 3.0f,
            acceptButtonNode = null,
            matchedKeyword = null,
            extractedTexts = emptyList()
        )

        val result = evaluator.evaluate(order, defaultSettings, "com.example")
        assertFalse(result.isAccepted)
        assertTrue(result.reason.contains("Accept button"))
    }

    @Test
    fun testCombinationTrigger_lowFare_returnsFalse() {
        val dummyNode = AccessibilityNodeInfo.obtain()
        val order = ParsedOrder(
            packageName = AppConstants.PACKAGE_UBER_DRIVER,
            fareAmount = 60, // Min is 80
            distanceKm = 2.0f,
            acceptButtonNode = dummyNode,
            matchedKeyword = "Accept",
            extractedTexts = emptyList()
        )

        val result = evaluator.evaluate(order, defaultSettings, "com.example")
        assertFalse(result.isAccepted)
        assertTrue(result.reason.contains("below minimum threshold"))
    }

    @Test
    fun testCombinationTrigger_excessiveDistance_returnsFalse() {
        val dummyNode = AccessibilityNodeInfo.obtain()
        val order = ParsedOrder(
            packageName = AppConstants.PACKAGE_UBER_DRIVER,
            fareAmount = 200,
            distanceKm = 8.5f, // Max is 5.0 km
            acceptButtonNode = dummyNode,
            matchedKeyword = "Accept",
            extractedTexts = emptyList()
        )

        val result = evaluator.evaluate(order, defaultSettings, "com.example")
        assertFalse(result.isAccepted)
        assertTrue(result.reason.contains("exceeds maximum threshold"))
    }

    @Test
    fun testCombinationTrigger_allCriteriaMet_returnsTrue() {
        val dummyNode = AccessibilityNodeInfo.obtain()
        val order = ParsedOrder(
            packageName = AppConstants.PACKAGE_UBER_DRIVER,
            fareAmount = 180,
            distanceKm = 3.2f,
            acceptButtonNode = dummyNode,
            matchedKeyword = "Accept",
            extractedTexts = emptyList()
        )

        val result = evaluator.evaluate(order, defaultSettings, "com.example")
        assertTrue(result.isAccepted)
    }

    @Test
    fun testMasterDisabled_returnsFalse() {
        val dummyNode = AccessibilityNodeInfo.obtain()
        val order = ParsedOrder(
            packageName = AppConstants.PACKAGE_UBER_DRIVER,
            fareAmount = 200,
            distanceKm = 2.0f,
            acceptButtonNode = dummyNode,
            matchedKeyword = "Accept",
            extractedTexts = emptyList()
        )

        val stoppedSettings = defaultSettings.copy(isMasterEnabled = false)
        val result = evaluator.evaluate(order, stoppedSettings, "com.example")
        assertFalse(result.isAccepted)
    }
}
