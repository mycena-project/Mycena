// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime permissions for LocalOnlyHotspot and Wi‑Fi Direct APK share.
 * Bluetooth is not required for this path (Quick Share is a system sheet).
 */
object SharePermissions {

    fun required(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun missing(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        required(sdkInt).filterNot { granted(context, it) }.toTypedArray()

    fun hasAll(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        missing(context, sdkInt).isEmpty()

    /** LocalOnlyHotspot on API 26–32 typically also needs the location toggle ON. */
    fun locationToggleRequired(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt < Build.VERSION_CODES.TIRAMISU

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * After at least one request, denied permissions with no rationale are "Don't ask again".
     * Do not call this before the first request — Android returns false then too.
     */
    fun permanentlyDenied(
        activity: Activity,
        permissions: Array<String>,
    ): Boolean =
        permissions.any { permission ->
            ContextCompat.checkSelfPermission(activity, permission) !=
                PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
}
