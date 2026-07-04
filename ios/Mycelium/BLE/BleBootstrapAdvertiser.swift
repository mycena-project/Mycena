// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import Foundation
import os.log

/// Connectable BLE advertisement wrapper around [`BleInviteGattServer`] (ADR 002 F1).
final class BleBootstrapAdvertiser {
    private let log = Logger(subsystem: "network.mycelium", category: "BleBootstrap")
    private let gattServer: BleInviteGattServer
    private(set) var isActive = false

    init(gattServer: BleInviteGattServer) {
        self.gattServer = gattServer
    }

    func startAdvertising() {
        gattServer.start()
        isActive = true
        log.info("peripheral bootstrap requested")
    }

    func refreshInvite() {
        gattServer.refreshInviteValue()
    }

    func stopAdvertising() {
        gattServer.stop()
        isActive = false
        log.info("BLE advertising stopped")
    }
}
