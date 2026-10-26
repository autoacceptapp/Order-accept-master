package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsManagerTest {

    private lateinit var settingsManager: SettingsManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settingsManager = SettingsManager(context)
    }

    @Test
    fun testDefaultSettings() {
        assertTrue(settingsManager.isRapidoEnabled)
        assertTrue(settingsManager.isUberEnabled)
        assertEquals(50, settingsManager.minFareAmount)
        assertEquals(5.0f, settingsManager.maxPickupDistance, 0.01f)
        assertFalse(settingsManager.isMasterEnabled)
    }

    @Test
    fun testSaveAndRetrieveSettings() {
        settingsManager.saveSettings(
            isMasterEnabled = true,
            isRapidoEnabled = false,
            isUberEnabled = true,
            isOlaEnabled = false,
            minFareAmount = 120,
            maxPickupDistance = 7.5f
        )

        val retrieved = settingsManager.loadSettings()
        assertTrue(retrieved.isMasterEnabled)
        assertFalse(retrieved.isRapidoEnabled)
        assertTrue(retrieved.isUberEnabled)
        assertFalse(retrieved.isOlaEnabled)
        assertEquals(120, retrieved.minFareAmount)
        assertEquals(7.5f, retrieved.maxPickupDistance, 0.01f)
    }

    @Test
    fun testIndividualPropertiesPersistence() {
        settingsManager.isRapidoEnabled = false
        assertFalse(settingsManager.isRapidoEnabled)

        settingsManager.isUberEnabled = false
        assertFalse(settingsManager.isUberEnabled)

        settingsManager.minFareAmount = 250
        assertEquals(250, settingsManager.minFareAmount)

        settingsManager.maxPickupDistance = 3.2f
        assertEquals(3.2f, settingsManager.maxPickupDistance, 0.01f)
    }
}
