package com.example

/**
 * Centralized constants for package names, target keywords, regex patterns, and timings.
 */
object AppConstants {

    const val TAG = "AutoAccept"

    // Supported gig platform package names
    const val PACKAGE_UBER_DRIVER = "com.ubercab.driver"
    const val PACKAGE_RAPIDO_CAPTAIN = "com.rapido.passenger.partner"
    const val PACKAGE_RAPIDO_ALT = "com.rapido.captain"
    const val PACKAGE_OLA_PARTNER = "com.olacabs.partner"

    // Target keywords for the primary accept action
    val ACCEPT_KEYWORDS = listOf(
        "Accept",
        "Confirm",
        "Take Order",
        "Accept Order",
        "Accept Ride",
        "Go"
    )

    // Regex patterns for extracting fare amounts (e.g., ₹185, Rs. 150, 200 INR)
    val FARE_PATTERNS = listOf(
        Regex("""(?:₹|Rs\.?|INR)\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(\d+(?:\.\d{1,2})?)\s*(?:₹|Rs\.?|INR)""", RegexOption.IGNORE_CASE)
    )

    // Regex patterns for extracting distances (e.g., 4.2 km, 800 m)
    val DISTANCE_KM_PATTERNS = listOf(
        Regex("""(\d+(?:\.\d{1,2})?)\s*(?:km|kms|kilometer|kilometers)""", RegexOption.IGNORE_CASE)
    )

    val DISTANCE_METER_PATTERNS = listOf(
        Regex("""(\d+(?:\.\d{1,2})?)\s*(?:m|meter|meters)\b""", RegexOption.IGNORE_CASE)
    )

    // Anti-loop execution timing
    const val DEFAULT_CLICK_COOLDOWN_MS = 2500L
}
