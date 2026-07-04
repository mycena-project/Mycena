// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

/** Wi‑Fi Direct bearer socket port (ADR 007). Must match iOS scaffold when added. */
object WifiDirectBearer {
    const val SOCKET_PORT = 7765
    const val SERVICE_NAME = "MyceliumBearer"
    /** Idle time before tearing down P2P group and returning to BLE-only. */
    const val IDLE_DOWNGRADE_MS = 30_000L
}
