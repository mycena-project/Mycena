// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import io.github.mycena_project.bindings.EnergyState
import kotlin.math.min
import kotlin.math.pow

/**
 * BLE discovery duty cycle: scan window + pause instead of continuous scanning.
 *
 * Base intervals follow the three user energy tiers ([EnergyState]). When
 * [TransportScore] prefers Wi‑Fi Aware for discovery, BLE pauses are lengthened.
 * Adaptive backoff stretches pauses after consecutive empty cycles (no peers),
 * independent of tier; any peer sighting resets backoff immediately.
 *
 * See `docs/BATTERY_PROFILING_RESULTS.md` (continuous scan, not LOW_LATENCY).
 */
enum class BleEnergyTier {
    Active,
    Intermittent,
    Passive,
    ;

    companion object {
        fun fromEnergyState(state: EnergyState): BleEnergyTier =
            when (state) {
                EnergyState.ACTIVE -> Active
                EnergyState.INTERMITTENT -> Intermittent
                EnergyState.PASSIVE -> Passive
            }

        fun fromPrefsLabel(label: String?): BleEnergyTier =
            when (label) {
                "Intermittent" -> Intermittent
                "Passive" -> Passive
                else -> Active
            }
    }
}

data class BleScanDutyIntervals(
    val scanWindowMs: Long,
    val pauseMs: Long,
)

data class BleScanDutyPolicy(
    val tier: BleEnergyTier,
    /** From [io.github.mycena_project.bindings.mycena.preferWifiAwareForDiscovery]. */
    val wifiAwarePreferred: Boolean,
    /** Consecutive completed scan windows with zero peer sightings. */
    val emptyCycles: Int,
)

object BleScanDutyCycle {
    /** Base scan/pause per energy tier (ms). */
    fun tierIntervals(tier: BleEnergyTier): BleScanDutyIntervals =
        when (tier) {
            BleEnergyTier.Active ->
                BleScanDutyIntervals(scanWindowMs = 10_000L, pauseMs = 5_000L)
            BleEnergyTier.Intermittent ->
                BleScanDutyIntervals(scanWindowMs = 8_000L, pauseMs = 22_000L)
            BleEnergyTier.Passive ->
                BleScanDutyIntervals(scanWindowMs = 5_000L, pauseMs = 55_000L)
        }

    /** Wi‑Fi Aware carries discovery — stretch BLE pauses (TransportScore policy). */
    const val WIFI_AWARE_PAUSE_MULTIPLIER: Double = 2.0

    /** Empty cycles before adaptive backoff begins. */
    const val BACKOFF_START_AFTER_EMPTY_CYCLES: Int = 3

    /** Per-step pause multiplier once backoff is active. */
    const val BACKOFF_STEP_MULTIPLIER: Double = 1.6

    const val MAX_PAUSE_MS: Long = 600_000L

    fun effectiveIntervals(policy: BleScanDutyPolicy): BleScanDutyIntervals {
        val base = tierIntervals(policy.tier)
        var pauseMs = base.pauseMs.toDouble()
        if (policy.wifiAwarePreferred) {
            pauseMs *= WIFI_AWARE_PAUSE_MULTIPLIER
        }
        val extraSteps =
            (policy.emptyCycles - BACKOFF_START_AFTER_EMPTY_CYCLES).coerceAtLeast(0)
        if (extraSteps > 0) {
            pauseMs *= BACKOFF_STEP_MULTIPLIER.pow(extraSteps.toDouble())
        }
        return BleScanDutyIntervals(
            scanWindowMs = base.scanWindowMs,
            pauseMs = min(pauseMs.toLong(), MAX_PAUSE_MS),
        )
    }

    /** Call after a scan window completes with no peer sightings. */
    fun onEmptyCycle(previousEmptyCycles: Int): Int = previousEmptyCycles + 1

    /** Call when any Mycena peer is seen — immediate backoff reset. */
    fun onPeerDetected(): Int = 0
}
