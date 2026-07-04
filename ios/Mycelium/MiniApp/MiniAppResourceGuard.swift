// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import Foundation
import UIKit

/// Memory-pressure hooks for a future WKWebView mini-app host (iOS has no mini-app runtime yet).
///
/// Wire into `MiniAppHostViewController` when WKWebView lands; constants mirror
/// `mycelium-miniapp/src/bridge_limits.rs`.
enum MiniAppResourceGuard {
    static let bridgeCallTimeoutMs: UInt64 = 5_000
    static let bridgeChainWindowMs: UInt64 = 30_000
    static let bridgeChainBudgetMs: UInt64 = 30_000

    /// Observe `UIApplication.didReceiveMemoryWarningNotification` and tear down the guest WebView.
    static func registerMemoryPressureHandler(
        webViewTeardown: @escaping () -> Void
    ) -> NSObjectProtocol {
        NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { _ in
            webViewTeardown()
        }
    }

    static func unregisterMemoryPressureHandler(_ token: NSObjectProtocol) {
        NotificationCenter.default.removeObserver(token)
    }
}
