// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.content.Context
import android.os.Build
import android.os.PowerManager
import network.mycelium.bindings.ThermalState
import network.mycelium.bindings.mycelium

/**
 * Maps Android [PowerManager] thermal status to mesh bearer throttling (Wi‑Fi Direct caps).
 */
object ThermalMonitor {
    fun start(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        applyThermal(pm.currentThermalStatus)
        pm.addThermalStatusListener(context.mainExecutor) { status ->
            applyThermal(status)
        }
    }

    private fun applyThermal(status: Int) {
        mycelium.setThermalState(mapThermal(status))
    }

    private fun mapThermal(status: Int): ThermalState =
        when (status) {
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN,
            -> ThermalState.CRITICAL
            PowerManager.THERMAL_STATUS_MODERATE,
            PowerManager.THERMAL_STATUS_LIGHT,
            -> ThermalState.WARM
            else -> ThermalState.NORMAL
        }
}
