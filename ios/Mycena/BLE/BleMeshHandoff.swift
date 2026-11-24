// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import CoreBluetooth
import Foundation
import os.log

/// Attempts BLE GATT invite read → transport dial (F1). Chat contacts still require QR / connectInvite.
enum BleMeshHandoff {
    private static let log = Logger(subsystem: "io.github.mycena_project", category: "BleMeshHandoff")
    private static let lock = NSLock()
    private static var accountsHandedOff = Set<String>()
    private static var devicesInFlight = Set<UUID>()

    enum Result: Equatable {
        case connected
        case alreadyConnected
        case skippedDuplicate
        case failed(String)
    }

    static func resetSession() {
        lock.lock()
        accountsHandedOff.removeAll()
        devicesInFlight.removeAll()
        lock.unlock()
    }

    static func tryHandoff(
        bridge: MycenaNodeBridge,
        central: CBCentralManager,
        target: BleBootstrapScanner.HandoffTarget,
    ) async -> Result {
        let deviceId = target.peripheral.identifier
        lock.lock()
        guard devicesInFlight.insert(deviceId).inserted else {
            lock.unlock()
            return .skippedDuplicate
        }
        lock.unlock()

        defer {
            lock.lock()
            devicesInFlight.remove(deviceId)
            lock.unlock()
        }

        let invite: String
        do {
            invite = try await BleInviteReader.readInvite(central: central, peripheral: target.peripheral)
        } catch {
            return .failed("gatt_read_failed")
        }

        let accountId =
            PeerInvite.accountId(from: invite)
            ?? PeerInvite.peerId(from: invite)
            ?? deviceId.uuidString

        lock.lock()
        if accountsHandedOff.contains(accountId) {
            lock.unlock()
            return .skippedDuplicate
        }
        lock.unlock()

        let peerId = PeerInvite.peerId(from: invite)
        if let peerId, bridge.connectedPeers().contains(peerId) {
            lock.lock()
            accountsHandedOff.insert(accountId)
            lock.unlock()
            return .alreadyConnected
        }

        do {
            try bridge.connectTransportInvite(invite.trimmingCharacters(in: .whitespacesAndNewlines))
            lock.lock()
            accountsHandedOff.insert(accountId)
            lock.unlock()
            if let peerId {
                BleMeshLinkRegistry.register(peerId: peerId, peripheral: target.peripheral)
                bridge.registerBleLink(peerId: peerId, deviceId: deviceId.uuidString)
                _ = await BleMeshDataSession.open(
                    central: central,
                    peripheral: target.peripheral,
                    peerId: peerId,
                )
            }
            log.info("handoff ok account=\(accountId, privacy: .public) peer=\(peerId ?? "?", privacy: .public) rssi=\(target.rssi)")
            return .connected
        } catch {
            return .failed(error.localizedDescription)
        }
    }
}
