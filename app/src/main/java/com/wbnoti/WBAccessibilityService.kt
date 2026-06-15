package com.wbnoti

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "WBA11y"

class WBAccessibilityService : AccessibilityService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var prefs: PreferencesManager

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        prefs = PreferencesManager(applicationContext)
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        Log.d(TAG, "Notification event from $pkg")

        val service = NotificationService.instance ?: return

        val notification = event.parcelableData as? Notification
        val isCall = notification?.category == Notification.CATEGORY_CALL
        // Extract text synchronously before the event is recycled by the system
        val notifText = buildString {
            append(event.text.joinToString(" "))
            notification?.extras?.let {
                append(" "); append(it.getString(Notification.EXTRA_TITLE) ?: "")
                append(" "); append(it.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "")
            }
        }

        scope.launch {
            val smsFilter = prefs.smsFilter.first()
            val whatsappFilter = prefs.whatsappFilter.first()
            val slackFilter = prefs.slackFilter.first()
            data class Result(val pattern: BuzzPattern, val count: Int)
            val result: Result? = when {
                isCall && pkg in CALL_PACKAGES -> if (prefs.callsEnabled.first()) Result(BuzzPattern.SMS, prefs.callsBuzzCount.first()) else null
                pkg in SMS_PACKAGES -> if (prefs.smsEnabled.first() && (smsFilter.isBlank() || notifText.contains(smsFilter.trim(), ignoreCase = true))) Result(BuzzPattern.SMS, prefs.smsBuzzCount.first()) else null
                pkg in WHATSAPP_PACKAGES -> if (prefs.whatsappEnabled.first() && (whatsappFilter.isBlank() || notifText.contains(whatsappFilter.trim(), ignoreCase = true))) Result(BuzzPattern.SMS, prefs.whatsappBuzzCount.first()) else null
                pkg in SLACK_PACKAGES -> if (prefs.slackEnabled.first() && (slackFilter.isBlank() || notifText.contains(slackFilter.trim(), ignoreCase = true))) Result(BuzzPattern.SMS, prefs.slackBuzzCount.first()) else null
                pkg in CALENDAR_PACKAGES -> if (prefs.calendarEnabled.first()) Result(BuzzPattern.CALENDAR, prefs.calendarBuzzCount.first()) else null
                pkg in prefs.pinnedPackages.first() -> {
                    val enabled = prefs.enabledPackages.first()
                    if (pkg in enabled) Result(BuzzPattern.SMS, prefs.pinnedPackageCounts.first()[pkg] ?: 1) else null
                }
                pkg in prefs.enabledPackages.first() -> Result(BuzzPattern.SMS, 1)
                else -> null
            }
            if (result != null && BuzzCoordinator.claim(if (isCall) "${pkg}_call" else pkg)) {
                Log.d(TAG, "Buzzing ${result.pattern} count=${result.count} for $pkg (isCall=$isCall)")
                service.buzzPattern(result.pattern, result.count)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        instance = null
    }

    companion object {
        @Volatile var instance: WBAccessibilityService? = null
            private set
    }
}
