// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import CoreBluetooth
import Foundation

/// Maps libp2p peer id ↔ BLE peripheral identifier after F1 handoff.
enum BleMeshLinkRegistry {
    private static let lock = NSLock()
    private static var peerToDevice: [String: UUID] = [:]
    private static var deviceToPeer: [UUID: String] = [:]

    static func register(peerId: String, peripheral: CBPeripheral) {
        let id = peripheral.identifier
        lock.lock()
        peerToDevice[peerId] = id
        deviceToPeer[id] = peerId
        lock.unlock()
    }

    static func register(peerId: String, deviceId: UUID) {
        lock.lock()
        peerToDevice[peerId] = deviceId
        deviceToPeer[deviceId] = peerId
        lock.unlock()
    }

    static func peer(forDevice deviceId: UUID) -> String? {
        lock.lock()
        defer { lock.unlock() }
        return deviceToPeer[deviceId]
    }

    static func deviceId(forPeer peerId: String) -> UUID? {
        lock.lock()
        defer { lock.unlock() }
        return peerToDevice[peerId]
    }

    static func allPeers() -> [String] {
        lock.lock()
        defer { lock.unlock() }
        return Array(peerToDevice.keys)
    }

    static func clear() {
        lock.lock()
        peerToDevice.removeAll()
        deviceToPeer.removeAll()
        lock.unlock()
    }
}
