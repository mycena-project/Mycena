// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.util.Log
import kotlinx.coroutines.delay
import io.github.mycena_project.app.discovery.MeshDiscoveryState
import io.github.mycena_project.app.thermal.ThermalEnforcement
import io.github.mycena_project.bindings.mycena

/**
 * Runs scan-window / pause loops for [BleBootstrapScanner] instead of continuous scanning.
 */
class BleScanDutyCycleRunner(
    private val scanner: BleBootstrapScanner,
    private val energyTierProvider: () -> BleEnergyTier,
    private val wifiAwarePreferredProvider: suspend () -> Boolean,
    private val connectedPeerCount: () -> Int,
) {
    private var emptyCycles = 0
    private var peersInCurrentWindow = false

    /** Wire from scanner when a handoff target or nearby node is observed. */
    fun onPeerObserved() {
        peersInCurrentWindow = true
        emptyCycles = BleScanDutyCycle.onPeerDetected()
    }

    suspend fun runLoop() {
        while (true) {
            if (connectedPeerCount() > 0) {
                if (scanner.isScanning()) {
                    scanner.stopScanOnly()
                    MeshDiscoveryState.onScanPaused()
                }
                emptyCycles = 0
                delay(5_000L)
                continue
            }

            val userTier = energyTierProvider()
            val tier = ThermalEnforcement.effectiveBleTier(userTier)
            val preferWifi = wifiAwarePreferredProvider()
            val policy =
                BleScanDutyPolicy(
                    tier = tier,
                    wifiAwarePreferred = preferWifi,
                    emptyCycles = emptyCycles,
                )
            val intervals =
                ThermalEnforcement.applyToIntervals(
                    BleScanDutyCycle.effectiveIntervals(policy),
                )

            peersInCurrentWindow = false
            scanner.setLowPowerScan(preferWifi || ThermalEnforcement.forceLowPowerBleScan())
            scanner.startScanning()
            MeshDiscoveryState.onScanWindowActive()
            scanner.onScanRestarted?.invoke()

            delay(intervals.scanWindowMs)

            scanner.stopScanOnly()
            MeshDiscoveryState.onScanPaused()

            if (!peersInCurrentWindow) {
                emptyCycles = BleScanDutyCycle.onEmptyCycle(emptyCycles)
                runCatching {
                    mycena.recordBleDiscoveryObservation(
                        intervals.scanWindowMs.toULong(),
                        0u,
                        false,
                    )
                }
            } else {
                emptyCycles = BleScanDutyCycle.onPeerDetected()
            }

            Log.d(
                TAG,
                "BLE duty pause ${intervals.pauseMs}ms tier=$tier userTier=$userTier " +
                    "thermal=${ThermalEnforcement.Runtime.level} wifiAware=$preferWifi " +
                    "emptyCycles=$emptyCycles",
            )
            delay(intervals.pauseMs)
        }
    }

    companion object {
        private const val TAG = "BleScanDuty"
    }
}
