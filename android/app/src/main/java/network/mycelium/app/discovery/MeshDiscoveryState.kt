// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** App-scoped BLE / bearer discovery signals for the Connect tab. Updated from [network.mycelium.app.MeshService]. */
object MeshDiscoveryState {
    private val _snapshot = MutableStateFlow(defaultSnapshot())
    val snapshot: StateFlow<MeshDiscoverySnapshot> = _snapshot.asStateFlow()

    fun reset() {
        _snapshot.value = defaultSnapshot()
    }

    fun onBleBootstrapStarted(nowMs: Long = System.currentTimeMillis()) {
        _snapshot.update {
            it.copy(
                bleBootstrapEnabled = true,
                scanActive = true,
                scanEpochStartedAtMs = nowMs,
                lastScanRestartAtMs = nowMs,
                nowMs = nowMs,
            )
        }
    }

    fun onBleBootstrapStopped(nowMs: Long = System.currentTimeMillis()) {
        _snapshot.update {
            it.copy(
                bleBootstrapEnabled = false,
                scanActive = false,
                nearbyDeviceCount = 0,
                lastNearbySeenAtMs = null,
                nowMs = nowMs,
            )
        }
    }

    fun setBlePermissionsGranted(granted: Boolean) {
        _snapshot.update { it.copy(blePermissionsGranted = granted) }
    }

    fun setBleBootstrapEnabled(enabled: Boolean) {
        _snapshot.update { it.copy(bleBootstrapEnabled = enabled) }
    }

    fun onNearbyDevices(count: Int, nowMs: Long = System.currentTimeMillis()) {
        _snapshot.update {
            it.copy(
                nearbyDeviceCount = count,
                lastNearbySeenAtMs = if (count > 0) nowMs else it.lastNearbySeenAtMs,
                nowMs = nowMs,
            )
        }
    }

    fun onScanWindowActive(nowMs: Long = System.currentTimeMillis()) {
        _snapshot.update {
            it.copy(
                scanActive = true,
                lastScanRestartAtMs = nowMs,
                nowMs = nowMs,
            )
        }
    }

    fun onScanPaused(nowMs: Long = System.currentTimeMillis()) {
        _snapshot.update {
            it.copy(
                scanActive = false,
                nowMs = nowMs,
            )
        }
    }

    fun onScanRestarted(nowMs: Long = System.currentTimeMillis()) {
        _snapshot.update {
            it.copy(
                lastScanRestartAtMs = nowMs,
                nowMs = nowMs,
            )
        }
    }

    fun setConnectedPeerCount(count: Int) {
        _snapshot.update { it.copy(connectedPeerCount = count, nowMs = System.currentTimeMillis()) }
    }

    fun setConnectingFromInvite(connecting: Boolean) {
        _snapshot.update { it.copy(connectingFromInvite = connecting, nowMs = System.currentTimeMillis()) }
    }

    fun tick(nowMs: Long = System.currentTimeMillis()) {
        _snapshot.update { it.copy(nowMs = nowMs) }
    }

    private fun defaultSnapshot() =
        MeshDiscoverySnapshot(
            bleBootstrapEnabled = false,
            blePermissionsGranted = false,
            scanActive = false,
            scanEpochStartedAtMs = 0L,
            lastScanRestartAtMs = 0L,
            lastNearbySeenAtMs = null,
            nearbyDeviceCount = 0,
            connectedPeerCount = 0,
            connectingFromInvite = false,
            nowMs = System.currentTimeMillis(),
        )
}
