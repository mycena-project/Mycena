// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.discovery

import io.github.mycena_project.app.R

/** User-visible discovery phase on the Connect tab (derived from mesh + BLE signals). */
enum class PeersDiscoveryPhase {
    /** BLE bearer off or permissions missing — not an error. */
    Idle,
    /** Active scan / dial in the first seconds after start or restart. */
    Searching,
    /** Scan ran for a while with no nearby devices and no connections. */
    NobodyInRange,
    /** BLE devices seen recently but not yet connected as libp2p peers. */
    DevicesNearby,
    /** At least one connected peer — list is shown below. */
    Connected,
    /** Post-QR / invite dial in progress. */
    Connecting,
}

data class MeshDiscoverySnapshot(
    val bleBootstrapEnabled: Boolean,
    val blePermissionsGranted: Boolean,
    val scanActive: Boolean,
    val scanEpochStartedAtMs: Long,
    val lastScanRestartAtMs: Long,
    val lastNearbySeenAtMs: Long?,
    val nearbyDeviceCount: Int,
    val connectedPeerCount: Int,
    val connectingFromInvite: Boolean,
    val nowMs: Long,
)

data class PeersDiscoveryPresentation(
    val phase: PeersDiscoveryPhase,
    /** String resource id for title — 0 means no banner. */
    val titleResId: Int,
    val bodyResId: Int,
    val showProgress: Boolean,
)

object PeersDiscoveryUi {
    /** Grace period before "nobody in range" (Bridgefy-style silent hang avoidance). */
    const val SEARCH_GRACE_MS: Long = 25_000L

    /** How long a BLE sighting counts as "devices nearby". */
    const val NEARBY_RECENT_MS: Long = 90_000L

    /** Restart BLE scan when no progress for this long (invisible to user). */
    const val SCAN_RESTART_STALE_MS: Long = 30_000L

    fun reduce(snapshot: MeshDiscoverySnapshot): PeersDiscoveryPresentation {
        if (snapshot.connectingFromInvite) {
            return presentation(
                PeersDiscoveryPhase.Connecting,
                R.string.peers_discovery_connecting_title,
                R.string.peers_discovery_connecting_body,
                showProgress = true,
            )
        }
        if (snapshot.connectedPeerCount > 0) {
            return presentation(
                PeersDiscoveryPhase.Connected,
                titleResId = 0,
                bodyResId = 0,
                showProgress = false,
            )
        }
        if (!snapshot.bleBootstrapEnabled || !snapshot.blePermissionsGranted) {
            return presentation(
                PeersDiscoveryPhase.Idle,
                R.string.peers_discovery_idle_title,
                R.string.peers_discovery_idle_body,
                showProgress = false,
            )
        }
        if (!snapshot.scanActive) {
            return presentation(
                PeersDiscoveryPhase.Searching,
                R.string.peers_discovery_searching_title,
                R.string.peers_discovery_searching_body,
                showProgress = true,
            )
        }

        val searchAnchorMs =
            maxOf(snapshot.scanEpochStartedAtMs, snapshot.lastScanRestartAtMs)
        val searchingElapsed = snapshot.nowMs - searchAnchorMs

        val recentNearby =
            snapshot.lastNearbySeenAtMs?.let { snapshot.nowMs - it <= NEARBY_RECENT_MS } == true

        if (recentNearby && snapshot.nearbyDeviceCount > 0) {
            return presentation(
                PeersDiscoveryPhase.DevicesNearby,
                R.string.peers_discovery_nearby_found_title,
                R.string.peers_discovery_nearby_found_body,
                showProgress = true,
            )
        }

        if (searchingElapsed < SEARCH_GRACE_MS) {
            return presentation(
                PeersDiscoveryPhase.Searching,
                R.string.peers_discovery_searching_title,
                R.string.peers_discovery_searching_body,
                showProgress = true,
            )
        }

        return presentation(
            PeersDiscoveryPhase.NobodyInRange,
            R.string.peers_discovery_nobody_title,
            R.string.peers_discovery_nobody_body,
            showProgress = false,
        )
    }

    fun shouldRestartBleScan(snapshot: MeshDiscoverySnapshot): Boolean {
        if (!snapshot.scanActive || !snapshot.bleBootstrapEnabled) return false
        if (snapshot.connectedPeerCount > 0) return false
        val anchor = maxOf(snapshot.scanEpochStartedAtMs, snapshot.lastScanRestartAtMs)
        val lastProgress =
            listOfNotNull(
                anchor,
                snapshot.lastNearbySeenAtMs,
            ).maxOrNull() ?: anchor
        return snapshot.nowMs - lastProgress >= SCAN_RESTART_STALE_MS
    }

    private fun presentation(
        phase: PeersDiscoveryPhase,
        titleResId: Int,
        bodyResId: Int,
        showProgress: Boolean,
    ) = PeersDiscoveryPresentation(
        phase = phase,
        titleResId = titleResId,
        bodyResId = bodyResId,
        showProgress = showProgress,
    )
}
