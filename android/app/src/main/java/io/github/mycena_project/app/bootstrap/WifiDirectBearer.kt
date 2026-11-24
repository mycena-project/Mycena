// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

/** Wi‑Fi Direct bearer socket port (ADR 007). Must match iOS scaffold when added. */
object WifiDirectBearer {
    const val SOCKET_PORT = 7765
    const val SERVICE_NAME = "MycenaBearer"
    /** Idle before teardown — [ActivityProfile] Always active. */
    const val IDLE_DOWNGRADE_MS_ACTIVE = 120_000L
    /** Idle before teardown — balanced (default). */
    const val IDLE_DOWNGRADE_MS_BALANCED = 30_000L
    /** Idle before teardown — battery saver. */
    const val IDLE_DOWNGRADE_MS_SAVER = 10_000L
    /** @deprecated Use [ActivityProfileRuntime.wifiDirectIdleDowngradeMs]. */
    const val IDLE_DOWNGRADE_MS = IDLE_DOWNGRADE_MS_BALANCED
}
