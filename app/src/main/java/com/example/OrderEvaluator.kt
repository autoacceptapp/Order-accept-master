package com.example

/**
 * Detailed outcome of evaluating parsed screen order metrics against user filter thresholds.
 */
data class EvaluationResult(
    val isAccepted: Boolean,
    val reason: String,
    val order: ParsedOrder
)

/**
 * Evaluates parsed order elements and enforces the Combination Trigger:
 * - Master automation must be running.
 * - The originating platform package must be enabled in settings.
 * - Minimum Fare, Maximum Distance, and Accept button MUST exist simultaneously.
 */
class OrderEvaluator {

    /**
     * Determines whether the parsed order qualifies for auto-acceptance.
     */
    fun evaluate(order: ParsedOrder, settings: FilterSettings, currentAppPackage: String): EvaluationResult {
        // 1. Check Master Automation switch
        if (!settings.isMasterEnabled) {
            return EvaluationResult(
                isAccepted = false,
                reason = "Automation stopped (Master toggle is OFF)",
                order = order
            )
        }

        // 2. Check Platform Filter based on package name
        if (!isPlatformEnabled(order.packageName, settings, currentAppPackage)) {
            return EvaluationResult(
                isAccepted = false,
                reason = "Platform '${order.packageName}' is disabled in settings",
                order = order
            )
        }

        // 3. Enforce Combination Trigger: Accept button presence
        val acceptNode = order.acceptButtonNode
        if (acceptNode == null) {
            return EvaluationResult(
                isAccepted = false,
                reason = "Combination trigger incomplete: Accept button not found",
                order = order
            )
        }

        // 4. Enforce Combination Trigger: Valid Fare threshold
        val fare = order.fareAmount
        if (fare == null) {
            return EvaluationResult(
                isAccepted = false,
                reason = "Combination trigger incomplete: Fare amount (₹) could not be parsed",
                order = order
            )
        }

        if (fare < settings.minFareAmount) {
            return EvaluationResult(
                isAccepted = false,
                reason = "Fare ₹$fare is below minimum threshold ₹${settings.minFareAmount}",
                order = order
            )
        }

        // 5. Enforce Combination Trigger: Valid Distance threshold
        val distance = order.distanceKm
        if (distance == null) {
            return EvaluationResult(
                isAccepted = false,
                reason = "Combination trigger incomplete: Distance (km) could not be parsed",
                order = order
            )
        }

        if (distance > settings.maxPickupDistance) {
            return EvaluationResult(
                isAccepted = false,
                reason = "Distance ${distance}km exceeds maximum threshold ${settings.maxPickupDistance}km",
                order = order
            )
        }

        // All combination trigger criteria met
        return EvaluationResult(
            isAccepted = true,
            reason = "Order qualifies: Fare ₹$fare >= ₹${settings.minFareAmount} and Distance ${distance}km <= ${settings.maxPickupDistance}km",
            order = order
        )
    }

    /**
     * Validates if the package belongs to an enabled platform.
     */
    fun isPlatformEnabled(packageName: String, settings: FilterSettings, currentAppPackage: String): Boolean {
        return when {
            // Self-app (Simulated order playground)
            packageName == currentAppPackage -> true

            // Rapido
            packageName == AppConstants.PACKAGE_RAPIDO_CAPTAIN ||
            packageName == AppConstants.PACKAGE_RAPIDO_ALT -> settings.isRapidoEnabled

            // Uber
            packageName == AppConstants.PACKAGE_UBER_DRIVER -> settings.isUberEnabled

            // Ola
            packageName == AppConstants.PACKAGE_OLA_PARTNER -> settings.isOlaEnabled

            // Unknown or unsupported package
            else -> false
        }
    }
}
