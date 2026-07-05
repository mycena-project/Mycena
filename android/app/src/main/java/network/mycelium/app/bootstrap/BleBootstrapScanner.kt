// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import network.mycelium.bindings.mycelium

/**
 * Scans for Mycelium GATT service and emits handoff targets for invite read + dial (F1).
 */
class BleBootstrapScanner(private val context: Context) {

    data class HandoffTarget(
        val device: BluetoothDevice,
        val rssi: Int,
    )

    data class NearbyNode(
        /** BLE MAC — display only; no peer id on air. */
        val deviceAddress: String,
        val rssi: Int,
        val firstSeen: Long = System.currentTimeMillis(),
    )

    private val _handoffTargets = MutableSharedFlow<HandoffTarget>(extraBufferCapacity = 8)
    val handoffTargets: SharedFlow<HandoffTarget> = _handoffTargets.asSharedFlow()

    private val _nearbyNodes = MutableSharedFlow<List<NearbyNode>>(replay = 1)
    val nearbyNodes: SharedFlow<List<NearbyNode>> = _nearbyNodes.asSharedFlow()

    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private var lowPowerScan = false
    private val seenAddresses = linkedMapOf<String, NearbyNode>()

    var onScanRestarted: (() -> Unit)? = null

    /** When Wi-Fi Aware handles discovery, reduce BLE scan duty (TransportScore policy). */
    fun setLowPowerScan(enabled: Boolean) {
        if (lowPowerScan == enabled) return
        lowPowerScan = enabled
        if (isScanning) {
            stopScanning()
            startScanning()
        }
    }

    fun startScanning() {
        if (isScanning) return

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as BluetoothManager
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return

        val filter =
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleMeshGatt.SERVICE_UUID))
                .build()

        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    if (lowPowerScan) {
                        ScanSettings.SCAN_MODE_LOW_POWER
                    } else {
                        ScanSettings.SCAN_MODE_BALANCED
                    },
                )
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .build()

        scanCallback =
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    val address = device.address ?: return
                    val node =
                        NearbyNode(
                            deviceAddress = address,
                            rssi = result.rssi,
                        )
                    seenAddresses[address] = node
                    _nearbyNodes.tryEmit(seenAddresses.values.toList())
                    if (callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH) {
                        Log.i(
                            "BleScanner",
                            "Mycelium BLE peer $address RSSI=${result.rssi} — attempting handoff",
                        )
                    }
                    mycelium.recordBleDiscoveryObservation(
                        80u,
                        64u,
                        true,
                    )
                    _handoffTargets.tryEmit(
                        HandoffTarget(device = device, rssi = result.rssi),
                    )
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w("BleScanner", "BLE scan failed ($errorCode) — restarting silently")
                    restartScanSilently()
                }
            }

        scanner.startScan(listOf(filter), settings, scanCallback!!)
        isScanning = true
    }

    /** Stop and restart scan without clearing the UI nearby cache (used by stale watchdog). */
    fun restartScanSilently() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        scanCallback?.let { cb ->
            runCatching { scanner?.stopScan(cb) }
        }
        isScanning = false
        startScanning()
        onScanRestarted?.invoke()
    }

    fun stopScanning() {
        scanCallback?.let {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as BluetoothManager
            bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(it)
        }
        isScanning = false
        seenAddresses.clear()
        _nearbyNodes.tryEmit(emptyList())
    }
}
