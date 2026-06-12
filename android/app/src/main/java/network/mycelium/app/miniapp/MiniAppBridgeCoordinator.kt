package network.mycelium.app.miniapp

/**
 * Hooks from [network.mycelium.app.ui.MiniAppRunnerScreen] into the WebView JS bridge.
 */
object MiniAppBridgeCoordinator {
    var requestQrScan: ((onResult: (String?) -> Unit) -> Unit)? = null
    var pendingScanCallback: ((String?) -> Unit)? = null
    var onBulletinRefresh: (() -> Unit)? = null
    var onAppMessage: ((fromPeer: String, payload: String) -> Unit)? = null
}
