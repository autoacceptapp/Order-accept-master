package com.example

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Result data class containing extracted order information from active screen nodes.
 */
data class ParsedOrder(
    val packageName: String,
    val fareAmount: Int?,
    val distanceKm: Float?,
    val acceptButtonNode: AccessibilityNodeInfo?,
    val matchedKeyword: String?,
    val extractedTexts: List<String>
) {
    val hasFare: Boolean get() = fareAmount != null
    val hasDistance: Boolean get() = distanceKm != null
    val hasAcceptButton: Boolean get() = acceptButtonNode != null

    /**
     * Complete order prompt is detected when fare, distance, and accept button are all present.
     */
    val isCompleteOffer: Boolean
        get() = hasFare && hasDistance && hasAcceptButton
}

/**
 * Robust utility for recursively traversing accessibility nodes and extracting ride/order metrics.
 */
object ScreenParser {

    private const val MAX_DEPTH = 32

    /**
     * Traverses the active root node hierarchy and extracts fare, pickup distance, and accept action nodes.
     */
    fun parseScreen(rootNode: AccessibilityNodeInfo?, packageName: String): ParsedOrder {
        if (rootNode == null) {
            return ParsedOrder(
                packageName = packageName,
                fareAmount = null,
                distanceKm = null,
                acceptButtonNode = null,
                matchedKeyword = null,
                extractedTexts = emptyList()
            )
        }

        var detectedFare: Int? = null
        var detectedDistance: Float? = null
        var detectedAcceptNode: AccessibilityNodeInfo? = null
        var detectedKeyword: String? = null
        val textsCollector = mutableListOf<String>()

        fun traverse(node: AccessibilityNodeInfo?, currentDepth: Int) {
            if (node == null || currentDepth > MAX_DEPTH) return

            // Inspect visible text and contentDescription
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            val candidateTexts = listOfNotNull(
                text?.takeIf { it.isNotEmpty() },
                desc?.takeIf { it.isNotEmpty() && it != text }
            )

            for (candidate in candidateTexts) {
                textsCollector.add(candidate)

                // 1. Check for Fare if not yet found
                if (detectedFare == null) {
                    val fare = extractFare(candidate)
                    if (fare != null) {
                        detectedFare = fare
                    }
                }

                // 2. Check for Distance if not yet found
                if (detectedDistance == null) {
                    val distance = extractDistance(candidate)
                    if (distance != null) {
                        detectedDistance = distance
                    }
                }

                // 3. Check for Accept / Action button
                if (detectedAcceptNode == null) {
                    val keyword = findAcceptKeyword(candidate)
                    if (keyword != null) {
                        detectedAcceptNode = node
                        detectedKeyword = keyword
                    }
                }
            }

            // Recurse over child nodes safely
            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                }
                traverse(child, currentDepth + 1)
            }
        }

        traverse(rootNode, 0)

        return ParsedOrder(
            packageName = packageName,
            fareAmount = detectedFare,
            distanceKm = detectedDistance,
            acceptButtonNode = detectedAcceptNode,
            matchedKeyword = detectedKeyword,
            extractedTexts = textsCollector
        )
    }

    /**
     * Extracts integer rupee amount from candidate string (e.g., "₹185.00" -> 185).
     */
    fun extractFare(text: String): Int? {
        for (pattern in AppConstants.FARE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val rawNumber = match.groupValues.getOrNull(1) ?: continue
                val parsed = rawNumber.toDoubleOrNull() ?: continue
                return parsed.toInt()
            }
        }
        return null
    }

    /**
     * Extracts pickup/delivery distance in kilometers (e.g. "4.2 km" -> 4.2f, "800 m" -> 0.8f).
     */
    fun extractDistance(text: String): Float? {
        // Match kilometers
        for (pattern in AppConstants.DISTANCE_KM_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val rawNumber = match.groupValues.getOrNull(1) ?: continue
                return rawNumber.toFloatOrNull()
            }
        }

        // Match meters and convert to km
        for (pattern in AppConstants.DISTANCE_METER_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val rawNumber = match.groupValues.getOrNull(1) ?: continue
                val meters = rawNumber.toFloatOrNull() ?: continue
                return meters / 1000f
            }
        }

        return null
    }

    /**
     * Checks if candidate text matches any accept action keyword.
     */
    fun findAcceptKeyword(text: String): String? {
        val trimmed = text.trim()
        for (keyword in AppConstants.ACCEPT_KEYWORDS) {
            // Match word boundary or exact containment
            if (trimmed.equals(keyword, ignoreCase = true) ||
                trimmed.contains(Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE))
            ) {
                return keyword
            }
        }
        return null
    }
}
