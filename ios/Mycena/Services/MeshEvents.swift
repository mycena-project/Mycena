// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import Foundation

/// NotificationCenter events from the Rust node (mirrors Android `MeshEvents` broadcasts).
enum MeshEvents {
    static let peerUp = Notification.Name("io.github.mycena_project.peerUp")
    static let peerDown = Notification.Name("io.github.mycena_project.peerDown")
    static let chat = Notification.Name("io.github.mycena_project.chat")
    static let mail = Notification.Name("io.github.mycena_project.mail")
    static let bulletin = Notification.Name("io.github.mycena_project.bulletin")
    static let sos = Notification.Name("io.github.mycena_project.sos")
    static let appStore = Notification.Name("io.github.mycena_project.appStore")
    static let contacts = Notification.Name("io.github.mycena_project.contacts")
    static let connectivity = Notification.Name("io.github.mycena_project.connectivity")

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
