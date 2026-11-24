// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app

object MeshEvents {
    const val CHAT = "mycena.CHAT_RECEIVED"
    const val MAIL = "mycena.MAIL_RECEIVED"
    const val BULLETIN = "mycena.BULLETIN_RECEIVED"
    const val SOS = "mycena.SOS_RECEIVED"
    const val PEER_UP = "mycena.PEER_UP"
    const val PEER_DOWN = "mycena.PEER_DOWN"
    const val APPSTORE = "mycena.APPSTORE_RECEIVED"
    const val CONNECTIVITY = "mycena.CONNECTIVITY"
    const val CONTACTS = "mycena.CONTACTS_UPDATED"
    const val EXTRA_INTERNET = "internet"
    const val EXTRA_FROM_PEER = "from_peer"
    const val EXTRA_BODY = "body"
}
