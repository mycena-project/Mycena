// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.settings

import android.content.Context
import android.content.SharedPreferences
import network.mycelium.app.bootstrap.BleEnergyTier
import network.mycelium.app.bootstrap.WifiDirectBearer
import network.mycelium.bindings.EnergyState
import network.mycelium.bindings.mycelium

/**
 * User-facing activity level ("Aktivität") — maps to a fixed internal bundle
 * (BLE scan duty, Wi‑Fi Direct idle readiness, Rust energy / custody relay policy).
 * No raw parameters are exposed in the UI.
 */
enum class ActivityLevel(val storageKey: String) {
    AlwaysActive("always"),
    Balanced("balanced"),
    BatterySaver("battery_saver"),
    ;

    companion object {
        fun fromStorageKey(key: String?): ActivityLevel? =
            entries.find { it.storageKey == key }

        /** Legacy [energy_state] pref values before activity_level existed. */
        fun fromLegacyEnergyState(energyState: String?): ActivityLevel =
            when (energyState) {
                "Passive" -> BatterySaver
                "Active" -> AlwaysActive
                else -> Balanced
            }
    }
}

/** Internal custody relay posture (driven by [EnergyState] in Rust). */
enum class CustodyRelayPriority {
    /** Forwards normal + high priority; highest spray budget. */
    High,
    /** Balanced forwarding. */
    Medium,
    /** High-priority custody only; minimal relay budget. */
    Low,
}

data class ActivityProfileBundle(
    val energyState: EnergyState,
    val bleTier: BleEnergyTier,
    /** How long Wi‑Fi Direct stays up after last bulk transfer before teardown. */
    val wifiDirectIdleMs: Long,
    val custodyPriority: CustodyRelayPriority,
)

/** Process-wide profile knobs read by [MeshService] / [WifiDirectBearerHost]. */
object ActivityProfileRuntime {
    @Volatile
    var wifiDirectIdleDowngradeMs: Long = WifiDirectBearer.IDLE_DOWNGRADE_MS_BALANCED
        private set

    fun applyBundle(bundle: ActivityProfileBundle) {
        wifiDirectIdleDowngradeMs = bundle.wifiDirectIdleMs
    }
}

object ActivityProfiles {
    const val PREFS_ACTIVITY_LEVEL = "activity_level"
    /** Legacy key — kept in sync for Rust + existing readers. */
    const val PREFS_ENERGY_STATE = "energy_state"

    val defaultLevel: ActivityLevel = ActivityLevel.Balanced

    fun bundle(level: ActivityLevel): ActivityProfileBundle =
        when (level) {
            ActivityLevel.AlwaysActive ->
                ActivityProfileBundle(
                    energyState = EnergyState.ACTIVE,
                    bleTier = BleEnergyTier.Active,
                    wifiDirectIdleMs = WifiDirectBearer.IDLE_DOWNGRADE_MS_ACTIVE,
                    custodyPriority = CustodyRelayPriority.High,
                )
            ActivityLevel.Balanced ->
                ActivityProfileBundle(
                    energyState = EnergyState.INTERMITTENT,
                    bleTier = BleEnergyTier.Intermittent,
                    wifiDirectIdleMs = WifiDirectBearer.IDLE_DOWNGRADE_MS_BALANCED,
                    custodyPriority = CustodyRelayPriority.Medium,
                )
            ActivityLevel.BatterySaver ->
                ActivityProfileBundle(
                    energyState = EnergyState.PASSIVE,
                    bleTier = BleEnergyTier.Passive,
                    wifiDirectIdleMs = WifiDirectBearer.IDLE_DOWNGRADE_MS_SAVER,
                    custodyPriority = CustodyRelayPriority.Low,
                )
        }

    fun readLevel(prefs: SharedPreferences): ActivityLevel {
        val stored = prefs.getString(PREFS_ACTIVITY_LEVEL, null)
        if (stored != null) {
            return ActivityLevel.fromStorageKey(stored) ?: defaultLevel
        }
        return ActivityLevel.fromLegacyEnergyState(
            prefs.getString(PREFS_ENERGY_STATE, null),
        )
    }

    fun applyLevel(context: Context, level: ActivityLevel) {
        val bundle = bundle(level)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREFS_ACTIVITY_LEVEL, level.storageKey)
            .putString(PREFS_ENERGY_STATE, legacyEnergyStateKey(level))
            .apply()
        ActivityProfileRuntime.applyBundle(bundle)
        mycelium.setEnergyState(bundle.energyState)
    }

    fun loadRuntimeFromPrefs(context: Context) {
        ActivityProfileRuntime.applyBundle(bundle(readLevel(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))))
    }

    fun legacyEnergyStateKey(level: ActivityLevel): String =
        when (level) {
            ActivityLevel.AlwaysActive -> "Active"
            ActivityLevel.Balanced -> "Intermittent"
            ActivityLevel.BatterySaver -> "Passive"
        }

    private const val PREFS_NAME = "settings"
}
