// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import io.github.mycena_project.app.stability.StabilityDiagnostics

object LocationHelper {
    private val locationRegex =
        Regex("""(-?\d+\.\d+)° ([NS]), (-?\d+\.\d+)° ([EW])""")

    fun hasLocationPermission(context: Context): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Fetches the current position once and formats it as a chat message.
     * No continuous tracking.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationMessage(context: Context): String? {
        if (!hasLocationPermission(context)) return null

        return suspendCancellableCoroutine { cont ->
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    ?: run {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
            val provider =
                when {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                        LocationManager.GPS_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                        LocationManager.NETWORK_PROVIDER
                    else -> {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                }

            val lastKnown =
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    StabilityDiagnostics.record("LocationHelper.lastKnown", e)
                    null
                }
            if (lastKnown != null && lastKnown.time > System.currentTimeMillis() - 60_000) {
                cont.resume(formatLocation(lastKnown))
                return@suspendCancellableCoroutine
            }

            val listener =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (cont.isActive) cont.resume(formatLocation(location))
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

                    override fun onProviderEnabled(provider: String) {}

                    override fun onProviderDisabled(provider: String) {
                        locationManager.removeUpdates(this)
                        if (cont.isActive) cont.resume(null)
                    }
                }

            try {
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (e: SecurityException) {
                StabilityDiagnostics.record("LocationHelper.requestUpdates", e)
                cont.resume(null)
                return@suspendCancellableCoroutine
            } catch (e: IllegalArgumentException) {
                StabilityDiagnostics.record("LocationHelper.requestUpdates", e)
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }
    }

    fun formatLocation(location: Location): String {
        val lat = "%.4f".format(kotlin.math.abs(location.latitude))
        val lon = "%.4f".format(kotlin.math.abs(location.longitude))
        val latDir = if (location.latitude >= 0) "N" else "S"
        val lonDir = if (location.longitude >= 0) "E" else "W"
        val accuracy = if (location.hasAccuracy()) " (±${location.accuracy.toInt()}m)" else ""
        return "📍 $lat° $latDir, $lon° $lonDir$accuracy"
    }

    fun extractCoordinates(locationMsg: String): Pair<Double, Double>? {
        val match = locationRegex.find(locationMsg) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val latSign = if (match.groupValues[2] == "S") -1.0 else 1.0
        val lon = match.groupValues[3].toDoubleOrNull() ?: return null
        val lonSign = if (match.groupValues[4] == "W") -1.0 else 1.0
        return Pair(lat * latSign, lon * lonSign)
    }
}
