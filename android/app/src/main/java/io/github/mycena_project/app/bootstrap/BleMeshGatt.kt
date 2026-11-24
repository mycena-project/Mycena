// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import java.util.UUID

/** GATT layout for BLE → mesh handoff (ADR 002 F1). */
object BleMeshGatt {
    /** Primary service — also advertised for passive scan filter. */
    val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    /** Read-only signed invite v3 URI (`mycena://invite/v3#…`). */
    val INVITE_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    /** Write-only mesh frames (chunked MYBL wire payloads, F2). */
    val MESH_DATA_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    /** Legacy manufacturer id (advert only; no peer id payload — privacy E0.3). */
    const val MANUFACTURER_ID = 0x4D59
}
