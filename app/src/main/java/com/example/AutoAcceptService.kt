package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main AccessibilityService acting as the central Coordinator.
 * Follows Single Responsibility Principle by delegating:
 * - Parsing to ScreenParser
 * - Evaluation & Combination Trigger to OrderEvaluator
 * - Action execution & anti-loop cooldown to ActionExecutor
 */
class AutoAcceptService : AccessibilityService() {

    data class DetectionEvent(
        val id: String = java.util.UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val formattedTime: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
        val packageName: String,
        val matchedKeyword: String,
        val nodeText: String,
        val nodeClassName: String,
        val viewId: String?,
        val isClickable: Boolean,
        val bounds: Rect,
        val fareAmount: Int? = null,
        val distanceKm: Float? = null,
        val evaluationReason: String = "",
        val isAccepted: Boolean = false,
        val clickExecuted: Boolean = false
    )

    private val screenParser = ScreenParser
    private val orderEvaluator = OrderEvaluator()
    private val actionExecutor = ActionExecutor()
    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager.getInstance(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceActive.value = true
        Log.i(AppConstants.TAG, "=== AutoAcceptService Connected & Running ===")

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val eventPackage = event.packageName?.toString() ?: return
        val currentSettings = settingsManager.loadSettings()

        // 1. Package Name Filter Check: Do not scan if package is not a recognized & enabled target
        if (!orderEvaluator.isPlatformEnabled(eventPackage, currentSettings, packageName)) {
            return
        }

        // 2. Safely retrieve the root node of the active window
        val rootNode: AccessibilityNodeInfo? = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.w(AppConstants.TAG, "Unable to query rootInActiveWindow: ${e.message}")
            null
        }

        if (rootNode == null) return

        // 3. Delegate screen parsing to ScreenParser
        val parsedOrder = screenParser.parseScreen(rootNode, eventPackage)

        // Only evaluate if at least one candidate target text/element was detected
        if (parsedOrder.acceptButtonNode == null && parsedOrder.fareAmount == null && parsedOrder.distanceKm == null) {
            return
        }

        // 4. Delegate filtering & combination trigger enforcement to OrderEvaluator
        val evaluation = orderEvaluator.evaluate(parsedOrder, currentSettings, packageName)

        var clickExecuted = false

        // 5. If qualified, delegate auto-click execution to ActionExecutor
        if (evaluation.isAccepted) {
            Log.i(AppConstants.TAG, "[ORDER QUALIFIED] ${evaluation.reason}")
            val clickResult = actionExecutor.performClick(parsedOrder.acceptButtonNode)
            clickExecuted = clickResult.success
        } else {
            Log.d(AppConstants.TAG, "[ORDER REJECTED] ${evaluation.reason}")
        }

        // Record the event for UI live monitoring & Logcat visibility
        recordEvent(parsedOrder, evaluation, clickExecuted)
    }

    private fun recordEvent(
        order: ParsedOrder,
        evaluation: EvaluationResult,
        clickExecuted: Boolean
    ) {
        val acceptNode = order.acceptButtonNode
        val bounds = Rect()
        acceptNode?.getBoundsInScreen(bounds)

        val event = DetectionEvent(
            packageName = order.packageName,
            matchedKeyword = order.matchedKeyword ?: "N/A",
            nodeText = acceptNode?.text?.toString() ?: (order.extractedTexts.firstOrNull() ?: "Order prompt"),
            nodeClassName = acceptNode?.className?.toString() ?: "unknown",
            viewId = acceptNode?.viewIdResourceName,
            isClickable = acceptNode?.isClickable ?: false,
            bounds = bounds,
            fareAmount = order.fareAmount,
            distanceKm = order.distanceKm,
            evaluationReason = evaluation.reason,
            isAccepted = evaluation.isAccepted,
            clickExecuted = clickExecuted
        )

        val current = _recentDetections.value.toMutableList()
        // Anti-duplicate consecutive detections within 600ms
        val last = current.firstOrNull()
        if (last != null &&
            last.nodeText == event.nodeText &&
            last.packageName == event.packageName &&
            last.fareAmount == event.fareAmount &&
            last.distanceKm == event.distanceKm &&
            (event.timestamp - last.timestamp) < 600
        ) {
            return
        }

        current.add(0, event)
        if (current.size > 50) {
            current.removeAt(current.lastIndex)
        }
        _recentDetections.value = current
    }

    override fun onInterrupt() {
        Log.w(AppConstants.TAG, "AutoAcceptService interrupted by OS.")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceActive.value = false
        Log.i(AppConstants.TAG, "AutoAcceptService destroyed.")
    }

    companion object {
        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _recentDetections = MutableStateFlow<List<DetectionEvent>>(emptyList())
        val recentDetections: StateFlow<List<DetectionEvent>> = _recentDetections.asStateFlow()

        fun clearDetections() {
            _recentDetections.value = emptyList()
        }
    }
}
