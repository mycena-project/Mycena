// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import Foundation
import Security
import os.log

/// 32-byte master key for Rust at-rest encryption (mirrors Android `StorageKeyHelper`).
enum StorageKeyHelper {
    private static let log = Logger(subsystem: "io.github.mycena_project", category: "StorageKey")
    private static let service = "io.github.mycena_project.app.storage-key"
    private static let account = "storage_key_hex"

    static func getOrCreateStorageKeyHex() -> String {
        if let existing = readKey() {
            return existing
        }
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        precondition(status == errSecSuccess, "SecRandomCopyBytes failed")
        let hex = bytes.map { String(format: "%02x", $0) }.joined()
        saveKey(hex)
        log.info("generated new at-rest storage key")
        return hex
    }

    static func hasExistingKey() -> Bool {
        readKey() != nil
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }

    private static func readKey() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func saveKey(_ hex: String) {
        let data = Data(hex.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }
}
