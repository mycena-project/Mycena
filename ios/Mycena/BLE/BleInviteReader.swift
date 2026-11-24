// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import CoreBluetooth
import Foundation
import os.log

/// Central role: connect over BLE, read the invite characteristic (F1 handoff).
enum BleInviteReader {
    private static let log = Logger(subsystem: "io.github.mycena_project", category: "BleInviteReader")
    private static let readTimeout: TimeInterval = 12

    enum ReadError: Error {
        case timeout
        case bluetoothUnavailable
        case invalidInvite
    }

    /// Reads a signed transport invite from a discovered peripheral.
    static func readInvite(
        central: CBCentralManager,
        peripheral: CBPeripheral,
    ) async throws -> String {
        try await withThrowingTaskGroup(of: String.self) { group in
            group.addTask {
                try await InviteReadSession(central: central, peripheral: peripheral).run()
            }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(readTimeout * 1_000_000_000))
                throw ReadError.timeout
            }
            guard let result = try await group.next() else {
                throw ReadError.timeout
            }
            group.cancelAll()
            return result
        }
    }

    private final class InviteReadSession: NSObject, CBPeripheralDelegate {
        private let central: CBCentralManager
        private let peripheral: CBPeripheral
        private var continuation: CheckedContinuation<String, Error>?
        private var connectObserver: NSObjectProtocol?

        init(central: CBCentralManager, peripheral: CBPeripheral) {
            self.central = central
            self.peripheral = peripheral
            super.init()
        }

        deinit {
            if let connectObserver {
                NotificationCenter.default.removeObserver(connectObserver)
            }
        }

        func run() async throws -> String {
            try await withCheckedThrowingContinuation { cont in
                continuation = cont
                peripheral.delegate = self
                if peripheral.state == .connected {
                    peripheral.discoverServices([BleMeshGatt.serviceUUID])
                    return
                }
                connectObserver = NotificationCenter.default.addObserver(
                    forName: .bleCentralDidConnect,
                    object: central,
                    queue: nil,
                ) { [weak self] note in
                    guard let self,
                          let connected = note.userInfo?["peripheral"] as? CBPeripheral,
                          connected.identifier == self.peripheral.identifier
                    else { return }
                    connected.discoverServices([BleMeshGatt.serviceUUID])
                }
                NotificationCenter.default.addObserver(
                    forName: .bleCentralDidFailToConnect,
                    object: central,
                    queue: nil,
                ) { [weak self] note in
                    guard let self,
                          let failed = note.userInfo?["peripheral"] as? CBPeripheral,
                          failed.identifier == self.peripheral.identifier
                    else { return }
                    self.finish(.failure(ReadError.bluetoothUnavailable))
                }
                central.connect(peripheral, options: nil)
            }
        }

        private func finish(_ result: Result<String, Error>) {
            guard let cont = continuation else { return }
            continuation = nil
            if peripheral.state == .connected || peripheral.state == .connecting {
                central.cancelPeripheralConnection(peripheral)
            }
            switch result {
            case let .success(value): cont.resume(returning: value)
            case let .failure(error): cont.resume(throwing: error)
            }
        }

        func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
            if error != nil {
                finish(.failure(ReadError.invalidInvite))
                return
            }
            guard let service = peripheral.services?.first(where: { $0.uuid == BleMeshGatt.serviceUUID }) else {
                finish(.failure(ReadError.invalidInvite))
                return
            }
            peripheral.discoverCharacteristics([BleMeshGatt.inviteCharUUID], for: service)
        }

        func peripheral(
            _ peripheral: CBPeripheral,
            didDiscoverCharacteristicsFor service: CBService,
            error: Error?,
        ) {
            if error != nil {
                finish(.failure(ReadError.invalidInvite))
                return
            }
            guard let char = service.characteristics?.first(where: { $0.uuid == BleMeshGatt.inviteCharUUID }) else {
                finish(.failure(ReadError.invalidInvite))
                return
            }
            peripheral.readValue(for: char)
        }

        func peripheral(
            _ peripheral: CBPeripheral,
            didUpdateValueFor characteristic: CBCharacteristic,
            error: Error?,
        ) {
            guard characteristic.uuid == BleMeshGatt.inviteCharUUID else { return }
            if error != nil {
                finish(.failure(ReadError.invalidInvite))
                return
            }
            guard let data = characteristic.value,
                  let text = String(data: data, encoding: .utf8)?
                      .trimmingCharacters(in: .whitespacesAndNewlines),
                  text.hasPrefix("mycena://")
            else {
                finish(.failure(ReadError.invalidInvite))
                return
            }
            BleInviteReader.log.info(
                "read invite from \(peripheral.identifier.uuidString) (\(text.count) chars)",
            )
            finish(.success(text))
        }

        func peripheral(_ peripheral: CBPeripheral, didModifyServices invalidatedServices: [CBService]) {
            finish(.failure(ReadError.invalidInvite))
        }
    }
}

extension Notification.Name {
    static let bleCentralDidConnect = Notification.Name("io.github.mycena_project.ble.centralDidConnect")
    static let bleCentralDidFailToConnect = Notification.Name("io.github.mycena_project.ble.centralDidFailConnect")
    static let bleCentralDidDisconnect = Notification.Name("io.github.mycena_project.ble.centralDidDisconnect")
}
