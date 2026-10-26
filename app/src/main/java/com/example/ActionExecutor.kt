package com.example

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Result data class for an auto-click execution attempt.
 */
data class ExecutionResult(
    val success: Boolean,
    val message: String
)

/**
 * Responsible for finding the clickable target node and safely executing ACTION_CLICK
 * with an integrated anti-loop cooldown timer.
 */
class ActionExecutor(
    private val cooldownMs: Long = AppConstants.DEFAULT_CLICK_COOLDOWN_MS
) {

    private var lastClickTimeMillis: Long = 0L

    /**
     * Executes ACTION_CLICK on the provided target node or its closest clickable parent.
     */
    fun performClick(targetNode: AccessibilityNodeInfo?): ExecutionResult {
        if (targetNode == null) {
            return ExecutionResult(false, "Target node is null")
        }

        val currentTime = SystemClock.uptimeMillis()
        val elapsed = currentTime - lastClickTimeMillis
        if (elapsed < cooldownMs) {
            val waitRemaining = cooldownMs - elapsed
            Log.w(
                AppConstants.TAG,
                "[COOLDOWN ACTIVE] Skipping auto-click. Wait remaining: ${waitRemaining}ms"
            )
            return ExecutionResult(false, "Cooldown active (${waitRemaining}ms remaining)")
        }

        // 1. Find the clickable node (either the target itself or an enclosing parent container)
        val clickableNode = findClickableAncestorOrSelf(targetNode)

        val clickTarget = clickableNode ?: targetNode
        val actionSuccess = try {
            clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Error performing ACTION_CLICK: ${e.message}", e)
            false
        }

        if (actionSuccess) {
            lastClickTimeMillis = currentTime
            Log.i(
                AppConstants.TAG,
                "[AUTO-CLICK SUCCESS] Click executed on class: ${clickTarget.className} | ViewId: ${clickTarget.viewIdResourceName}"
            )
            return ExecutionResult(true, "Click dispatched successfully")
        } else {
            Log.w(
                AppConstants.TAG,
                "[AUTO-CLICK FAILED] performAction(ACTION_CLICK) returned false for node: ${clickTarget.className}"
            )
            return ExecutionResult(false, "Accessibility performAction(ACTION_CLICK) failed")
        }
    }

    /**
     * Searches upward for the nearest clickable node in the parent hierarchy.
     */
    private fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node

        var currentParent: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (currentParent != null && depth < 5) {
            if (currentParent.isClickable) {
                return currentParent
            }
            currentParent = currentParent.parent
            depth++
        }
        return null
    }

    /**
     * Reset the cooldown (useful for testing).
     */
    fun resetCooldown() {
        lastClickTimeMillis = 0L
    }
}
