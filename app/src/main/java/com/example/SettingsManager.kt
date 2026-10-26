package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Filter configuration data class.
 */
data class FilterSettings(
    val isMasterEnabled: Boolean = false,
    val isRapidoEnabled: Boolean = true,
    val isUberEnabled: Boolean = true,
    val isOlaEnabled: Boolean = true,
    val minFareAmount: Int = 50,
    val maxPickupDistance: Float = 5.0f
)

/**
 * Lightweight local Key-Value storage manager using Android SharedPreferences.
 * Operates without external databases or paid libraries.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<FilterSettings> = _settingsFlow.asStateFlow()

    var isMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MASTER_ENABLED, value).apply()
            notifySettingsChanged()
        }

    var isRapidoEnabled: Boolean
        get() = prefs.getBoolean(KEY_RAPIDO_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_RAPIDO_ENABLED, value).apply()
            notifySettingsChanged()
        }

    var isUberEnabled: Boolean
        get() = prefs.getBoolean(KEY_UBER_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_UBER_ENABLED, value).apply()
            notifySettingsChanged()
        }

    var isOlaEnabled: Boolean
        get() = prefs.getBoolean(KEY_OLA_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_OLA_ENABLED, value).apply()
            notifySettingsChanged()
        }

    var minFareAmount: Int
        get() = prefs.getInt(KEY_MIN_FARE_AMOUNT, 50)
        set(value) {
            prefs.edit().putInt(KEY_MIN_FARE_AMOUNT, value).apply()
            notifySettingsChanged()
        }

    var maxPickupDistance: Float
        get() = prefs.getFloat(KEY_MAX_PICKUP_DISTANCE, 5.0f)
        set(value) {
            prefs.edit().putFloat(KEY_MAX_PICKUP_DISTANCE, value).apply()
            notifySettingsChanged()
        }

    fun loadSettings(): FilterSettings {
        return FilterSettings(
            isMasterEnabled = isMasterEnabled,
            isRapidoEnabled = isRapidoEnabled,
            isUberEnabled = isUberEnabled,
            isOlaEnabled = isOlaEnabled,
            minFareAmount = minFareAmount,
            maxPickupDistance = maxPickupDistance
        )
    }

    /**
     * Atomically saves all filter preferences and notifies observers.
     */
    fun saveSettings(
        isMasterEnabled: Boolean,
        isRapidoEnabled: Boolean,
        isUberEnabled: Boolean,
        isOlaEnabled: Boolean,
        minFareAmount: Int,
        maxPickupDistance: Float
    ) {
        prefs.edit()
            .putBoolean(KEY_MASTER_ENABLED, isMasterEnabled)
            .putBoolean(KEY_RAPIDO_ENABLED, isRapidoEnabled)
            .putBoolean(KEY_UBER_ENABLED, isUberEnabled)
            .putBoolean(KEY_OLA_ENABLED, isOlaEnabled)
            .putInt(KEY_MIN_FARE_AMOUNT, minFareAmount)
            .putFloat(KEY_MAX_PICKUP_DISTANCE, maxPickupDistance)
            .apply()

        notifySettingsChanged()
    }

    private fun notifySettingsChanged() {
        _settingsFlow.value = FilterSettings(
            isMasterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, false),
            isRapidoEnabled = prefs.getBoolean(KEY_RAPIDO_ENABLED, true),
            isUberEnabled = prefs.getBoolean(KEY_UBER_ENABLED, true),
            isOlaEnabled = prefs.getBoolean(KEY_OLA_ENABLED, true),
            minFareAmount = prefs.getInt(KEY_MIN_FARE_AMOUNT, 50),
            maxPickupDistance = prefs.getFloat(KEY_MAX_PICKUP_DISTANCE, 5.0f)
        )
    }

    companion object {
        const val PREFS_NAME = "auto_accept_preferences"
        const val KEY_MASTER_ENABLED = "is_master_enabled"
        const val KEY_RAPIDO_ENABLED = "isRapidoEnabled"
        const val KEY_UBER_ENABLED = "isUberEnabled"
        const val KEY_OLA_ENABLED = "isOlaEnabled"
        const val KEY_MIN_FARE_AMOUNT = "minFareAmount"
        const val KEY_MAX_PICKUP_DISTANCE = "maxPickupDistance"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
