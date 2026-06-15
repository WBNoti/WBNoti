package com.wbnoti

object BuzzCoordinator {
    private val recentBuzzes = mutableMapOf<String, Long>()
    private const val DEDUP_WINDOW_MS = 4000L

    // Returns true and records the buzz if no buzz was recorded for this package
    // within the dedup window. Prevents NLS and AccessibilityService double-firing.
    fun claim(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(this) {
            recentBuzzes.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
            val last = recentBuzzes[pkg] ?: 0L
            return if (now - last > DEDUP_WINDOW_MS) {
                recentBuzzes[pkg] = now
                true
            } else false
        }
    }
}
