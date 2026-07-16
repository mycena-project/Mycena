// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BleBootstrapPermissions {
    /** BLE + Wi‑Fi discovery permissions needed when bootstrap starts Wi‑Fi Aware / Direct. */
    val required: Array<String>
        get() =
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    add(Manifest.permission.BLUETOOTH)
                    add(Manifest.permission.BLUETOOTH_ADMIN)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.NEARBY_WIFI_DEVICES)
                } else {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }.toTypedArray()

    fun hasAll(context: Context): Boolean =
        required.all { granted(context, it) }

    /** Wi‑Fi Aware / NSD fallback (without BLE). */
    fun hasWifiDiscovery(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
}
