// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

/**
 * Connectable BLE advertisement + GATT invite server for mesh handoff (ADR 002 F1).
 *
 * Passive scan filter uses [BleMeshGatt.SERVICE_UUID] only — no peer id in manufacturer data.
 */
class BleBootstrapAdvertiser(
    private val context: Context,
    private val gattServer: BleInviteGattServer,
) {

    companion object {
        private const val TAG = "BleBootstrap"
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var callback: AdvertiseCallback? = null

    fun startAdvertising(): Boolean {
        if (isAdvertising) return true
        if (!gattServer.start()) {
            Log.w(TAG, "GATT server failed — cannot advertise invite")
            return false
        }
        if (gattServer.refreshInviteValue().isEmpty()) {
            Log.w(TAG, "no signed invite yet — retry after node ready")
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return false
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BLE advertising not supported on this device")
            return false
        }

        val settings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(true)
                .setTimeout(0)
                .build()

        val data =
            AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(BleMeshGatt.SERVICE_UUID))
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()

        callback =
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    isAdvertising = true
                    Log.i(TAG, "connectable BLE advertising started (F1 handoff)")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "BLE advertising failed: $errorCode")
                }
            }

        advertiser?.startAdvertising(settings, data, callback)
        return true
    }

    fun refreshInvite() {
        gattServer.refreshInviteValue()
    }

    fun stopAdvertising() {
        callback?.let { advertiser?.stopAdvertising(it) }
        isAdvertising = false
        gattServer.stop()
        Log.i(TAG, "BLE advertising stopped")
    }

    fun isActive() = isAdvertising
}
