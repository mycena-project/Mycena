// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.bindings

typealias NodeConfig = uniffi.mycelium.NodeConfig
typealias ChatMessage = uniffi.mycelium.ChatMessage
typealias BulletinPost = uniffi.mycelium.BulletinPost
typealias SosPost = uniffi.mycelium.SosPost
typealias MailMessage = uniffi.mycelium.MailMessage
typealias NodeMetrics = uniffi.mycelium.NodeMetrics
typealias WalletBalance = uniffi.mycelium.WalletBalance
typealias TxInfo = uniffi.mycelium.TxInfo
typealias EnergyState = uniffi.mycelium.EnergyState
typealias NodeEventCallback = uniffi.mycelium.NodeEventCallback
typealias ConnectivityMode = uniffi.mycelium.ConnectivityMode
typealias HotWalletConfig = uniffi.mycelium.HotWalletConfig
typealias HotWalletStatus = uniffi.mycelium.HotWalletStatus
typealias PaymentRequestData = uniffi.mycelium.PaymentRequestData
typealias GroupInfo = uniffi.mycelium.GroupInfo
typealias ContactInfo = uniffi.mycelium.ContactInfo
typealias ContactStatus = uniffi.mycelium.ContactStatus
typealias MiniAppInfo = uniffi.mycelium.MiniAppInfo
typealias AppStoreListing = uniffi.mycelium.AppStoreListing
typealias MiniAppInstallPreview = uniffi.mycelium.MiniAppInstallPreview
typealias PresenceProfileData = uniffi.mycelium.PresenceProfileData
typealias NearbyProfile = uniffi.mycelium.NearbyProfile
typealias ProximityMessageData = uniffi.mycelium.ProximityMessageData
typealias ScopeKeyInfo = uniffi.mycelium.ScopeKeyInfo
typealias PeerReputationData = uniffi.mycelium.PeerReputationData
typealias BleOutboundFrame = uniffi.mycelium.BleOutboundFrame

object mycelium {
    fun initNode(config: NodeConfig) = uniffi.mycelium.initNode(config)
    fun stopNode() = uniffi.mycelium.stopNode()
    fun localPeerId(): String = uniffi.mycelium.localPeerId()
    fun localAccountId(): String = uniffi.mycelium.localAccountId()
    fun knownPeers(): List<String> = uniffi.mycelium.knownPeers()
    fun connectedPeers(): List<String> = uniffi.mycelium.connectedPeers()
    fun metrics(): NodeMetrics = uniffi.mycelium.metrics()
    fun relayPeerId(): String = uniffi.mycelium.relayPeerId()
    fun relayKeypairAgeSecs(): ULong = uniffi.mycelium.relayKeypairAgeSecs()
    fun getBulletinSubscriptions(): List<String> = uniffi.mycelium.getBulletinSubscriptions()
    fun subscribeBulletinScope(scope: String) = uniffi.mycelium.subscribeBulletinScope(scope)
    fun unsubscribeBulletinScope(scope: String) = uniffi.mycelium.unsubscribeBulletinScope(scope)
    fun isBulletinEnabled(): Boolean = uniffi.mycelium.isBulletinEnabled()
    fun listScopeKeys(): List<ScopeKeyInfo> = uniffi.mycelium.listScopeKeys()
    fun addScopeKey(inviteJson: String) = uniffi.mycelium.addScopeKey(inviteJson)
    fun createScopeKey(scope: String, displayName: String) =
        uniffi.mycelium.createScopeKey(scope, displayName)
    fun exportScopeKey(scope: String): String = uniffi.mycelium.exportScopeKey(scope)
    fun deleteScopeKey(scope: String) = uniffi.mycelium.deleteScopeKey(scope)
    fun scopeIsEncrypted(scope: String): Boolean = uniffi.mycelium.scopeIsEncrypted(scope)
    fun startProximity(profile: PresenceProfileData, ttlSecs: UInt) =
        uniffi.mycelium.startProximity(profile, ttlSecs)
    fun stopProximity() = uniffi.mycelium.stopProximity()
    fun nearbyProfiles(): List<NearbyProfile> = uniffi.mycelium.nearbyProfiles()
    fun isProximityActive(): Boolean = uniffi.mycelium.isProximityActive()
    fun expressProximityInterest(encPubkeyHex: String): Boolean =
        uniffi.mycelium.expressProximityInterest(encPubkeyHex)
    fun proximityMessages(sinceMs: ULong = 0u): List<ProximityMessageData> =
        uniffi.mycelium.proximityMessages(sinceMs)
    fun sendProximityMessage(encPubkeyHex: String, message: String) =
        uniffi.mycelium.sendProximityMessage(encPubkeyHex, message)
    fun sendChatDirect(toPeer: String, body: String) = uniffi.mycelium.sendChatDirect(toPeer, body)
    fun sendChatEncrypted(toPeer: String, body: String) = uniffi.mycelium.sendChatEncrypted(toPeer, body)
    fun localEncPubkey(): String = uniffi.mycelium.localEncPubkey()
    fun hasEncKeyFor(peerId: String): Boolean = uniffi.mycelium.hasEncKeyFor(peerId)
    fun sendChatBroadcast(body: String) = uniffi.mycelium.sendChatBroadcast(body)
    fun chatHistory(peerId: String, limit: UInt): List<ChatMessage> = uniffi.mycelium.chatHistory(peerId, limit)
    fun postBulletin(scope: String, title: String, body: String, ttlSecs: ULong) =
        uniffi.mycelium.postBulletin(scope, title, body, ttlSecs)
    fun bulletinsForScope(scope: String): List<BulletinPost> = uniffi.mycelium.bulletinsForScope(scope)
    fun postSos(geohash: String, message: String, ttlSecs: ULong) =
        uniffi.mycelium.postSos(geohash, message, ttlSecs)
    fun listSosPosts(): List<SosPost> = uniffi.mycelium.listSosPosts()
    fun setListenSos(enabled: Boolean) = uniffi.mycelium.setListenSos(enabled)
    fun isListenSosEnabled(): Boolean = uniffi.mycelium.isListenSosEnabled()
    fun sendMail(toPeer: String, subject: String, body: String) = uniffi.mycelium.sendMail(toPeer, subject, body)
    fun mailInbox(limit: UInt): List<MailMessage> = uniffi.mycelium.mailInbox(limit)
    fun mailSent(limit: UInt): List<MailMessage> = uniffi.mycelium.mailSent(limit)
    fun markMailRead(mailId: String) = uniffi.mycelium.markMailRead(mailId)
    fun setDisplayName(name: String) = uniffi.mycelium.setDisplayName(name)
    fun setRendezvousEnabled(enabled: Boolean) = uniffi.mycelium.setRendezvousEnabled(enabled)
    fun setInternetEnabled(enabled: Boolean) = uniffi.mycelium.setInternetEnabled(enabled)
    fun isInternetEnabled(): Boolean = uniffi.mycelium.isInternetEnabled()
    fun setCrowdMode(enabled: Boolean) = uniffi.mycelium.setCrowdMode(enabled)
    fun isCrowdModeEnabled(): Boolean = uniffi.mycelium.isCrowdModeEnabled()
    fun getShareableMultiaddrs(): List<String> = uniffi.mycelium.getShareableMultiaddrs()
    fun displayName(): String = uniffi.mycelium.displayName()
    fun setEnergyState(state: EnergyState) = uniffi.mycelium.setEnergyState(state)
    fun addBootstrapPeer(multiaddr: String) = uniffi.mycelium.addBootstrapPeer(multiaddr)
    fun connectInvite(raw: String) = uniffi.mycelium.connectInvite(raw)
    fun connectTransportInvite(raw: String) = uniffi.mycelium.connectTransportInvite(raw)
    fun ingestBleFrame(fromPeer: String, frame: ByteArray) =
        uniffi.mycelium.ingestBleFrame(fromPeer, frame)
    fun peerIdFromBleFrame(frame: ByteArray): String? =
        uniffi.mycelium.peerIdFromBleFrame(frame)
    fun drainBleOutbound(toPeer: String?, max: UInt): List<BleOutboundFrame> =
        uniffi.mycelium.drainBleOutbound(toPeer, max)
    fun registerBleLink(peerId: String, deviceId: String) =
        uniffi.mycelium.registerBleLink(peerId, deviceId)
    fun ingestBleStreamFrame(fromPeer: String, frame: ByteArray) =
        uniffi.mycelium.ingestBleStreamFrame(fromPeer, frame)
    fun drainBleStreamOutbound(toPeer: String, max: UInt): List<ByteArray> =
        uniffi.mycelium.drainBleStreamOutbound(toPeer, max)
    fun blePeerAddrs(): List<String> = uniffi.mycelium.blePeerAddrs()
    fun setEventCallback(callback: NodeEventCallback) = uniffi.mycelium.setEventCallback(callback)
    fun walletAddress(): String = uniffi.mycelium.walletAddress()
    fun walletBalance(): WalletBalance = uniffi.mycelium.walletBalance()
    fun sendTransaction(toAddress: String, amountMuon: ULong, memo: String?) =
        uniffi.mycelium.sendTransaction(toAddress, amountMuon, memo)
    fun walletRecentTransactions(limit: UInt): List<TxInfo> =
        uniffi.mycelium.walletRecentTransactions(limit)
    fun requestFaucet() = uniffi.mycelium.requestFaucet()
    fun currentConnectivityMode(): ConnectivityMode = uniffi.mycelium.currentConnectivityMode()
    fun getHotWalletConfig(): HotWalletConfig = uniffi.mycelium.getHotWalletConfig()
    fun setHotWalletConfig(config: HotWalletConfig) = uniffi.mycelium.setHotWalletConfig(config)
    fun setColdWalletAddress(address: String?) = uniffi.mycelium.setColdWalletAddress(address)
    fun hotWalletStatus(): HotWalletStatus = uniffi.mycelium.hotWalletStatus()
    fun buildPaymentRequestUri(toAddress: String, amountMuon: ULong, memo: String?): String =
        uniffi.mycelium.buildPaymentRequestUri(toAddress, amountMuon, memo)
    fun parsePaymentRequestUri(uri: String): PaymentRequestData? =
        uniffi.mycelium.parsePaymentRequestUri(uri)

    fun listContacts(): List<ContactInfo> = uniffi.mycelium.listContacts()
    fun listAcceptedContacts(): List<ContactInfo> = uniffi.mycelium.listAcceptedContacts()
    fun listPendingContacts(): List<ContactInfo> = uniffi.mycelium.listPendingContacts()
    fun addContact(peerId: String, displayName: String, accepted: Boolean): ContactInfo =
        uniffi.mycelium.addContact(peerId, displayName, accepted)
    fun acceptContact(peerId: String): ContactInfo = uniffi.mycelium.acceptContact(peerId)
    fun rejectContact(peerId: String) = uniffi.mycelium.rejectContact(peerId)
    fun blockContact(peerId: String): ContactInfo = uniffi.mycelium.blockContact(peerId)
    fun unblockContact(peerId: String): ContactInfo = uniffi.mycelium.unblockContact(peerId)
    fun peerReputation(peerId: String): PeerReputationData? = uniffi.mycelium.peerReputation(peerId)
    fun removeContact(peerId: String) = uniffi.mycelium.removeContact(peerId)

    fun listGroups(): List<GroupInfo> = uniffi.mycelium.listGroups()
    fun createGroup(name: String): GroupInfo = uniffi.mycelium.createGroup(name)
    fun importGroupInvite(json: String): GroupInfo? = uniffi.mycelium.importGroupInvite(json)
    fun exportGroupInvite(groupId: String): String = uniffi.mycelium.exportGroupInvite(groupId)
    fun sendGroupMessage(groupId: String, body: String) = uniffi.mycelium.sendGroupMessage(groupId, body)
    fun deleteGroup(groupId: String) = uniffi.mycelium.deleteGroup(groupId)
    fun groupChatHistory(groupId: String, limit: UInt): List<ChatMessage> =
        uniffi.mycelium.groupChatHistory(groupId, limit)

    fun listInstalledApps(): List<MiniAppInfo> = uniffi.mycelium.listInstalledApps()
    fun previewMiniappInstall(bundleData: ByteArray): MiniAppInstallPreview =
        uniffi.mycelium.previewMiniappInstall(bundleData)
    fun installApp(
        bundleData: ByteArray,
        listingAppId: String?,
        allowSideload: Boolean,
        allowDowngrade: Boolean = false,
    ): MiniAppInfo = uniffi.mycelium.installApp(bundleData, listingAppId, allowSideload, allowDowngrade)
    fun installOfficialBundle(appId: String): MiniAppInfo =
        uniffi.mycelium.installOfficialBundle(appId)
    fun uninstallApp(appId: String) = uniffi.mycelium.uninstallApp(appId)
    fun getAppFile(appId: String, path: String): ByteArray? = uniffi.mycelium.getAppFile(appId, path)
    fun browseAppStore(): List<AppStoreListing> = uniffi.mycelium.browseAppStore()
    fun miniappStorageGet(appId: String, key: String): String? =
        uniffi.mycelium.miniappStorageGet(appId, key)
    fun miniappStorageSet(appId: String, key: String, value: String) =
        uniffi.mycelium.miniappStorageSet(appId, key, value)
    fun miniappStorageDelete(appId: String, key: String) =
        uniffi.mycelium.miniappStorageDelete(appId, key)

    fun miniappBridgeCall(appId: String, method: String, argsJson: String): String =
        uniffi.mycelium.miniappBridgeCall(appId, method, argsJson)
    fun miniappIssueBridgeSession(appId: String): String =
        uniffi.mycelium.miniappIssueBridgeSession(appId)
    fun miniappRevokeBridgeSession(appId: String) =
        uniffi.mycelium.miniappRevokeBridgeSession(appId)
    fun miniappGetPolicy(appId: String): uniffi.mycelium.MiniAppPolicyData =
        uniffi.mycelium.miniappGetPolicy(appId)
    fun miniappSetSafeMode(appId: String, enabled: Boolean) =
        uniffi.mycelium.miniappSetSafeMode(appId, enabled)
    fun miniappReportApp(appId: String) = uniffi.mycelium.miniappReportApp(appId)

    fun miniappIssueCapability(appId: String, permission: String, sessionToken: String): String =
        uniffi.mycelium.miniappIssueCapability(appId, permission, sessionToken)

    fun miniappPublishRevocation(appId: String, reason: String) =
        uniffi.mycelium.miniappPublishRevocation(appId, reason)
}
