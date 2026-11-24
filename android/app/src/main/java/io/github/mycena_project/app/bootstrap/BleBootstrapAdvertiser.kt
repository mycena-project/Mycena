// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import io.github.mycena_project.app.stability.StabilityDiagnostics

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
        /** Mac desktop matches service UUID or this local name during broad scan. */
        private const val ADVERTISE_NAME = "Mycena"
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var callback: AdvertiseCallback? = null
    private var savedAdapterName: String? = null

    fun startAdvertising(): Boolean {
        if (isAdvertising) return true
        if (!gattServer.start()) {
            Log.w(TAG, "GATT server failed — cannot advertise invite")
            return false
        }
        if (gattServer.refreshInviteValue().isEmpty()) {
            Log.w(TAG, "no signed invite yet — retry after node ready")
        }

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

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
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()

        // Primary packet: service UUID (handoff filter). Scan response: local name so
        // macOS CoreBluetooth exposes "Mycena" even when the 128-bit UUID lands only
        // in the response on some Android stacks.
        val data =
            AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(BleMeshGatt.SERVICE_UUID))
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()

        val scanResponse =
            AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(BleMeshGatt.SERVICE_UUID))
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .build()

        savedAdapterName = bluetoothAdapter.name
        runCatching {
            bluetoothAdapter.name = ADVERTISE_NAME
            Log.i(TAG, "BLE adapter name set to $ADVERTISE_NAME (was ${savedAdapterName ?: "?"})")
        }.onFailure {
            Log.w(TAG, "could not set BLE adapter name — Mac may rely on service UUID only", it)
        }

        callback =
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    isAdvertising = true
                    Log.i(
                        TAG,
                        "connectable BLE advertising started (F1 handoff, name=$ADVERTISE_NAME uuid=${BleMeshGatt.SERVICE_UUID})",
                    )
                }

                override fun onStartFailure(errorCode: Int) {
                    isAdvertising = false
                    Log.e(TAG, "BLE advertising failed: errorCode=$errorCode")
                    StabilityDiagnostics.info("BleAdvertise.onStartFailure", "errorCode=$errorCode")
                }
            }

        advertiser?.let { adv ->
            val cb = callback ?: return@let
            try {
                adv.startAdvertising(settings, data, scanResponse, cb)
            } catch (e: SecurityException) {
                StabilityDiagnostics.record("BleAdvertise.start", e)
                return false
            } catch (e: IllegalArgumentException) {
                StabilityDiagnostics.record("BleAdvertise.start", e)
                return false
            } catch (e: IllegalStateException) {
                StabilityDiagnostics.record("BleAdvertise.start", e)
                return false
            }
        }
        return true
    }

    fun refreshInvite() {
        gattServer.refreshInviteValue()
    }

    fun stopAdvertising() {
        callback?.let { cb ->
            try {
                advertiser?.stopAdvertising(cb)
            } catch (e: SecurityException) {
                StabilityDiagnostics.record("BleAdvertise.stop", e)
            } catch (e: IllegalStateException) {
                StabilityDiagnostics.record("BleAdvertise.stop", e)
            }
        }
        isAdvertising = false
        callback = null
        savedAdapterName?.let { previous ->
            runCatching { bluetoothAdapterFromContext()?.name = previous }
                .onFailure { Log.w(TAG, "could not restore BLE adapter name", it) }
        }
        savedAdapterName = null
        gattServer.stop()
        Log.i(TAG, "BLE advertising stopped")
    }

    private fun bluetoothAdapterFromContext() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isActive() = isAdvertising
}
