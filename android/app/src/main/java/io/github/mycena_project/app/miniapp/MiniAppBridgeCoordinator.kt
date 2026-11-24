// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.miniapp

/**
 * Hooks from [io.github.mycena_project.app.ui.MiniAppRunnerScreen] into the WebView JS bridge.
 */
object MiniAppBridgeCoordinator {
    var requestQrScan: ((onResult: (String?) -> Unit) -> Unit)? = null
    var pendingScanCallback: ((String?) -> Unit)? = null
    var onBulletinRefresh: (() -> Unit)? = null
    var onAppMessage: ((fromPeer: String, payload: String) -> Unit)? = null
}
