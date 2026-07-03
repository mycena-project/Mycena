// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.mycelium.bindings.mycelium
import java.util.concurrent.ConcurrentHashMap

/**
 * Peripheral GATT server: invite read (F1) + mesh data write (F2).
 */
class BleInviteGattServer(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    companion object {
        private const val TAG = "BleInviteGatt"
        private val CCCD_UUID = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: android.bluetooth.BluetoothGattServer? = null
    private var inviteCharacteristic: BluetoothGattCharacteristic? = null
    private var cachedInvite: String = ""
    private val assemblers = ConcurrentHashMap<String, BleMeshChunker.Assembler>()

    private val serverCallback =
        object : android.bluetooth.BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: android.bluetooth.BluetoothDevice,
                status: Int,
                newState: Int,
            ) {
                Log.d(TAG, "central ${device.address} state=$newState status=$status")
                if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                    scope.launch(Dispatchers.IO) {
                        maybeRegisterCentral(device)
                    }
                }
            }

            override fun onCharacteristicReadRequest(
                device: android.bluetooth.BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (characteristic.uuid != BleMeshGatt.INVITE_CHAR_UUID) return
                refreshInviteValue()
                val full = cachedInvite.toByteArray(Charsets.UTF_8)
                val slice =
                    if (offset >= full.size) {
                        ByteArray(0)
                    } else {
                        full.copyOfRange(offset, full.size)
                    }
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    slice,
                )
            }

            override fun onCharacteristicWriteRequest(
                device: android.bluetooth.BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray,
            ) {
                if (characteristic.uuid != BleMeshGatt.MESH_DATA_CHAR_UUID) return
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value,
                    )
                }
                scope.launch(Dispatchers.IO) {
                    ingestMeshChunk(device, value)
                }
            }
        }

    fun start(): Boolean {
        if (gattServer != null) return true
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth unavailable")
            return false
        }
        val server = bluetoothManager.openGattServer(context, serverCallback)
        if (server == null) {
            Log.e(TAG, "openGattServer failed")
            return false
        }
        val service =
            BluetoothGattService(
                BleMeshGatt.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY,
            )
        val inviteChar =
            BluetoothGattCharacteristic(
                BleMeshGatt.INVITE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
        inviteChar.addDescriptor(
            BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ,
            ),
        )
        val meshChar =
            BluetoothGattCharacteristic(
                BleMeshGatt.MESH_DATA_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        service.addCharacteristic(inviteChar)
        service.addCharacteristic(meshChar)
        if (!server.addService(service)) {
            Log.e(TAG, "addService failed")
            server.close()
            return false
        }
        gattServer = server
        inviteCharacteristic = inviteChar
        refreshInviteValue()
        Log.i(TAG, "GATT server started (invite + mesh data)")
        return true
    }

    fun refreshInviteValue(): String {
        val invite =
            mycelium.getShareableMultiaddrs()
                .firstOrNull { it.startsWith("mycelium://") }
                .orEmpty()
        cachedInvite = invite
        if (invite.isNotEmpty()) {
            inviteCharacteristic?.value = invite.toByteArray(Charsets.UTF_8)
        }
        return invite
    }

    fun stop() {
        gattServer?.close()
        gattServer = null
        inviteCharacteristic = null
        cachedInvite = ""
        assemblers.clear()
        Log.i(TAG, "GATT server stopped")
    }

    /** When a central connects to write mesh data, learn its peer id via invite read (F2). */
    private suspend fun maybeRegisterCentral(device: android.bluetooth.BluetoothDevice) {
        val address = device.address ?: return
        if (BleMeshLinkRegistry.peerForAddress(address) != null) return
        val invite = BleInviteReader.readInvite(context, device) ?: return
        val peerId = PeerInvite.peerIdFrom(invite) ?: return
        BleMeshLinkRegistry.register(peerId, device)
        mycelium.registerBleLink(peerId, address)
        Log.i(TAG, "registered central $peerId @ $address")
    }

    private suspend fun ingestMeshChunk(device: android.bluetooth.BluetoothDevice, chunk: ByteArray) {
        val address = device.address ?: return
        var fromPeer = BleMeshLinkRegistry.peerForAddress(address)
        if (fromPeer == null) {
            maybeRegisterCentral(device)
            fromPeer = BleMeshLinkRegistry.peerForAddress(address)
        }
        val assembler = assemblers.getOrPut(address) { BleMeshChunker.Assembler() }
        val frame = assembler.push(chunk) ?: return
        val peer = fromPeer ?: run {
            Log.w(TAG, "mesh frame from unknown $address")
            return
        }
        try {
            mycelium.ingestBleFrame(peer, frame)
            Log.i(TAG, "ingested BLE frame from $peer (${frame.size} bytes)")
        } catch (e: Exception) {
            Log.w(TAG, "ingest BLE frame: ${e.message}")
        }
    }

    fun isRunning(): Boolean = gattServer != null

    fun hasInvite(): Boolean = cachedInvite.startsWith("mycelium://")
}
