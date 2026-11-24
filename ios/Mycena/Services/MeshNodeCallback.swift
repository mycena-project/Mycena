// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import Foundation
import os.log

#if MYCENA_RUST_FFI

/// Forwards Rust `NodeEventCallback` into UI updates and `MeshEvents` notifications.
final class MeshNodeCallback: NodeEventCallback {
    private let log = Logger(subsystem: "io.github.mycena_project", category: "NodeCallback")
    private weak var host: MeshHost?

    init(host: MeshHost) {
        self.host = host
    }

    func onPeerDiscovered(peerId: String) {
        log.debug("peer up \(peerId, privacy: .public)")
        MeshEvents.postPeerUp(peerId)
        Task { @MainActor in host?.refreshPeerState() }
    }

    func onPeerLost(peerId: String) {
        log.debug("peer down \(peerId, privacy: .public)")
        MeshEvents.postPeerDown(peerId)
        Task { @MainActor in host?.refreshPeerState() }
    }

    func onChatReceived(message: ChatMessage) {
        MeshEvents.postChat(fromPeer: message.fromPeer, body: message.body)
    }

    func onMailReceived(message: MailMessage) {
        NotificationCenter.default.post(name: MeshEvents.mail, object: nil)
    }

    func onBulletinReceived(post: BulletinPost) {
        NotificationCenter.default.post(name: MeshEvents.bulletin, object: nil)
    }

    func onSosReceived(post: SosPost) {
        NotificationCenter.default.post(name: MeshEvents.sos, object: nil)
    }

    func onAppStoreListing(listing: AppStoreListing) {
        NotificationCenter.default.post(name: MeshEvents.appStore, object: nil)
    }

    func onConnectivityChanged(mode: ConnectivityMode) {
        let internet = mode == .internet
        MeshEvents.postConnectivity(internet: internet)
        Task { @MainActor in host?.updateConnectivity(mode) }
    }

    func onContactRequest(peerId: String, displayName: String) {
        NotificationCenter.default.post(name: MeshEvents.contacts, object: nil)
    }
}

#endif
