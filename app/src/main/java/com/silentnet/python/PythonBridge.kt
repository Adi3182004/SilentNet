package com.silentnet.python

import com.chaquo.python.Python

class PythonBridge {

    private fun module() = Python.getInstance().getModule("smart_route")

    fun priorityScore(text: String, hasAttachment: Boolean): Int {
        return try {
            module().callAttr("priority_score", text, hasAttachment).toInt()
        } catch (_: Throwable) {
            val base = text.length.coerceAtMost(60) / 2 + if (hasAttachment) 18 else 0
            base.coerceIn(0, 100)
        }
    }

    fun smartReply(text: String): String {
        return try {
            module().callAttr("smart_reply", text).toString()
        } catch (_: Throwable) {
            "Got it"
        }
    }

    fun predictLostLocation(reports: List<Map<String, Any>>): String {
        return try {
            module().callAttr("lostlink_prediction", reports).toString()
        } catch (_: Throwable) {
            "Unknown Location"
        }
    }

    fun estimateLastSeen(reports: List<Map<String, Any>>): Long {
        return try {
            module().callAttr("last_seen_estimator", reports).toLong()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    fun calculateConfidence(rssiValues: List<Int>): Double {
        return try {
            module().callAttr("recovery_confidence", rssiValues).toDouble()
        } catch (_: Throwable) {
            0.5
        }
    }
}
