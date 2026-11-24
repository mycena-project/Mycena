// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.discovery

import io.github.mycena_project.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeersDiscoveryUiTest {

    private val base =
        MeshDiscoverySnapshot(
            bleBootstrapEnabled = true,
            blePermissionsGranted = true,
            scanActive = true,
            scanEpochStartedAtMs = 0L,
            lastScanRestartAtMs = 0L,
            lastNearbySeenAtMs = null,
            nearbyDeviceCount = 0,
            connectedPeerCount = 0,
            connectingFromInvite = false,
            nowMs = 0L,
        )

    @Test
    fun earlyScan_showsSearchingWithProgress() {
        val ui =
            PeersDiscoveryUi.reduce(
                base.copy(
                    scanEpochStartedAtMs = 0L,
                    nowMs = 10_000L,
                ),
            )
        assertEquals(PeersDiscoveryPhase.Searching, ui.phase)
        assertEquals(R.string.peers_discovery_searching_title, ui.titleResId)
        assertTrue(ui.showProgress)
    }

    @Test
    fun timeoutWithoutPeers_showsCalmNobodyInRangeExplanation() {
        val ui =
            PeersDiscoveryUi.reduce(
                base.copy(
                    scanEpochStartedAtMs = 0L,
                    nowMs = PeersDiscoveryUi.SEARCH_GRACE_MS + 5_000L,
                ),
            )
        assertEquals(PeersDiscoveryPhase.NobodyInRange, ui.phase)
        assertEquals(R.string.peers_discovery_nobody_title, ui.titleResId)
        assertEquals(R.string.peers_discovery_nobody_body, ui.bodyResId)
        assertFalse(ui.showProgress)
    }

    @Test
    fun timeoutWithoutPeers_isNotSameAsStillSearching() {
        val searching =
            PeersDiscoveryUi.reduce(base.copy(nowMs = 5_000L))
        val nobody =
            PeersDiscoveryUi.reduce(
                base.copy(nowMs = PeersDiscoveryUi.SEARCH_GRACE_MS + 1_000L),
            )
        assertEquals(PeersDiscoveryPhase.Searching, searching.phase)
        assertEquals(PeersDiscoveryPhase.NobodyInRange, nobody.phase)
        assertTrue(searching.titleResId != nobody.titleResId)
    }

    @Test
    fun inviteConnect_showsConnectingState() {
        val ui =
            PeersDiscoveryUi.reduce(
                base.copy(connectingFromInvite = true, nowMs = 1_000L),
            )
        assertEquals(PeersDiscoveryPhase.Connecting, ui.phase)
        assertEquals(R.string.peers_discovery_connecting_title, ui.titleResId)
        assertTrue(ui.showProgress)
    }

    @Test
    fun shouldRestartBleScan_afterStalePeriodWithoutProgress() {
        val snap =
            base.copy(
                scanEpochStartedAtMs = 0L,
                lastScanRestartAtMs = 0L,
                nowMs = PeersDiscoveryUi.SCAN_RESTART_STALE_MS + 1_000L,
            )
        assertTrue(PeersDiscoveryUi.shouldRestartBleScan(snap))
    }

    @Test
    fun shouldNotRestartBleScan_whenConnected() {
        val snap =
            base.copy(
                connectedPeerCount = 1,
                nowMs = PeersDiscoveryUi.SCAN_RESTART_STALE_MS + 1_000L,
            )
        assertFalse(PeersDiscoveryUi.shouldRestartBleScan(snap))
    }
}
