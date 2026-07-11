// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.thermal

import network.mycelium.app.bootstrap.BleEnergyTier
import network.mycelium.app.bootstrap.BleScanDutyCycle
import network.mycelium.app.bootstrap.BleScanDutyIntervals
import network.mycelium.bindings.ThermalState
import kotlin.math.min

/**
 * Silent thermal throttling layered on top of the user [ActivityLevel] profile.
 *
 * Updates come from [ThermalMonitor] (Android [android.os.PowerManager] /
 * iOS `ProcessInfo.thermalState`). Also pushes [ThermalState] into Rust for
 * relay / Wi‑Fi Direct / custody policy.
 */
object ThermalEnforcement {
    enum class Level {
        Normal,
        Warm,
        Critical,
    }

    object Runtime {
        @Volatile
        var level: Level = Level.Normal
            private set

        @Volatile
        var rustState: ThermalState = ThermalState.NORMAL
            private set

        /** Tear down radio-heavy bearers when entering critical (no UI). */
        @Volatile
        var onEnterCritical: (() -> Unit)? = null

        fun update(state: ThermalState) {
            val previous = level
            rustState = state
            level =
                when (state) {
                    ThermalState.CRITICAL -> Level.Critical
                    ThermalState.WARM -> Level.Warm
                    else -> Level.Normal
                }
            if (level == Level.Critical && previous != Level.Critical) {
                onEnterCritical?.invoke()
            }
        }
    }

    /** Thermal tightening only — never loosens below the user activity tier. */
    fun effectiveBleTier(userTier: BleEnergyTier): BleEnergyTier =
        when (Runtime.level) {
            Level.Normal -> userTier
            Level.Warm ->
                when (userTier) {
                    BleEnergyTier.Active -> BleEnergyTier.Intermittent
                    else -> userTier
                }
            Level.Critical -> BleEnergyTier.Passive
        }

    /** Apply after activity tier + adaptive backoff intervals are computed. */
    fun applyToIntervals(base: BleScanDutyIntervals): BleScanDutyIntervals =
        when (Runtime.level) {
            Level.Normal -> base
            Level.Warm ->
                BleScanDutyIntervals(
                    scanWindowMs = (base.scanWindowMs * 3 / 4).coerceAtLeast(4_000L),
                    pauseMs =
                        min(
                            (base.pauseMs * 1.5).toLong(),
                            BleScanDutyCycle.MAX_PAUSE_MS,
                        ),
                )
            Level.Critical ->
                BleScanDutyIntervals(
                    scanWindowMs = 3_000L,
                    pauseMs = min(180_000L, BleScanDutyCycle.MAX_PAUSE_MS),
                )
        }

    fun effectiveWifiDirectIdleMs(activityIdleMs: Long): Long =
        when (Runtime.level) {
            Level.Normal -> activityIdleMs
            Level.Warm -> min(activityIdleMs, 15_000L)
            Level.Critical -> 5_000L
        }

    fun forceLowPowerBleScan(): Boolean = Runtime.level != Level.Normal

    fun isCriticallyThrottled(): Boolean = Runtime.level == Level.Critical
}
