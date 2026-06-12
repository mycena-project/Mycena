// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

object MeshEvents {
    const val CHAT = "mycelium.CHAT_RECEIVED"
    const val MAIL = "mycelium.MAIL_RECEIVED"
    const val BULLETIN = "mycelium.BULLETIN_RECEIVED"
    const val PEER_UP = "mycelium.PEER_UP"
    const val PEER_DOWN = "mycelium.PEER_DOWN"
    const val APPSTORE = "mycelium.APPSTORE_RECEIVED"
    const val CONNECTIVITY = "mycelium.CONNECTIVITY"
    const val CONTACTS = "mycelium.CONTACTS_UPDATED"
    const val EXTRA_INTERNET = "internet"
    const val EXTRA_FROM_PEER = "from_peer"
    const val EXTRA_BODY = "body"
}
