package network.mycelium.app.bootstrap

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Holds a Wi‑Fi multicast lock so libp2p mDNS can discover peers on the same LAN (Android).
 */
class WifiMulticastLock(context: Context) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var lock: WifiManager.MulticastLock? = null

    fun acquire() {
        if (lock?.isHeld == true) return
        try {
            val multicastLock =
                wifiManager.createMulticastLock("mycelium-mdns").apply {
                    setReferenceCounted(true)
                }
            multicastLock.acquire()
            lock = multicastLock
            Log.i(TAG, "Wi‑Fi multicast lock acquired (LAN peer discovery)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }
    }

    fun release() {
        try {
            lock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not release multicast lock: ${e.message}")
        } finally {
            lock = null
        }
    }

    companion object {
        private const val TAG = "WifiMulticastLock"
    }
}
