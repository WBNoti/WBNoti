package com.wbnoti

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("wbnoti_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_CALLS_ENABLED = booleanPreferencesKey("calls_enabled")
        private val KEY_SMS_ENABLED = booleanPreferencesKey("sms_enabled")
        private val KEY_CALENDAR_ENABLED = booleanPreferencesKey("calendar_enabled")
        private val KEY_ENABLED_PACKAGES = stringSetPreferencesKey("enabled_packages")
        private val KEY_PINNED_PACKAGES = stringSetPreferencesKey("pinned_packages")
        // Each element is "pkg|count", e.g. "com.spotify|2"
        private val KEY_PINNED_COUNTS = stringSetPreferencesKey("pinned_package_counts")
        private val KEY_CALLS_BUZZ_COUNT = androidx.datastore.preferences.core.intPreferencesKey("calls_buzz_count")
        private val KEY_SMS_BUZZ_COUNT = androidx.datastore.preferences.core.intPreferencesKey("sms_buzz_count")
        private val KEY_WHATSAPP_ENABLED = booleanPreferencesKey("whatsapp_enabled")
        private val KEY_WHATSAPP_BUZZ_COUNT = androidx.datastore.preferences.core.intPreferencesKey("whatsapp_buzz_count")
        private val KEY_SLACK_ENABLED = booleanPreferencesKey("slack_enabled")
        private val KEY_SLACK_BUZZ_COUNT = androidx.datastore.preferences.core.intPreferencesKey("slack_buzz_count")
        private val KEY_CALENDAR_BUZZ_COUNT = androidx.datastore.preferences.core.intPreferencesKey("calendar_buzz_count")
        private val KEY_SMS_FILTER = stringPreferencesKey("sms_filter")
        private val KEY_WHATSAPP_FILTER = stringPreferencesKey("whatsapp_filter")
        private val KEY_SLACK_FILTER = stringPreferencesKey("slack_filter")
    }

    val callsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALLS_ENABLED] ?: true }
    val smsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SMS_ENABLED] ?: true }
    val whatsappEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_WHATSAPP_ENABLED] ?: true }
    val slackEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SLACK_ENABLED] ?: true }
    val calendarEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALENDAR_ENABLED] ?: true }
    val enabledPackages: Flow<Set<String>> = context.dataStore.data.map { it[KEY_ENABLED_PACKAGES] ?: emptySet() }
    val pinnedPackages: Flow<Set<String>> = context.dataStore.data.map { it[KEY_PINNED_PACKAGES] ?: emptySet() }
    val pinnedPackageCounts: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_PINNED_COUNTS] ?: emptySet()).mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 1).coerceIn(1, 10) else null
        }.toMap()
    }
    val callsBuzzCount: Flow<Int> = context.dataStore.data.map { (it[KEY_CALLS_BUZZ_COUNT] ?: 1).coerceIn(1, 10) }
    val smsBuzzCount: Flow<Int> = context.dataStore.data.map { (it[KEY_SMS_BUZZ_COUNT] ?: 1).coerceIn(1, 10) }
    val whatsappBuzzCount: Flow<Int> = context.dataStore.data.map { (it[KEY_WHATSAPP_BUZZ_COUNT] ?: 1).coerceIn(1, 10) }
    val slackBuzzCount: Flow<Int> = context.dataStore.data.map { (it[KEY_SLACK_BUZZ_COUNT] ?: 1).coerceIn(1, 10) }
    val calendarBuzzCount: Flow<Int> = context.dataStore.data.map { (it[KEY_CALENDAR_BUZZ_COUNT] ?: 1).coerceIn(1, 10) }
    val smsFilter: Flow<String> = context.dataStore.data.map { it[KEY_SMS_FILTER] ?: "" }
    val whatsappFilter: Flow<String> = context.dataStore.data.map { it[KEY_WHATSAPP_FILTER] ?: "" }
    val slackFilter: Flow<String> = context.dataStore.data.map { it[KEY_SLACK_FILTER] ?: "" }

    suspend fun setCallsEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_CALLS_ENABLED] = enabled } }
    suspend fun setCallsBuzzCount(count: Int) { context.dataStore.edit { it[KEY_CALLS_BUZZ_COUNT] = count.coerceIn(1, 10) } }
    suspend fun setSmsEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_SMS_ENABLED] = enabled } }
    suspend fun setWhatsappEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_WHATSAPP_ENABLED] = enabled } }
    suspend fun setSlackEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_SLACK_ENABLED] = enabled } }
    suspend fun setCalendarEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_CALENDAR_ENABLED] = enabled } }
    suspend fun setSmsBuzzCount(count: Int) { context.dataStore.edit { it[KEY_SMS_BUZZ_COUNT] = count.coerceIn(1, 10) } }
    suspend fun setWhatsappBuzzCount(count: Int) { context.dataStore.edit { it[KEY_WHATSAPP_BUZZ_COUNT] = count.coerceIn(1, 10) } }
    suspend fun setSlackBuzzCount(count: Int) { context.dataStore.edit { it[KEY_SLACK_BUZZ_COUNT] = count.coerceIn(1, 10) } }
    suspend fun setCalendarBuzzCount(count: Int) { context.dataStore.edit { it[KEY_CALENDAR_BUZZ_COUNT] = count.coerceIn(1, 10) } }
    suspend fun setSmsFilter(filter: String) { context.dataStore.edit { it[KEY_SMS_FILTER] = filter } }
    suspend fun setWhatsappFilter(filter: String) { context.dataStore.edit { it[KEY_WHATSAPP_FILTER] = filter } }
    suspend fun setSlackFilter(filter: String) { context.dataStore.edit { it[KEY_SLACK_FILTER] = filter } }

    suspend fun setPackageEnabled(packageName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_ENABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.add(packageName) else current.remove(packageName)
            prefs[KEY_ENABLED_PACKAGES] = current
        }
    }

    suspend fun pinPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val pinned = prefs[KEY_PINNED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            pinned.add(packageName)
            prefs[KEY_PINNED_PACKAGES] = pinned
            // Enable it by default when pinned
            val enabled = prefs[KEY_ENABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            enabled.add(packageName)
            prefs[KEY_ENABLED_PACKAGES] = enabled
            // Set default count if not already stored
            val counts = prefs[KEY_PINNED_COUNTS]?.toMutableSet() ?: mutableSetOf()
            if (counts.none { it.startsWith("$packageName|") }) counts.add("$packageName|1")
            prefs[KEY_PINNED_COUNTS] = counts
        }
    }

    suspend fun unpinPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val pinned = prefs[KEY_PINNED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            pinned.remove(packageName)
            prefs[KEY_PINNED_PACKAGES] = pinned
            val counts = prefs[KEY_PINNED_COUNTS]?.toMutableSet() ?: mutableSetOf()
            counts.removeIf { it.startsWith("$packageName|") }
            prefs[KEY_PINNED_COUNTS] = counts
        }
    }

    suspend fun setPinnedPackageCount(packageName: String, count: Int) {
        context.dataStore.edit { prefs ->
            val counts = prefs[KEY_PINNED_COUNTS]?.toMutableSet() ?: mutableSetOf()
            counts.removeIf { it.startsWith("$packageName|") }
            counts.add("$packageName|${count.coerceIn(1, 10)}")
            prefs[KEY_PINNED_COUNTS] = counts
        }
    }
}
