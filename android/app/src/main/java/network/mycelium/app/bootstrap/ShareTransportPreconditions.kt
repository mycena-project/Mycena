// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build

object ShareTransportPreconditions {

    const val ERROR_WIFI_HARDWARE = "wifi_unavailable"
    const val ERROR_WIFI_DISABLED = "wifi_disabled"
    const val ERROR_WIFI_DIRECT_UNSUPPORTED = "wifi_direct_unsupported"
    const val ERROR_LOCATION_DISABLED = "location_disabled"
    const val ERROR_PERMISSION = "permission_denied"

    fun wifiHardwarePresent(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)

    fun wifiDirectPresent(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    fun wifiEnabled(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false
        @Suppress("DEPRECATION")
        return wm.isWifiEnabled
    }

    /**
     * Deprecated [WifiManager.disconnect] / [WifiManager.enableNetwork] do not work for
     * third-party apps on API 29+.
     */
    fun canPauseStaWifi(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt < Build.VERSION_CODES.Q

    /**
     * @return error token for UI, or null if phone-to-phone may proceed to start hotspot.
     */
    fun phoneToPhoneBlocker(context: Context): String? {
        if (!wifiHardwarePresent(context)) return ERROR_WIFI_HARDWARE
        if (!SharePermissions.hasAll(context)) return ERROR_PERMISSION
        if (SharePermissions.locationToggleRequired() &&
            !SharePermissions.isLocationEnabled(context)
        ) {
            return ERROR_LOCATION_DISABLED
        }
        return null
    }

    /** Same-LAN mode needs an enabled Wi‑Fi STA (or existing connection). */
    fun localNetworkBlocker(context: Context): String? {
        if (!wifiHardwarePresent(context)) return ERROR_WIFI_HARDWARE
        if (!wifiEnabled(context)) return ERROR_WIFI_DISABLED
        return null
    }

    fun directFallbackBlocker(context: Context): String? {
        if (!wifiDirectPresent(context)) return ERROR_WIFI_DIRECT_UNSUPPORTED
        if (!wifiEnabled(context)) return ERROR_WIFI_DISABLED
        if (!SharePermissions.hasAll(context)) return ERROR_PERMISSION
        if (SharePermissions.locationToggleRequired() &&
            !SharePermissions.isLocationEnabled(context)
        ) {
            return ERROR_LOCATION_DISABLED
        }
        return null
    }
}
