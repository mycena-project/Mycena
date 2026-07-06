// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import Foundation

/// UserDefaults-backed host settings (subset of Android `MeshService` prefs).
enum MeshSettings {
    private static let defaults = UserDefaults.standard

    static var displayName: String {
        get {
            defaults.string(forKey: Keys.displayName)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .nonEmpty ?? "ios-user"
        }
        set { defaults.set(newValue, forKey: Keys.displayName) }
    }

    static var internetEnabled: Bool {
        get { defaults.object(forKey: Keys.internetEnabled) as? Bool ?? true }
        set { defaults.set(newValue, forKey: Keys.internetEnabled) }
    }

    static var rendezvousEnabled: Bool {
        get {
            let stored = defaults.object(forKey: Keys.rendezvousEnabled) as? Bool
            return stored ?? internetEnabled
        }
        set { defaults.set(newValue, forKey: Keys.rendezvousEnabled) }
    }

    static var crowdModeEnabled: Bool {
        get { defaults.bool(forKey: Keys.crowdMode) }
        set { defaults.set(newValue, forKey: Keys.crowdMode) }
    }

    static var bootstrapPeers: [String] {
        get {
            defaults.string(forKey: Keys.bootstrapPeers)?
                .split(whereSeparator: \.isNewline)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty } ?? []
        }
        set { defaults.set(newValue.joined(separator: "\n"), forKey: Keys.bootstrapPeers) }
    }

    static var bulletinSubscriptions: [String] {
        get { defaults.stringArray(forKey: Keys.bulletinSubscriptions) ?? [] }
        set { defaults.set(newValue, forKey: Keys.bulletinSubscriptions) }
    }

    private enum Keys {
        static let displayName = "display_name"
        static let internetEnabled = "internet_enabled"
        static let rendezvousEnabled = "rendezvous_enabled"
        static let crowdMode = "crowd_mode"
        static let bootstrapPeers = "bootstrap_peers"
        static let bulletinSubscriptions = "bulletin_subscriptions"
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
