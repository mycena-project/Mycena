// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.thermal

import io.github.mycena_project.app.bootstrap.BleEnergyTier
import io.github.mycena_project.app.bootstrap.BleScanDutyIntervals
import io.github.mycena_project.bindings.ThermalState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalEnforcementTest {

    @After
    fun reset() {
        ThermalEnforcement.Runtime.update(ThermalState.NORMAL)
    }

    @Test
    fun critical_forcesPassiveBleTier_regardlessOfUserActive() {
        ThermalEnforcement.Runtime.update(ThermalState.CRITICAL)
        assertEquals(
            BleEnergyTier.Passive,
            ThermalEnforcement.effectiveBleTier(BleEnergyTier.Active),
        )
    }

    @Test
    fun warm_downgradesActiveToIntermittent() {
        ThermalEnforcement.Runtime.update(ThermalState.WARM)
        assertEquals(
            BleEnergyTier.Intermittent,
            ThermalEnforcement.effectiveBleTier(BleEnergyTier.Active),
        )
        assertEquals(
            BleEnergyTier.Passive,
            ThermalEnforcement.effectiveBleTier(BleEnergyTier.Passive),
        )
    }

    @Test
    fun critical_stretchesScanPauseBeyondActivityProfile() {
        ThermalEnforcement.Runtime.update(ThermalState.CRITICAL)
        val tightened =
            ThermalEnforcement.applyToIntervals(
                BleScanDutyIntervals(scanWindowMs = 10_000L, pauseMs = 5_000L),
            )
        assertTrue(tightened.pauseMs > 5_000L)
        assertEquals(3_000L, tightened.scanWindowMs)
    }

    @Test
    fun critical_capsWifiDirectIdle() {
        ThermalEnforcement.Runtime.update(ThermalState.CRITICAL)
        assertEquals(5_000L, ThermalEnforcement.effectiveWifiDirectIdleMs(120_000L))
    }

    @Test
    fun onEnterCritical_firesOnce() {
        var count = 0
        ThermalEnforcement.Runtime.onEnterCritical = { count++ }
        ThermalEnforcement.Runtime.update(ThermalState.WARM)
        assertEquals(0, count)
        ThermalEnforcement.Runtime.update(ThermalState.CRITICAL)
        assertEquals(1, count)
        ThermalEnforcement.Runtime.update(ThermalState.CRITICAL)
        assertEquals(1, count)
        ThermalEnforcement.Runtime.onEnterCritical = null
    }
}
