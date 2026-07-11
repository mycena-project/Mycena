// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleScanDutyCycleTest {

    @Test
    fun activeTier_hasShortestPause() {
        val active = BleScanDutyCycle.tierIntervals(BleEnergyTier.Active)
        val passive = BleScanDutyCycle.tierIntervals(BleEnergyTier.Passive)
        assertTrue(active.pauseMs < passive.pauseMs)
        assertTrue(active.scanWindowMs >= passive.scanWindowMs)
    }

    @Test
    fun wifiAwarePreferred_doublesPause() {
        val base =
            BleScanDutyCycle.effectiveIntervals(
                BleScanDutyPolicy(BleEnergyTier.Active, wifiAwarePreferred = false, emptyCycles = 0),
            )
        val aware =
            BleScanDutyCycle.effectiveIntervals(
                BleScanDutyPolicy(BleEnergyTier.Active, wifiAwarePreferred = true, emptyCycles = 0),
            )
        assertEquals(base.scanWindowMs, aware.scanWindowMs)
        assertEquals(base.pauseMs * 2, aware.pauseMs)
    }

    @Test
    fun adaptiveBackoff_stretchesPauseAfterEmptyCycles() {
        val fresh =
            BleScanDutyCycle.effectiveIntervals(
                BleScanDutyPolicy(BleEnergyTier.Active, wifiAwarePreferred = false, emptyCycles = 2),
            )
        val backedOff =
            BleScanDutyCycle.effectiveIntervals(
                BleScanDutyPolicy(BleEnergyTier.Active, wifiAwarePreferred = false, emptyCycles = 5),
            )
        assertTrue(backedOff.pauseMs > fresh.pauseMs)
    }

    @Test
    fun adaptiveBackoff_independentOfTier() {
        val passiveBackedOff =
            BleScanDutyCycle.effectiveIntervals(
                BleScanDutyPolicy(BleEnergyTier.Passive, wifiAwarePreferred = false, emptyCycles = 6),
            )
        val passiveFresh =
            BleScanDutyCycle.effectiveIntervals(
                BleScanDutyPolicy(BleEnergyTier.Passive, wifiAwarePreferred = false, emptyCycles = 0),
            )
        assertTrue(passiveBackedOff.pauseMs > passiveFresh.pauseMs)
    }

    @Test
    fun peerDetected_resetsEmptyCycleCounter() {
        assertEquals(0, BleScanDutyCycle.onPeerDetected())
        assertEquals(1, BleScanDutyCycle.onEmptyCycle(0))
        assertEquals(4, BleScanDutyCycle.onEmptyCycle(3))
    }

    @Test
    fun pauseCappedAtMax() {
        val huge =
            BleScanDutyCycle.effectiveIntervals(
                BleScanDutyPolicy(BleEnergyTier.Active, wifiAwarePreferred = true, emptyCycles = 100),
            )
        assertEquals(BleScanDutyCycle.MAX_PAUSE_MS, huge.pauseMs)
    }

    @Test
    fun energyTier_fromPrefsLabel() {
        assertEquals(BleEnergyTier.Intermittent, BleEnergyTier.fromPrefsLabel("Intermittent"))
        assertEquals(BleEnergyTier.Passive, BleEnergyTier.fromPrefsLabel("Passive"))
        assertEquals(BleEnergyTier.Active, BleEnergyTier.fromPrefsLabel("Active"))
        assertEquals(BleEnergyTier.Active, BleEnergyTier.fromPrefsLabel(null))
    }
}
