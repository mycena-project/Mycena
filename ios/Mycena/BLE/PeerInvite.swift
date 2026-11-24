// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import Foundation

/// Parses QR / deep-link invites (must match `mycena_core::invite`).
enum PeerInvite {
    private static let prefixV1 = "mycena://invite/v1#"
    private static let prefixV3 = "mycena://invite/v3#"

    enum Target {
        case peerId(String)
        case multiaddr(String)
    }

    static func parse(_ raw: String) -> Target? {
        let input = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { return nil }
        if input.hasPrefix(prefixV3) {
            let rest = String(input.dropFirst(prefixV3.count))
            let parts = rest.split(separator: "|", maxSplits: 5, omittingEmptySubsequences: false)
            guard parts.count >= 2, !parts[1].isEmpty else { return nil }
            return .peerId(String(parts[1]).trimmingCharacters(in: .whitespaces))
        }
        if input.hasPrefix(prefixV1) {
            let id = String(input.dropFirst(prefixV1.count)).trimmingCharacters(in: .whitespaces)
            return id.isEmpty ? nil : .peerId(id)
        }
        if input.hasPrefix("/") { return .multiaddr(input) }
        if input.hasPrefix("12D3Koo"), input.count >= 40 { return .peerId(input) }
        return nil
    }

    static func peerId(from raw: String) -> String? {
        guard case let .peerId(id)? = parse(raw) else { return nil }
        return id
    }

    static func accountId(from raw: String) -> String? {
        let input = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard input.hasPrefix(prefixV3) else { return nil }
        let rest = String(input.dropFirst(prefixV3.count))
        let first = rest.split(separator: "|", maxSplits: 1).first.map(String.init)?
            .trimmingCharacters(in: .whitespaces)
        guard let first, !first.isEmpty else { return nil }
        return first
    }
}
