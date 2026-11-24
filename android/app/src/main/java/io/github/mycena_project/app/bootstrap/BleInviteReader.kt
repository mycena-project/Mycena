// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Central role: connect over BLE, read the invite characteristic (F1 handoff).
 */
object BleInviteReader {

    private const val TAG = "BleInviteReader"
    private const val READ_TIMEOUT_MS = 12_000L

    suspend fun readInvite(context: Context, device: BluetoothDevice): String? {
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var gatt: BluetoothGatt? = null
                var finished = false

                fun complete(invite: String?) {
                    if (finished) return
                    finished = true
                    try {
                        gatt?.disconnect()
                        gatt?.close()
                    } catch (_: Exception) {
                    }
                    if (cont.isActive) cont.resume(invite)
                }

                val callback =
                    object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            g: BluetoothGatt,
                            status: Int,
                            newState: Int,
                        ) {
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    Log.d(TAG, "connected to ${device.address}, discovering services")
                                    g.discoverServices()
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> {
                                    if (!finished) {
                                        Log.d(TAG, "disconnected from ${device.address} status=$status")
                                        complete(null)
                                    }
                                }
                            }
                        }

                        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                complete(null)
                                return
                            }
                            val service = g.getService(BleMeshGatt.SERVICE_UUID)
                            val char = service?.getCharacteristic(BleMeshGatt.INVITE_CHAR_UUID)
                            if (char == null) {
                                Log.w(TAG, "invite characteristic missing on ${device.address}")
                                complete(null)
                                return
                            }
                            if (!g.readCharacteristic(char)) {
                                Log.w(TAG, "readCharacteristic failed on ${device.address}")
                                complete(null)
                            }
                        }

                        override fun onCharacteristicRead(
                            g: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            status: Int,
                        ) {
                            onCharacteristicReadCompat(g, characteristic, status, characteristic.value)
                        }

                        @Deprecated("Deprecated in API 33")
                        override fun onCharacteristicRead(
                            g: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            value: ByteArray,
                            status: Int,
                        ) {
                            onCharacteristicReadCompat(g, characteristic, status, value)
                        }

                        private fun onCharacteristicReadCompat(
                            g: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            status: Int,
                            value: ByteArray?,
                        ) {
                            if (characteristic.uuid != BleMeshGatt.INVITE_CHAR_UUID) return
                            if (status != BluetoothGatt.GATT_SUCCESS || value == null) {
                                complete(null)
                                return
                            }
                            val text = value.toString(Charsets.UTF_8).trim()
                            if (text.startsWith("mycena://")) {
                                Log.i(TAG, "read invite from ${device.address} (${text.length} chars)")
                                complete(text)
                            } else {
                                Log.w(TAG, "invalid invite payload from ${device.address}")
                                complete(null)
                            }
                        }
                    }

                gatt =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        @Suppress("DEPRECATION")
                        device.connectGatt(context, false, callback)
                    }

                cont.invokeOnCancellation {
                    complete(null)
                }
            }
        }
    }
}
