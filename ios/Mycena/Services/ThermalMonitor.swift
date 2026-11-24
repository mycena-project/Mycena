// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import Foundation

/// Silent thermal throttling layered on user activity settings (mirrors Android).
enum ThermalEnforcementLevel: Equatable {
    case normal
    case warm
    case critical
}

enum ThermalEnforcement {
    private(set) static var level: ThermalEnforcementLevel = .normal

    static var onEnterCritical: (() -> Void)?
    static var onLevelChanged: ((ThermalEnforcementLevel) -> Void)?

    static func update(level newLevel: ThermalEnforcementLevel) {
        let previous = level
        level = newLevel
        if newLevel == .critical, previous != .critical {
            onEnterCritical?()
        }
        if newLevel != previous {
            onLevelChanged?(newLevel)
        }
    }
}

#if MYCENA_RUST_FFI

private func mapRustThermal(_ level: ThermalEnforcementLevel) -> ThermalState {
    switch level {
    case .normal: return .normal
    case .warm: return .warm
    case .critical: return .critical
    }
}

#endif

/// Observes `ProcessInfo.thermalState` and applies silent throttling (no UI).
@MainActor
final class ThermalMonitor {
    static let shared = ThermalMonitor()

    private var observer: NSObjectProtocol?

    func start(onLevelChanged: @escaping (ThermalEnforcementLevel) -> Void) {
        ThermalEnforcement.onLevelChanged = onLevelChanged
        apply(ProcessInfo.processInfo.thermalState)
        observer = NotificationCenter.default.addObserver(
            forName: ProcessInfo.thermalStateDidChangeNotification,
            object: nil,
            queue: .main,
        ) { [weak self] _ in
            Task { @MainActor in
                self?.apply(ProcessInfo.processInfo.thermalState)
            }
        }
    }

    func stop() {
        if let observer {
            NotificationCenter.default.removeObserver(observer)
        }
        observer = nil
        ThermalEnforcement.onEnterCritical = nil
        ThermalEnforcement.onLevelChanged = nil
    }

    private func apply(_ state: ProcessInfo.ThermalState) {
        let level = mapLevel(state)
        ThermalEnforcement.update(level: level)
        #if MYCENA_RUST_FFI
        setThermalState(state: mapRustThermal(level))
        #endif
    }

    private func mapLevel(_ state: ProcessInfo.ThermalState) -> ThermalEnforcementLevel {
        switch state {
        case .critical, .serious:
            return .critical
        case .fair:
            return .warm
        case .nominal:
            return .normal
        @unknown default:
            return .normal
        }
    }
}
