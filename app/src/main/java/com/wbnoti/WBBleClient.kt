@file:Suppress("DEPRECATION") // connectGatt deprecated in API 33 with no public replacement
package com.wbnoti

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "WBBleClient"

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, READY, ERROR }

enum class BuzzPattern { SMS, CALENDAR }

class WBBleClient(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter get() = bluetoothManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bleJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + bleJob)
    private var patternJob: Job? = null
    private var reconnectJob: Job? = null

    // Persists the MAC of the device whose GATT services confirmed it is a real WHOOP band,
    // so future connects go directly to that address rather than trusting any "WHOOP"-named device.
    private val devicePrefs = context.getSharedPreferences("wbnoti_ble", Context.MODE_PRIVATE)
    private fun storedWhoopMac(): String? = devicePrefs.getString("whoop_mac", null)
    private fun storeWhoopMac(mac: String) = devicePrefs.edit().putString("whoop_mac", mac).apply()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var cmdCharacteristic: BluetoothGattCharacteristic? = null
    private val seq = AtomicInteger(0)
    @Volatile private var isWhoop5 = false
    @Volatile private var manualDisconnect = false

    private val connectionTimeoutRunnable = Runnable {
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.SCANNING) {
            Log.e(TAG, "Connection timed out")
            gatt?.close()
            gatt = null
            cmdCharacteristic = null
            _state.value = ConnectionState.ERROR
        }
    }

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = try { device.name } catch (e: SecurityException) { null } ?: return
            Log.d(TAG, "Found device: $name")
            if (!name.startsWith("WHOOP")) return
            bluetoothAdapter.bluetoothLeScanner?.stopScan(this)
            _state.value = ConnectionState.CONNECTING
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _state.value = ConnectionState.ERROR
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Connection state error status=$status newState=$newState")
                gatt.close()
                this@WBBleClient.gatt = null
                cmdCharacteristic = null
                _batteryLevel.value = null
                _state.value = ConnectionState.ERROR
                if (!manualDisconnect) {
                    reconnectJob = scope.launch {
                        delay(5000)
                        connect()
                    }
                }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected, discovering services")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected")
                gatt.close()
                this@WBBleClient.gatt = null
                cmdCharacteristic = null
                _batteryLevel.value = null
                _state.value = ConnectionState.DISCONNECTED
                if (!manualDisconnect) {
                    reconnectJob = scope.launch {
                        delay(5000)
                        connect()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            gatt.services.forEach { Log.d(TAG, "Service: ${it.uuid}") }
            val service5 = gatt.getService(WBProtocol.SERVICE_UUID_5)
            val service4 = gatt.getService(WBProtocol.SERVICE_UUID)
            when {
                service5 != null -> {
                    isWhoop5 = true
                    cmdCharacteristic = service5.getCharacteristic(WBProtocol.CMD_TO_STRAP_5)
                    Log.d(TAG, "WHOOP 5.0 ready")
                }
                service4 != null -> {
                    isWhoop5 = false
                    cmdCharacteristic = service4.getCharacteristic(WBProtocol.CMD_TO_STRAP)
                    Log.d(TAG, "WHOOP 4.0 ready")
                }
                else -> {
                    Log.e(TAG, "WHOOP service not found")
                    return
                }
            }
            if (cmdCharacteristic != null) {
                mainHandler.removeCallbacks(connectionTimeoutRunnable)
                storeWhoopMac(gatt.device.address)
                _state.value = ConnectionState.READY
                val battChar = gatt.getService(WBProtocol.BATTERY_SERVICE_UUID)
                    ?.getCharacteristic(WBProtocol.BATTERY_LEVEL_UUID)
                if (battChar != null) gatt.readCharacteristic(battChar)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == WBProtocol.BATTERY_LEVEL_UUID &&
                status == BluetoothGatt.GATT_SUCCESS) {
                _batteryLevel.value = characteristic.value?.firstOrNull()?.toInt()?.and(0xFF)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (characteristic.uuid == WBProtocol.BATTERY_LEVEL_UUID &&
                status == BluetoothGatt.GATT_SUCCESS) {
                _batteryLevel.value = value.firstOrNull()?.toInt()?.and(0xFF)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "Write status: $status")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        manualDisconnect = false
        if (_state.value != ConnectionState.DISCONNECTED && _state.value != ConnectionState.ERROR) return
        mainHandler.removeCallbacks(connectionTimeoutRunnable)
        mainHandler.postDelayed(connectionTimeoutRunnable, 10_000)

        val storedMac = storedWhoopMac()
        if (storedMac != null) {
            Log.d(TAG, "Connecting to stored WHOOP MAC $storedMac")
            _state.value = ConnectionState.CONNECTING
            gatt = bluetoothAdapter.getRemoteDevice(storedMac)
                .connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK)
            return
        }

        val bonded = bluetoothAdapter.bondedDevices
            ?.firstOrNull { it.name?.startsWith("WHOOP") == true }
        if (bonded != null) {
            Log.d(TAG, "Found bonded WHOOP: ${bonded.name}, connecting directly")
            _state.value = ConnectionState.CONNECTING
            gatt = bonded.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK)
            return
        }

        Log.d(TAG, "No bonded WHOOP found, scanning...")
        _state.value = ConnectionState.SCANNING
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bluetoothAdapter.bluetoothLeScanner?.startScan(emptyList(), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        stopBuzzing()
        mainHandler.removeCallbacks(connectionTimeoutRunnable)
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        val g = gatt
        gatt = null
        cmdCharacteristic = null
        _batteryLevel.value = null
        g?.disconnect()
        g?.close()
        _state.value = ConnectionState.DISCONNECTED
    }

    fun close() {
        disconnect()
        bleJob.cancel()
    }

    @SuppressLint("MissingPermission")
    fun buzz() {
        val characteristic = cmdCharacteristic ?: return
        if (_state.value != ConnectionState.READY) return

        val s = (seq.getAndIncrement() and 0xFF).toByte()
        val packet = if (isWhoop5) WBProtocol.buildHapticPacket5(s)
                     else WBProtocol.buildHapticPacket4(s)
        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeCharacteristic(characteristic, packet, writeType)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = packet
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            gatt?.writeCharacteristic(characteristic)
        }
    }

    fun buzzPattern(pattern: BuzzPattern, count: Int = 1) {
        patternJob?.cancel()
        val interPulseDelay = if (pattern == BuzzPattern.CALENDAR) 1500L else 1000L
        patternJob = scope.launch {
            repeat(count.coerceIn(1, 10)) { i ->
                if (i > 0) delay(interPulseDelay)
                buzz()
            }
        }
    }

    fun stopBuzzing() {
        patternJob?.cancel()
        patternJob = null
    }
}
