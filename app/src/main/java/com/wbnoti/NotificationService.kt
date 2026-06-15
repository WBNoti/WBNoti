@file:Suppress("DEPRECATION") // PhoneStateListener used intentionally for pre-API-31 fallback
package com.wbnoti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "WBNotiSvc"

private fun notificationText(sbn: StatusBarNotification): String {
    val extras = sbn.notification.extras
    val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
    val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
    val big = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
    return "$title $text $big"
}

private fun passesFilter(text: String, filter: String): Boolean =
    filter.isBlank() || text.contains(filter.trim(), ignoreCase = true)

class NotificationService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var prefs: PreferencesManager
    internal lateinit var bleClient: WBBleClient

    fun buzzPattern(pattern: BuzzPattern, count: Int = 1) = bleClient.buzzPattern(pattern, count)
    fun stopBuzzing() = bleClient.stopBuzzing()

    // ConcurrentHashMap.newKeySet() gives atomic add() (returns false if already present),
    // used below to make the poll-loop check-and-add a single atomic operation.
    private val seenKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null
    // Tracks the in-flight coroutine that reads prefs before buzzing on RINGING,
    // so a fast reject can cancel it before the buzz starts.
    private var ringingJob: Job? = null

    private fun handleCallState(state: Int) {
        Log.d(TAG, "handleCallState state=$state")
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "CALL_STATE_RINGING — buzzing")
                ringingJob?.cancel()
                ringingJob = scope.launch {
                    if (prefs.callsEnabled.first()) bleClient.buzzPattern(BuzzPattern.SMS, prefs.callsBuzzCount.first())
                }
            }
            TelephonyManager.CALL_STATE_IDLE, TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "Call ended/answered — stopping buzz")
                ringingJob?.cancel()
                ringingJob = null
                bleClient.stopBuzzing()
            }
        }
    }

    private fun registerTelephonyListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = handleCallState(state)
                }
                telephonyCallback = cb
                telephonyManager?.registerTelephonyCallback(mainExecutor, cb)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleCallState(state)
                }
                phoneStateListener = listener
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            Log.d(TAG, "Telephony listener registered")
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE not granted — call detection disabled: ${e.message}")
        }
    }

    private fun unregisterTelephonyListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        bleClient = WBBleClient(this)
        registerTelephonyListener()
        startPolling()
    }

    private fun startForegroundService() {
        val channelId = "wbnoti_service"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "WBNoti", NotificationManager.IMPORTANCE_MIN)
                    .apply { description = "Running in background to forward notifications to your band" }
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("WBNoti active")
            .setContentText("Forwarding notifications to your band")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }
    }

    // Samsung blocks some messaging apps (WhatsApp) from reaching third-party notification
    // listeners via semFlags=0x2008. Poll active notifications every 3s as fallback.
    private fun startPolling() {
        scope.launch {
            while (true) {
                delay(3000)
                try {
                    val active = activeNotifications ?: continue
                    for (sbn in active) {
                        val pkg = sbn.packageName
                        if (pkg !in ALL_MESSAGING_PACKAGES) continue
                        if (!seenKeys.add(sbn.key)) continue  // atomic check-and-add
                        val notifText = notificationText(sbn)
                        val (enabled, count) = when {
                            pkg in SMS_PACKAGES -> (prefs.smsEnabled.first() && passesFilter(notifText, prefs.smsFilter.first())) to prefs.smsBuzzCount.first()
                            pkg in WHATSAPP_PACKAGES -> (prefs.whatsappEnabled.first() && passesFilter(notifText, prefs.whatsappFilter.first())) to prefs.whatsappBuzzCount.first()
                            pkg in SLACK_PACKAGES -> (prefs.slackEnabled.first() && passesFilter(notifText, prefs.slackFilter.first())) to prefs.slackBuzzCount.first()
                            else -> false to 1
                        }
                        if (enabled && BuzzCoordinator.claim(pkg)) {
                            Log.d(TAG, "Polled buzz for $pkg count=$count")
                            bleClient.buzzPattern(BuzzPattern.SMS, count)
                        }
                    }
                    val activeKeys = active.map { it.key }.toSet()
                    seenKeys.retainAll(activeKeys)
                } catch (e: CancellationException) {
                    throw e  // let coroutine cancellation propagate normally
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterTelephonyListener()
        job.cancel()
        bleClient.close()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val notification = sbn.notification

        Log.d(TAG, "posted pkg=$pkg flags=${notification.flags} category=${notification.category}")

        val isCall = pkg in CALL_PACKAGES || notification.category == Notification.CATEGORY_CALL
        if (!isCall) {
            @Suppress("DEPRECATION")
            if (notification.priority < Notification.PRIORITY_DEFAULT) {
                Log.d(TAG, "skip: low priority"); return
            }
            if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
                Log.d(TAG, "skip: ongoing event"); return
            }
        }

        if (pkg in ALL_MESSAGING_PACKAGES) seenKeys.add(sbn.key)

        scope.launch {
            val enabledPkgs = prefs.enabledPackages.first()
            val notifText = notificationText(sbn)
            data class Result(val pattern: BuzzPattern, val count: Int)
            val result: Result? = when {
                isCall && pkg in CALL_PACKAGES -> if (prefs.callsEnabled.first()) Result(BuzzPattern.SMS, prefs.callsBuzzCount.first()) else null
                pkg in SMS_PACKAGES -> if (prefs.smsEnabled.first() && passesFilter(notifText, prefs.smsFilter.first())) Result(BuzzPattern.SMS, prefs.smsBuzzCount.first()) else null
                pkg in WHATSAPP_PACKAGES -> if (prefs.whatsappEnabled.first() && passesFilter(notifText, prefs.whatsappFilter.first())) Result(BuzzPattern.SMS, prefs.whatsappBuzzCount.first()) else null
                pkg in SLACK_PACKAGES -> if (prefs.slackEnabled.first() && passesFilter(notifText, prefs.slackFilter.first())) Result(BuzzPattern.SMS, prefs.slackBuzzCount.first()) else null
                pkg in CALENDAR_PACKAGES -> if (prefs.calendarEnabled.first()) Result(BuzzPattern.CALENDAR, prefs.calendarBuzzCount.first()) else null
                pkg in prefs.pinnedPackages.first() -> if (pkg in enabledPkgs) Result(BuzzPattern.SMS, prefs.pinnedPackageCounts.first()[pkg] ?: 1) else null
                pkg in enabledPkgs -> Result(BuzzPattern.SMS, 1)
                else -> null
            }
            if (result != null && BuzzCoordinator.claim(pkg)) {
                Log.d(TAG, "Buzzing pattern=${result.pattern} count=${result.count} for: $pkg")
                bleClient.buzzPattern(result.pattern, result.count)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in CALL_PACKAGES ||
            sbn.notification.category == Notification.CATEGORY_CALL) {
            bleClient.stopBuzzing()
        }
    }

    companion object {
        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _batteryLevel = MutableStateFlow<Int?>(null)
        val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

        @Volatile var instance: NotificationService? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        scope.launch { bleClient.state.collect { _connectionState.value = it } }
        scope.launch { bleClient.batteryLevel.collect { _batteryLevel.value = it } }
        startForegroundService()
        bleClient.connect()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
