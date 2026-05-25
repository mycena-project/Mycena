package network.mycelium.app.bootstrap

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * Sends a BLE advertisement inviting nearby devices to discover and install Mycelium.
 *
 * Payload:
 * - Known service UUID (recognized by Mycelium scanners)
 * - Optional peer ID prefix in manufacturer data
 *
 * Power: low-power advertising is typically ~0.1–0.5 mA.
 */
class BleBootstrapAdvertiser(private val context: Context) {

    companion object {
        val MYCELIUM_SERVICE_UUID: UUID =
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

        const val MANUFACTURER_ID = 0x4D59 // "MY" in ASCII

        private const val TAG = "BleBootstrap"
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var callback: AdvertiseCallback? = null

    fun startAdvertising(peerId: String, installUrl: String = "https://mycelium-beta.vercel.app/") {
        if (isAdvertising) return
        @Suppress("UNUSED_VARIABLE")
        val unusedInstallUrl = installUrl

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BLE advertising not supported on this device")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val peerIdBytes = peerId.toByteArray(Charsets.UTF_8).take(8).toByteArray()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(MYCELIUM_SERVICE_UUID))
            .addManufacturerData(MANUFACTURER_ID, peerIdBytes)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                isAdvertising = true
                Log.i(TAG, "BLE bootstrap advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE bootstrap advertising failed: $errorCode")
            }
        }

        advertiser?.startAdvertising(settings, data, callback)
    }

    fun stopAdvertising() {
        callback?.let { advertiser?.stopAdvertising(it) }
        isAdvertising = false
        Log.i(TAG, "BLE bootstrap advertising stopped")
    }

    fun isActive() = isAdvertising
}
