// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import Foundation

/// NotificationCenter events from the Rust node (mirrors Android `MeshEvents` broadcasts).
enum MeshEvents {
    static let peerUp = Notification.Name("network.mycelium.peerUp")
    static let peerDown = Notification.Name("network.mycelium.peerDown")
    static let chat = Notification.Name("network.mycelium.chat")
    static let mail = Notification.Name("network.mycelium.mail")
    static let bulletin = Notification.Name("network.mycelium.bulletin")
    static let sos = Notification.Name("network.mycelium.sos")
    static let appStore = Notification.Name("network.mycelium.appStore")
    static let contacts = Notification.Name("network.mycelium.contacts")
    static let connectivity = Notification.Name("network.mycelium.connectivity")

    static let peerIdKey = "peer_id"
    static let chatFromKey = "from_peer"
    static let chatBodyKey = "body"
    static let internetKey = "internet"

    static func postPeerUp(_ peerId: String) {
        NotificationCenter.default.post(name: peerUp, object: nil, userInfo: [peerIdKey: peerId])
    }

    static func postPeerDown(_ peerId: String) {
        NotificationCenter.default.post(name: peerDown, object: nil, userInfo: [peerIdKey: peerId])
    }

    static func postChat(fromPeer: String, body: String) {
        NotificationCenter.default.post(
            name: chat,
            object: nil,
            userInfo: [chatFromKey: fromPeer, chatBodyKey: body],
        )
    }

    static func postConnectivity(internet: Bool) {
        NotificationCenter.default.post(
            name: connectivity,
            object: nil,
            userInfo: [internetKey: internet],
        )
    }
}
