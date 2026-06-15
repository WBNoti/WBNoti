package com.wbnoti

import android.Manifest
import android.app.Application
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application, private val prefs: PreferencesManager) : AndroidViewModel(app) {
    val callsEnabled = prefs.callsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val smsEnabled = prefs.smsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val whatsappEnabled = prefs.whatsappEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val slackEnabled = prefs.slackEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val calendarEnabled = prefs.calendarEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val enabledPackages = prefs.enabledPackages.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val pinnedPackages = prefs.pinnedPackages.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val pinnedPackageCounts = prefs.pinnedPackageCounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val callsBuzzCount = prefs.callsBuzzCount.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val smsBuzzCount = prefs.smsBuzzCount.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val whatsappBuzzCount = prefs.whatsappBuzzCount.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val slackBuzzCount = prefs.slackBuzzCount.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val calendarBuzzCount = prefs.calendarBuzzCount.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    fun setCallsEnabled(v: Boolean) = viewModelScope.launch { prefs.setCallsEnabled(v) }
    fun setCallsBuzzCount(count: Int) = viewModelScope.launch { prefs.setCallsBuzzCount(count) }
    fun setSmsEnabled(v: Boolean) = viewModelScope.launch { prefs.setSmsEnabled(v) }
    fun setWhatsappEnabled(v: Boolean) = viewModelScope.launch { prefs.setWhatsappEnabled(v) }
    fun setSlackEnabled(v: Boolean) = viewModelScope.launch { prefs.setSlackEnabled(v) }
    fun setCalendarEnabled(v: Boolean) = viewModelScope.launch { prefs.setCalendarEnabled(v) }
    fun setPackageEnabled(pkg: String, v: Boolean) = viewModelScope.launch { prefs.setPackageEnabled(pkg, v) }
    fun pinPackage(pkg: String) = viewModelScope.launch { prefs.pinPackage(pkg) }
    fun unpinPackage(pkg: String) = viewModelScope.launch { prefs.unpinPackage(pkg) }
    fun setPinnedPackageCount(pkg: String, count: Int) = viewModelScope.launch { prefs.setPinnedPackageCount(pkg, count) }
    fun setSmsBuzzCount(count: Int) = viewModelScope.launch { prefs.setSmsBuzzCount(count) }
    fun setWhatsappBuzzCount(count: Int) = viewModelScope.launch { prefs.setWhatsappBuzzCount(count) }
    fun setSlackBuzzCount(count: Int) = viewModelScope.launch { prefs.setSlackBuzzCount(count) }
    fun setCalendarBuzzCount(count: Int) = viewModelScope.launch { prefs.setCalendarBuzzCount(count) }

    val smsFilter = prefs.smsFilter.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val whatsappFilter = prefs.whatsappFilter.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val slackFilter = prefs.slackFilter.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setSmsFilter(f: String) = viewModelScope.launch { prefs.setSmsFilter(f) }
    fun setWhatsappFilter(f: String) = viewModelScope.launch { prefs.setWhatsappFilter(f) }
    fun setSlackFilter(f: String) = viewModelScope.launch { prefs.setSlackFilter(f) }

    private val _installedApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val installedApps: StateFlow<List<Pair<String, String>>> = _installedApps.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = getInstalledUserApps(app)
        }
    }
}

class MainActivity : ComponentActivity() {

    private val allPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // API 33+ only needs READ_BASIC_PHONE_STATE for call-state detection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_BASIC_PHONE_STATE)
        } else {
            add(Manifest.permission.READ_PHONE_STATE)
        }
    }.toTypedArray()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled via state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions.launch(allPermissions)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val prefs = remember { PreferencesManager(this) }
                val vm: MainViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>) =
                        MainViewModel(application, prefs) as T
                })
                WBNotiApp(vm)
            }
        }
    }
}

@Composable
fun WBNotiApp(vm: MainViewModel) {
    val context = LocalContext.current
    val notificationAccessGranted = remember {
        mutableStateOf(isNotificationAccessGranted(context))
    }
    val accessibilityGranted = remember {
        mutableStateOf(isAccessibilityGranted(context))
    }

    val bleClient = NotificationService.instance?.bleClient
    val connectionState by NotificationService.connectionState.collectAsState()
    val batteryLevel by NotificationService.batteryLevel.collectAsState()

    val callsEnabled by vm.callsEnabled.collectAsState()
    val smsEnabled by vm.smsEnabled.collectAsState()
    val whatsappEnabled by vm.whatsappEnabled.collectAsState()
    val slackEnabled by vm.slackEnabled.collectAsState()
    val calendarEnabled by vm.calendarEnabled.collectAsState()
    val enabledPackages by vm.enabledPackages.collectAsState()
    val pinnedPackages by vm.pinnedPackages.collectAsState()
    val pinnedPackageCounts by vm.pinnedPackageCounts.collectAsState()
    val callsBuzzCount by vm.callsBuzzCount.collectAsState()
    val smsBuzzCount by vm.smsBuzzCount.collectAsState()
    val whatsappBuzzCount by vm.whatsappBuzzCount.collectAsState()
    val slackBuzzCount by vm.slackBuzzCount.collectAsState()
    val calendarBuzzCount by vm.calendarBuzzCount.collectAsState()

    val installedApps by vm.installedApps.collectAsState()
    val smsFilter by vm.smsFilter.collectAsState()
    val whatsappFilter by vm.whatsappFilter.collectAsState()
    val slackFilter by vm.slackFilter.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted.value = isNotificationAccessGranted(context)
                accessibilityGranted.value = isAccessibilityGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("WBNoti", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "v${context.packageManager.getPackageInfo(context.packageName, 0).versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // Notification access banner
            if (!notificationAccessGranted.value) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Notification access required", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }) { Text("Grant Access") }
                        }
                    }
                }
            }

            // Accessibility access banner
            if (!accessibilityGranted.value) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Accessibility access required for SMS & WhatsApp", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Button(onClick = {
                                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) { Text("Grant Access") }
                        }
                    }
                }
            }

            // WHOOP connection
            item {
                Card {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WHOOP", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.ERROR -> "Failed — tap Connect to retry"
                                    ConnectionState.SCANNING -> "Scanning for WHOOP..."
                                    ConnectionState.CONNECTING -> "Connecting..."
                                    ConnectionState.READY -> "Connected"
                                    ConnectionState.DISCONNECTED -> "Disconnected"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (connectionState) {
                                    ConnectionState.READY -> MaterialTheme.colorScheme.primary
                                    ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                            if (batteryLevel != null) {
                                Text(
                                    text = "Battery: $batteryLevel%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        batteryLevel!! <= 20 -> MaterialTheme.colorScheme.error
                                        batteryLevel!! <= 40 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        when (connectionState) {
                            ConnectionState.DISCONNECTED, ConnectionState.ERROR ->
                                Button(onClick = { bleClient?.disconnect(); bleClient?.connect() }) { Text("Connect") }
                            ConnectionState.READY -> OutlinedButton(onClick = { bleClient?.disconnect() }) { Text("Disconnect") }
                            else -> CircularProgressIndicator(Modifier.size(32.dp))
                        }
                    }
                }
            }

            // Test buzz
            if (connectionState == ConnectionState.READY) {
                item {
                    OutlinedButton(onClick = { bleClient?.buzz() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Test Buzz")
                    }
                }
            }

            // Built-in toggles
            item {
                Text("Notification Types", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                BuzzCountRow(
                    label = "Phone Calls",
                    checked = callsEnabled,
                    buzzCount = callsBuzzCount,
                    onTest = if (connectionState == ConnectionState.READY) {{ bleClient?.buzzPattern(BuzzPattern.SMS, callsBuzzCount) }} else null,
                    onCheckedChange = { vm.setCallsEnabled(it) },
                    onCountChange = { vm.setCallsBuzzCount(it) }
                )
            }
            item {
                BuzzCountRow(
                    label = "SMS",
                    checked = smsEnabled,
                    buzzCount = smsBuzzCount,
                    onTest = if (connectionState == ConnectionState.READY) {{ bleClient?.buzzPattern(BuzzPattern.SMS, smsBuzzCount) }} else null,
                    onCheckedChange = { vm.setSmsEnabled(it) },
                    onCountChange = { vm.setSmsBuzzCount(it) },
                    filterText = smsFilter,
                    onFilterChange = { vm.setSmsFilter(it) }
                )
            }
            item {
                BuzzCountRow(
                    label = "WhatsApp",
                    checked = whatsappEnabled,
                    buzzCount = whatsappBuzzCount,
                    onTest = if (connectionState == ConnectionState.READY) {{ bleClient?.buzzPattern(BuzzPattern.SMS, whatsappBuzzCount) }} else null,
                    onCheckedChange = { vm.setWhatsappEnabled(it) },
                    onCountChange = { vm.setWhatsappBuzzCount(it) },
                    filterText = whatsappFilter,
                    onFilterChange = { vm.setWhatsappFilter(it) }
                )
            }
            item {
                BuzzCountRow(
                    label = "Slack",
                    checked = slackEnabled,
                    buzzCount = slackBuzzCount,
                    onTest = if (connectionState == ConnectionState.READY) {{ bleClient?.buzzPattern(BuzzPattern.SMS, slackBuzzCount) }} else null,
                    onCheckedChange = { vm.setSlackEnabled(it) },
                    onCountChange = { vm.setSlackBuzzCount(it) },
                    filterText = slackFilter,
                    onFilterChange = { vm.setSlackFilter(it) }
                )
            }
            item {
                BuzzCountRow(
                    label = "Google Calendar Meetings",
                    checked = calendarEnabled,
                    buzzCount = calendarBuzzCount,
                    onTest = if (connectionState == ConnectionState.READY) {{ bleClient?.buzzPattern(BuzzPattern.CALENDAR, calendarBuzzCount) }} else null,
                    onCheckedChange = { vm.setCalendarEnabled(it) },
                    onCountChange = { vm.setCalendarBuzzCount(it) }
                )
            }

            // Pinned apps shown as full BuzzCountRows (fall back to pkg name if not in installedApps)
            val installedMap = installedApps.toMap()
            items(pinnedPackages.toList().sorted()) { pkg ->
                val label = installedMap[pkg] ?: pkg
                val count = pinnedPackageCounts[pkg] ?: 1
                BuzzCountRow(
                    label = label,
                    checked = pkg in enabledPackages,
                    buzzCount = count,
                    onTest = if (connectionState == ConnectionState.READY) {{ bleClient?.buzzPattern(BuzzPattern.SMS, count) }} else null,
                    onCheckedChange = { vm.setPackageEnabled(pkg, it) },
                    onCountChange = { vm.setPinnedPackageCount(pkg, it) },
                    onRemove = { vm.unpinPackage(pkg) }
                )
            }

            // Per-app toggles (exclude pinned apps)
            item {
                Spacer(Modifier.height(4.dp))
                Text("Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(installedApps.filter { (pkg, _) -> pkg !in pinnedPackages }) { (pkg, label) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { vm.pinPackage(pkg) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("↑", style = MaterialTheme.typography.labelMedium) }
                    Switch(checked = pkg in enabledPackages, onCheckedChange = { vm.setPackageEnabled(pkg, it) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onTest: (() -> Unit)? = null, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            if (onTest != null) {
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.padding(end = 8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("Test", style = MaterialTheme.typography.labelSmall) }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun BuzzCountRow(
    label: String,
    checked: Boolean,
    buzzCount: Int,
    onTest: (() -> Unit)?,
    onCheckedChange: (Boolean) -> Unit,
    onCountChange: (Int) -> Unit,
    onRemove: (() -> Unit)? = null,
    filterText: String? = null,
    onFilterChange: ((String) -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                if (onRemove != null) {
                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.padding(end = 4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("×", style = MaterialTheme.typography.labelSmall) }
                }
                if (onTest != null) {
                    OutlinedButton(
                        onClick = onTest,
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("Test", style = MaterialTheme.typography.labelSmall) }
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Buzzes:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledIconButton(
                    onClick = { onCountChange(buzzCount - 1) },
                    enabled = buzzCount > 1,
                    modifier = Modifier.size(28.dp)
                ) { Text("−", style = MaterialTheme.typography.labelLarge) }
                Text(
                    "$buzzCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.widthIn(min = 16.dp),
                )
                FilledIconButton(
                    onClick = { onCountChange(buzzCount + 1) },
                    enabled = buzzCount < 10,
                    modifier = Modifier.size(28.dp)
                ) { Text("+", style = MaterialTheme.typography.labelLarge) }
            }
            if (filterText != null && onFilterChange != null) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = onFilterChange,
                    label = { Text("Keyword filter", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("all notifications", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

// @SuppressLint("QueryAllPackages"): the ACTION_MAIN/CATEGORY_LAUNCHER intent in <queries>
// covers all launcher-visible apps without needing QUERY_ALL_PACKAGES permission.
@SuppressLint("QueryAllPackages")
fun getInstalledUserApps(context: android.content.Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val skip = setOf(context.packageName) + CALL_PACKAGES + ALL_MESSAGING_PACKAGES + CALENDAR_PACKAGES +
        setOf("com.samsung.android.keyscafe", "com.samsung.android.app.routineplus")
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcherIntent, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it !in skip }
        .mapNotNull { pkg ->
            runCatching { pkg to pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }
        .sortedBy { it.second }
}

fun isNotificationAccessGranted(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.split(":").any {
        android.content.ComponentName.unflattenFromString(it)?.packageName == context.packageName
    }
}

fun isAccessibilityGranted(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services") ?: return false
    return flat.split(":").any {
        android.content.ComponentName.unflattenFromString(it)?.packageName == context.packageName
    }
}
