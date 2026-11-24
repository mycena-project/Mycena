// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.settings

import io.github.mycena_project.app.bootstrap.BleEnergyTier
import io.github.mycena_project.app.bootstrap.WifiDirectBearer
import io.github.mycena_project.bindings.EnergyState
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityProfileTest {

    @Test
    fun defaultLevel_isBalanced() {
        assertEquals(ActivityLevel.Balanced, ActivityProfiles.defaultLevel)
    }

    @Test
    fun balancedBundle_mapsToIntermittentEnergyAndMediumCustody() {
        val bundle = ActivityProfiles.bundle(ActivityLevel.Balanced)
        assertEquals(EnergyState.INTERMITTENT, bundle.energyState)
        assertEquals(BleEnergyTier.Intermittent, bundle.bleTier)
        assertEquals(WifiDirectBearer.IDLE_DOWNGRADE_MS_BALANCED, bundle.wifiDirectIdleMs)
        assertEquals(CustodyRelayPriority.Medium, bundle.custodyPriority)
    }

    @Test
    fun alwaysActiveBundle_isMostAggressive() {
        val active = ActivityProfiles.bundle(ActivityLevel.AlwaysActive)
        val saver = ActivityProfiles.bundle(ActivityLevel.BatterySaver)
        assert(active.wifiDirectIdleMs > saver.wifiDirectIdleMs)
        assertEquals(EnergyState.ACTIVE, active.energyState)
        assertEquals(CustodyRelayPriority.High, active.custodyPriority)
        assertEquals(CustodyRelayPriority.Low, saver.custodyPriority)
    }

    @Test
    fun legacyEnergyState_migration() {
        assertEquals(
            ActivityLevel.Balanced,
            ActivityLevel.fromLegacyEnergyState("Intermittent"),
        )
        assertEquals(
            ActivityLevel.AlwaysActive,
            ActivityLevel.fromLegacyEnergyState("Active"),
        )
        assertEquals(
            ActivityLevel.BatterySaver,
            ActivityLevel.fromLegacyEnergyState("Passive"),
        )
        assertEquals(
            ActivityLevel.Balanced,
            ActivityLevel.fromLegacyEnergyState(null),
        )
    }

    @Test
    fun legacyEnergyStateKey_roundTrip() {
        ActivityLevel.entries.forEach { level ->
            assertEquals(level, ActivityLevel.fromLegacyEnergyState(
                ActivityProfiles.legacyEnergyStateKey(level),
            ))
        }
    }
}
