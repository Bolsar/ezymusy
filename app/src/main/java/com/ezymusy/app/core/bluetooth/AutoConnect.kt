package com.ezymusy.app.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A paired Bluetooth device. [name] is what the user sees; [address] matches it reliably once connected. */
data class BtDevice(val name: String, val address: String)

/** Picked device for "connect and Shuffle all when the app opens". Null means the feature is off. */
data class AutoConnectSetting(val enabled: Boolean, val device: BtDevice?)

// Outputs that carry music. SCO is call audio only. BLE constants are API 31+ ints; older devices never report them.
@SuppressLint("InlinedApi")
private val MUSIC_OUTPUTS = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
)

/**
 * True when an audio output of [type] with [name]/[address] is [target]. The name only counts when the address is
 * blank (before API 28): two headphones of the same model share a name.
 */
internal fun isTarget(type: Int, name: String, address: String, target: BtDevice) =
    type in MUSIC_OUTPUTS &&
        if (address.isBlank()) name == target.name else address.equals(target.address, ignoreCase = true)

/**
 * Turns on Bluetooth (with the user's tap: apps can't do it silently since Android 13) and waits for the picked
 * headphones. Apps can't force a connection either, so this relies on the headphones reconnecting on their own.
 */
class AutoConnect(private val context: Context) {
    // ponytail: SharedPreferences for two values; DataStore once settings grow.
    private val prefs = context.getSharedPreferences("auto_connect", Context.MODE_PRIVATE)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _setting = MutableStateFlow(read())
    val setting: StateFlow<AutoConnectSetting> = _setting.asStateFlow()

    val hasAdapter get() = adapter != null
    val isOn get() = adapter?.isEnabled == true

    /** BLUETOOTH_CONNECT is a runtime permission from Android 12; before that the install-time one covers it. */
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null

    val hasPermission
        get() = permission == null ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Empty while Bluetooth is off: Android only lists bonded devices when it is on. */
    @SuppressLint("MissingPermission") // callers check hasPermission
    fun paired(): List<BtDevice> {
        if (!hasPermission) return emptyList()
        return adapter?.bondedDevices.orEmpty()
            .map { BtDevice(it.name ?: it.address, it.address) }
            .sortedBy { it.name.lowercase() }
    }

    fun pick(device: BtDevice) = save(AutoConnectSetting(enabled = true, device = device))

    fun setEnabled(enabled: Boolean) = save(_setting.value.copy(enabled = enabled && _setting.value.device != null))

    /** True once [device] is an audio output, false after [timeout]. */
    suspend fun awaitConnected(device: BtDevice, timeout: Duration = 15.seconds): Boolean {
        val connected = CompletableDeferred<Unit>()
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                if (added.any { it.matches(device) }) connected.complete(Unit)
            }
        }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        return try {
            // Already connected before the callback was registered.
            if (audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.matches(device) }) return true
            withTimeoutOrNull(timeout) { connected.await() } != null
        } finally {
            audio.unregisterAudioDeviceCallback(callback)
        }
    }

    private fun AudioDeviceInfo.matches(target: BtDevice): Boolean {
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) address else ""
        return isTarget(type, productName?.toString().orEmpty(), address, target)
    }

    private fun read() = AutoConnectSetting(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        device = prefs.getString(KEY_ADDRESS, null)?.let { BtDevice(prefs.getString(KEY_NAME, null) ?: it, it) },
    )

    private fun save(setting: AutoConnectSetting) {
        prefs.edit {
            putBoolean(KEY_ENABLED, setting.enabled)
            putString(KEY_NAME, setting.device?.name)
            putString(KEY_ADDRESS, setting.device?.address)
        }
        _setting.value = setting
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_NAME = "device_name"
        const val KEY_ADDRESS = "device_address"
    }
}
