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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Long-lived GATT client for writing mesh frames to a peer's [BleMeshGatt.MESH_DATA_CHAR_UUID].
 */
class BleMeshDataSession private constructor(
    private val context: Context,
    private val device: BluetoothDevice,
    val peerId: String,
) {
    companion object {
        private const val TAG = "BleMeshData"
        private val sessions = mutableMapOf<String, BleMeshDataSession>()
        private val registryLock = Mutex()

        suspend fun open(context: Context, device: BluetoothDevice, peerId: String): BleMeshDataSession? {
            val address = device.address ?: return null
            return registryLock.withLock {
                sessions[address]?.let { return@withLock it }
                val session = BleMeshDataSession(context.applicationContext, device, peerId)
                if (session.connect()) {
                    sessions[address] = session
                    session
                } else {
                    null
                }
            }
        }

        suspend fun closeAll() {
            registryLock.withLock {
                sessions.values.forEach { it.close() }
                sessions.clear()
            }
        }
    }

    private var gatt: BluetoothGatt? = null
    private var meshChar: BluetoothGattCharacteristic? = null
    private val writeMutex = Mutex()

    private suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        var done = false
        fun finish(ok: Boolean) {
            if (done) return
            done = true
            if (cont.isActive) cont.resume(ok)
        }
        val callback =
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (!done) finish(false)
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(false)
                        return
                    }
                    val service = g.getService(BleMeshGatt.SERVICE_UUID)
                    meshChar = service?.getCharacteristic(BleMeshGatt.MESH_DATA_CHAR_UUID)
                    finish(meshChar != null)
                }
            }
        gatt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
        cont.invokeOnCancellation { finish(false) }
    }

    suspend fun sendFrame(frame: ByteArray): Boolean = writeMutex.withLock {
        val g = gatt ?: return false
        val base = meshChar ?: return false
        for (chunk in BleMeshChunker.chunkFrame(frame)) {
            base.value = chunk
            if (!g.writeCharacteristic(base)) {
                Log.w(TAG, "write failed to ${device.address}")
                return false
            }
            delay(25)
        }
        Log.d(TAG, "sent ${frame.size} byte frame to $peerId via BLE")
        true
    }

    fun close() {
        gatt?.close()
        gatt = null
        meshChar = null
    }
}
