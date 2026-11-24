// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import CoreBluetooth
import Foundation

/// GATT layout for BLE → mesh handoff (ADR 002 F1/F2/F3). Must match Android [`BleMeshGatt.kt`].
enum BleMeshGatt {
    static let serviceUUID = CBUUID(string: "6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    static let inviteCharUUID = CBUUID(string: "6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    static let meshDataCharUUID = CBUUID(string: "6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    static let advertiseLocalName = "Mycena"
    /// Legacy manufacturer id (advert only; no peer id payload — privacy E0.3).
    static let manufacturerId: UInt16 = 0x4D59
}
