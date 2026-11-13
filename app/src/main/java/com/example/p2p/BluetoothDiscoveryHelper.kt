package com.example.p2p

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log

class BluetoothDiscoveryHelper(private val context: Context) {

    companion object {
        private val MINISHARE_SERVICE_UUID =
            ParcelUuid.fromString("0000B81D-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var lastHintTimestamp = 0L

    private fun isBleSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        if (!isBleSupported()) return
        val btAdapter = adapter ?: return
        if (!btAdapter.isEnabled) return

        stopAdvertising()
        try {
            val bleAdvertiser = btAdapter.bluetoothLeAdvertiser ?: return
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(MINISHARE_SERVICE_UUID)
                .build()

            val callback = object : AdvertiseCallback() {
                override fun onStartFailure(errorCode: Int) {
                    Log.d("BluetoothDiscovery", "BLE advertise failure: $errorCode")
                }
            }
            advertiser = bleAdvertiser
            advertiseCallback = callback
            bleAdvertiser.startAdvertising(settings, data, callback)
        } catch (e: SecurityException) {
            Log.d("BluetoothDiscovery", "Missing Bluetooth permission for advertising")
        } catch (e: Exception) {
            Log.d("BluetoothDiscovery", "BLE advertise error", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        val cb = advertiseCallback ?: return
        try {
            advertiser?.stopAdvertising(cb)
        } catch (_: Exception) {
        }
        advertiseCallback = null
        advertiser = null
    }

    @SuppressLint("MissingPermission")
    fun startScanning(onMiniShareBeaconFound: () -> Unit) {
        if (!isBleSupported()) return
        val btAdapter = adapter ?: return
        if (!btAdapter.isEnabled) return

        stopScanning()
        try {
            val bleScanner = btAdapter.bluetoothLeScanner ?: return
            val filter = ScanFilter.Builder()
                .setServiceUuid(MINISHARE_SERVICE_UUID)
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val now = System.currentTimeMillis()
                    if (now - lastHintTimestamp > 3000L) {
                        lastHintTimestamp = now
                        onMiniShareBeaconFound()
                    }
                }
            }
            scanner = bleScanner
            scanCallback = callback
            bleScanner.startScan(listOf(filter), settings, callback)
        } catch (e: SecurityException) {
            Log.d("BluetoothDiscovery", "Missing Bluetooth permission for scanning")
        } catch (e: Exception) {
            Log.d("BluetoothDiscovery", "BLE scan error", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        val cb = scanCallback ?: return
        try {
            scanner?.stopScan(cb)
        } catch (_: Exception) {
        }
        scanCallback = null
        scanner = null
    }

    fun stopAll() {
        stopAdvertising()
        stopScanning()
    }
}
