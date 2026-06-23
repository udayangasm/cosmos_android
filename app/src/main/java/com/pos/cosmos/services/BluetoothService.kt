package com.pos.cosmos.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothDeviceInfo(
    val address: String,
    val name: String,
    val type: String = "Unknown",
    val rssi: Int = 0
)

class BluetoothService(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private val _availableDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<BluetoothDeviceInfo>> = _availableDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDeviceInfo?>(null)
    val connectedDevice: StateFlow<BluetoothDeviceInfo?> = _connectedDevice.asStateFlow()

    private val foundDevices = mutableSetOf<String>()
    private var discoveryReceiver: BroadcastReceiver? = null

    fun hasBluetoothSupport(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun enableBluetooth(): Boolean {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter?.enable() ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun scanForDevices(onProgress: ((String) -> Unit)? = null) {
        if (!hasBluetoothSupport()) {
            _isScanning.value = false
            return
        }

        _isScanning.value = true
        _availableDevices.value = emptyList()
        foundDevices.clear()

        try {
            // Enable Bluetooth if not enabled
            if (!isBluetoothEnabled()) {
                enableBluetooth()
            }

            // First, add already paired devices
            addPairedDevices()

            // Then start discovery for new devices
            startDiscovery(onProgress)
        } catch (e: Exception) {
            e.printStackTrace()
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun addPairedDevices() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val bondedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
                val deviceList = mutableListOf<BluetoothDeviceInfo>()

                for (device in bondedDevices) {
                    val name = device.name ?: "Unknown"
                    val address = device.address ?: continue

                    if (!foundDevices.contains(address)) {
                        foundDevices.add(address)
                        deviceList.add(
                            BluetoothDeviceInfo(
                                address = address,
                                name = name,
                                type = "Paired"
                            )
                        )
                    }
                }

                if (deviceList.isNotEmpty()) {
                    _availableDevices.value = deviceList
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery(onProgress: ((String) -> Unit)? = null) {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            // Register broadcast receiver for discovery
            discoveryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device: BluetoothDevice? =
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            val rssi: Int =
                                intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                            if (device != null) {
                                val name = device.name ?: "Unknown Device"
                                val address = device.address ?: return

                                if (!foundDevices.contains(address)) {
                                    foundDevices.add(address)
                                    val newDevice = BluetoothDeviceInfo(
                                        address = address,
                                        name = name,
                                        type = "Discovered",
                                        rssi = rssi
                                    )

                                    // Update the list
                                    val current = _availableDevices.value.toMutableList()
                                    current.add(newDevice)
                                    _availableDevices.value = current.sortedByDescending { it.rssi }

                                    onProgress?.invoke("Found: $name")
                                }
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            _isScanning.value = false
                            unregisterReceiver()
                        }
                    }
                }
            }

            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)

            context.registerReceiver(discoveryReceiver, filter)

            // Start discovery
            bluetoothAdapter?.startDiscovery()
        } catch (e: Exception) {
            e.printStackTrace()
            _isScanning.value = false
        }
    }

    fun stopScanning() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isScanning.value = false
        unregisterReceiver()
    }

    private fun unregisterReceiver() {
        try {
            if (discoveryReceiver != null) {
                context.unregisterReceiver(discoveryReceiver)
                discoveryReceiver = null
            }
        } catch (e: Exception) {
            // Receiver already unregistered
        }
    }

    fun setConnectedDevice(device: BluetoothDeviceInfo?) {
        _connectedDevice.value = device
    }

    fun getDeviceByAddress(address: String): BluetoothDevice? {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter?.getRemoteDevice(address)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun needsRuntimePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
    }

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
