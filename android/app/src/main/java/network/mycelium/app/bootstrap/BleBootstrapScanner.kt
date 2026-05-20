package network.mycelium.app.bootstrap

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Scans for nearby Mycelium nodes advertising the bootstrap service UUID.
 */
class BleBootstrapScanner(private val context: Context) {

    private val _nearbyNodes = MutableStateFlow<List<NearbyNode>>(emptyList())
    val nearbyNodes: StateFlow<List<NearbyNode>> = _nearbyNodes

    private var scanCallback: ScanCallback? = null
    private var isScanning = false

    data class NearbyNode(
        val peerIdPrefix: String,
        val rssi: Int,
        val firstSeen: Long = System.currentTimeMillis(),
    )

    fun startScanning() {
        if (isScanning) return

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as BluetoothManager
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleBootstrapAdvertiser.MYCELIUM_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val manufacturerData = result.scanRecord
                    ?.getManufacturerSpecificData(BleBootstrapAdvertiser.MANUFACTURER_ID)
                    ?: return

                val peerIdPrefix = manufacturerData.take(8)
                    .joinToString("") { "%02x".format(it) }

                val node = NearbyNode(
                    peerIdPrefix = peerIdPrefix,
                    rssi = result.rssi,
                )

                val current = _nearbyNodes.value.toMutableList()
                if (current.none { it.peerIdPrefix == node.peerIdPrefix }) {
                    current.add(node)
                    _nearbyNodes.value = current
                    Log.i("BleScanner", "Found Mycelium node: $peerIdPrefix (RSSI: ${result.rssi})")
                }
            }
        }

        scanner.startScan(listOf(filter), settings, scanCallback!!)
        isScanning = true
    }

    fun stopScanning() {
        scanCallback?.let {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as BluetoothManager
            bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(it)
        }
        isScanning = false
        _nearbyNodes.value = emptyList()
    }
}
