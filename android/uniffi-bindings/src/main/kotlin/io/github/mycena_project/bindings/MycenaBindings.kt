// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.bindings

typealias NodeConfig = uniffi.mycena.NodeConfig
typealias ChatMessage = uniffi.mycena.ChatMessage
typealias BulletinPost = uniffi.mycena.BulletinPost
typealias SosPost = uniffi.mycena.SosPost
typealias ResourceMapEntry = uniffi.mycena.ResourceMapEntry
typealias VolunteerTaskEntry = uniffi.mycena.VolunteerTaskEntry
typealias MailMessage = uniffi.mycena.MailMessage
typealias NodeMetrics = uniffi.mycena.NodeMetrics
typealias WalletBalance = uniffi.mycena.WalletBalance
typealias TxInfo = uniffi.mycena.TxInfo
typealias EnergyState = uniffi.mycena.EnergyState
typealias NodeEventCallback = uniffi.mycena.NodeEventCallback
typealias ConnectivityMode = uniffi.mycena.ConnectivityMode
typealias HotWalletConfig = uniffi.mycena.HotWalletConfig
typealias HotWalletStatus = uniffi.mycena.HotWalletStatus
typealias PaymentRequestData = uniffi.mycena.PaymentRequestData
typealias GroupInfo = uniffi.mycena.GroupInfo
typealias ContactInfo = uniffi.mycena.ContactInfo
typealias ContactStatus = uniffi.mycena.ContactStatus
typealias MiniAppInfo = uniffi.mycena.MiniAppInfo
typealias AppStoreListing = uniffi.mycena.AppStoreListing
typealias MiniAppInstallPreview = uniffi.mycena.MiniAppInstallPreview
typealias PresenceProfileData = uniffi.mycena.PresenceProfileData
typealias NearbyProfile = uniffi.mycena.NearbyProfile
typealias ProximityMessageData = uniffi.mycena.ProximityMessageData
typealias ScopeKeyInfo = uniffi.mycena.ScopeKeyInfo
typealias PeerReputationData = uniffi.mycena.PeerReputationData
typealias BleOutboundFrame = uniffi.mycena.BleOutboundFrame
typealias PrivacyShapingConfig = uniffi.mycena.PrivacyShapingConfig
typealias PrivacyShapingProfile = uniffi.mycena.PrivacyShapingProfile
typealias PrivacyShapingOverheadEstimate = uniffi.mycena.PrivacyShapingOverheadEstimate
typealias UserTrustSummary = uniffi.mycena.UserTrustSummary
typealias CustodyPendingStatus = uniffi.mycena.CustodyPendingStatus
typealias ExpectedDeliveryData = uniffi.mycena.ExpectedDeliveryData
typealias ExpectedDeliveryBasis = uniffi.mycena.ExpectedDeliveryBasis
typealias ThermalState = uniffi.mycena.ThermalState
typealias MovementIndicator = uniffi.mycena.MovementIndicator
typealias EnergyProfileData = uniffi.mycena.EnergyProfileData

object mycena {
    fun initNode(config: NodeConfig) = uniffi.mycena.initNode(config)
    fun stopNode() = uniffi.mycena.stopNode()
    fun localPeerId(): String = uniffi.mycena.localPeerId()
    fun localAccountId(): String = uniffi.mycena.localAccountId()
    fun knownPeers(): List<String> = uniffi.mycena.knownPeers()
    fun connectedPeers(): List<String> = uniffi.mycena.connectedPeers()
    fun listPendingCustody(): List<CustodyPendingStatus> = uniffi.mycena.listPendingCustody()
    fun metrics(): NodeMetrics = uniffi.mycena.metrics()
    fun relayPeerId(): String = uniffi.mycena.relayPeerId()
    fun relayKeypairAgeSecs(): ULong = uniffi.mycena.relayKeypairAgeSecs()
    fun getBulletinSubscriptions(): List<String> = uniffi.mycena.getBulletinSubscriptions()
    fun subscribeBulletinScope(scope: String) = uniffi.mycena.subscribeBulletinScope(scope)
    fun unsubscribeBulletinScope(scope: String) = uniffi.mycena.unsubscribeBulletinScope(scope)
    fun isBulletinEnabled(): Boolean = uniffi.mycena.isBulletinEnabled()
    fun listScopeKeys(): List<ScopeKeyInfo> = uniffi.mycena.listScopeKeys()
    fun addScopeKey(inviteJson: String) = uniffi.mycena.addScopeKey(inviteJson)
    fun createScopeKey(scope: String, displayName: String) =
        uniffi.mycena.createScopeKey(scope, displayName)
    fun exportScopeKey(scope: String): String = uniffi.mycena.exportScopeKey(scope)
    fun deleteScopeKey(scope: String) = uniffi.mycena.deleteScopeKey(scope)
    fun scopeIsEncrypted(scope: String): Boolean = uniffi.mycena.scopeIsEncrypted(scope)
    fun startProximity(profile: PresenceProfileData, ttlSecs: UInt) =
        uniffi.mycena.startProximity(profile, ttlSecs)
    fun stopProximity() = uniffi.mycena.stopProximity()
    fun nearbyProfiles(): List<NearbyProfile> = uniffi.mycena.nearbyProfiles()
    fun isProximityActive(): Boolean = uniffi.mycena.isProximityActive()
    fun expressProximityInterest(encPubkeyHex: String): Boolean =
        uniffi.mycena.expressProximityInterest(encPubkeyHex)
    fun proximityMessages(sinceMs: ULong = 0u): List<ProximityMessageData> =
        uniffi.mycena.proximityMessages(sinceMs)
    fun sendProximityMessage(encPubkeyHex: String, message: String) =
        uniffi.mycena.sendProximityMessage(encPubkeyHex, message)
    fun sendChatDirect(toPeer: String, body: String) = uniffi.mycena.sendChatDirect(toPeer, body)
    fun sendChatPriority(toPeer: String, body: String, depositMuon: ULong) =
        uniffi.mycena.sendChatPriority(toPeer, body, depositMuon)
    fun userTrustSummary(): UserTrustSummary = uniffi.mycena.userTrustSummary()
    fun endorsePeer(peerId: String, strength: UByte) = uniffi.mycena.endorsePeer(peerId, strength)
    fun sendChatEncrypted(toPeer: String, body: String) = uniffi.mycena.sendChatEncrypted(toPeer, body)
    fun localEncPubkey(): String = uniffi.mycena.localEncPubkey()
    fun hasEncKeyFor(peerId: String): Boolean = uniffi.mycena.hasEncKeyFor(peerId)
    fun sendChatBroadcast(body: String) = uniffi.mycena.sendChatBroadcast(body)
    fun chatHistory(peerId: String, limit: UInt): List<ChatMessage> = uniffi.mycena.chatHistory(peerId, limit)
    fun postBulletin(scope: String, title: String, body: String, ttlSecs: ULong, geohash: String? = null) =
        uniffi.mycena.postBulletin(scope, title, body, ttlSecs, geohash)
    fun bulletinsForScope(scope: String): List<BulletinPost> = uniffi.mycena.bulletinsForScope(scope)
    fun postSos(geohash: String, message: String, ttlSecs: ULong) =
        uniffi.mycena.postSos(geohash, message, ttlSecs)
    fun listSosPosts(): List<SosPost> = uniffi.mycena.listSosPosts()
    fun setListenSos(enabled: Boolean) = uniffi.mycena.setListenSos(enabled)
    fun isListenSosEnabled(): Boolean = uniffi.mycena.isListenSosEnabled()
    fun setCrisisManualMode(mode: UByte) = uniffi.mycena.setCrisisManualMode(mode)
    fun isCrisisModeActive(): Boolean = uniffi.mycena.isCrisisModeActive()
    fun refreshCrisisAuto(internetReachable: Boolean, localGeohashPrefix: String?) =
        uniffi.mycena.refreshCrisisAuto(internetReachable, localGeohashPrefix)
    fun setLocalGeohashPrefix(prefix: String?) =
        uniffi.mycena.setLocalGeohashPrefix(prefix)
    fun postResourceMap(geohash: String, category: String, description: String, ttlSecs: ULong) =
        uniffi.mycena.postResourceMap(geohash, category, description, ttlSecs)
    fun listResourceMapEntries(): List<ResourceMapEntry> = uniffi.mycena.listResourceMapEntries()
    fun postVolunteerTask(
        geohash: String,
        category: String,
        description: String,
        slotsNeeded: UInt,
        ttlSecs: ULong,
    ): VolunteerTaskEntry =
        uniffi.mycena.postVolunteerTask(geohash, category, description, slotsNeeded, ttlSecs)
    fun listVolunteerTasks(): List<VolunteerTaskEntry> = uniffi.mycena.listVolunteerTasks()
    fun claimVolunteerTask(taskId: String): VolunteerTaskEntry =
        uniffi.mycena.claimVolunteerTask(taskId)
    fun sendMail(toPeer: String, subject: String, body: String) = uniffi.mycena.sendMail(toPeer, subject, body)
    fun mailInbox(limit: UInt): List<MailMessage> = uniffi.mycena.mailInbox(limit)
    fun mailSent(limit: UInt): List<MailMessage> = uniffi.mycena.mailSent(limit)
    fun markMailRead(mailId: String) = uniffi.mycena.markMailRead(mailId)
    fun setDisplayName(name: String) = uniffi.mycena.setDisplayName(name)
    fun setRendezvousEnabled(enabled: Boolean) = uniffi.mycena.setRendezvousEnabled(enabled)
    fun setInternetEnabled(enabled: Boolean) = uniffi.mycena.setInternetEnabled(enabled)
    fun isInternetEnabled(): Boolean = uniffi.mycena.isInternetEnabled()
    fun setCrowdMode(enabled: Boolean) = uniffi.mycena.setCrowdMode(enabled)
    fun isCrowdModeEnabled(): Boolean = uniffi.mycena.isCrowdModeEnabled()
    fun setPrivacyShaping(config: PrivacyShapingConfig) = uniffi.mycena.setPrivacyShaping(config)
    fun privacyShapingOverheadEstimate(config: PrivacyShapingConfig): PrivacyShapingOverheadEstimate =
        uniffi.mycena.privacyShapingOverheadEstimate(config)
    fun defaultPrivacyShapingConfig(profile: PrivacyShapingProfile): PrivacyShapingConfig =
        uniffi.mycena.defaultPrivacyShapingConfig(profile)
    fun getShareableMultiaddrs(): List<String> = uniffi.mycena.getShareableMultiaddrs()
    fun buildOfflineShareBootstrapSidecar(installUrl: String): ByteArray =
        uniffi.mycena.buildOfflineShareBootstrapSidecar(installUrl)
    fun propagateInstalledMiniapps(): UInt = uniffi.mycena.propagateInstalledMiniapps()
    fun displayName(): String = uniffi.mycena.displayName()
    fun setEnergyState(state: EnergyState) = uniffi.mycena.setEnergyState(state)
    fun setWifiAwareAvailable(available: Boolean) = uniffi.mycena.setWifiAwareAvailable(available)
    fun setBleDiscoveryActive(active: Boolean) = uniffi.mycena.setBleDiscoveryActive(active)
    fun preferWifiAwareForDiscovery(): Boolean = uniffi.mycena.preferWifiAwareForDiscovery()
    fun queueDiscoveryAnnouncement(inviteUri: String) = uniffi.mycena.queueDiscoveryAnnouncement(inviteUri)
    fun ingestWifiAwareDiscovery(peerHint: String, inviteUri: String, latencyMs: ULong) =
        uniffi.mycena.ingestWifiAwareDiscovery(peerHint, inviteUri, latencyMs)
    fun recordBleDiscoveryObservation(latencyMs: ULong, bytes: UInt, success: Boolean) =
        uniffi.mycena.recordBleDiscoveryObservation(latencyMs, bytes, success)
    fun drainDiscoveryAnnouncements(max: UInt): List<String> =
        uniffi.mycena.drainDiscoveryAnnouncements(max)
    fun setWebRtcAvailable(available: Boolean) = uniffi.mycena.setWebrtcAvailable(available)
    fun setWebRtcLinkReady(peer: String, ready: Boolean) =
        uniffi.mycena.setWebrtcLinkReady(peer, ready)
    fun ingestWebRtcFrame(fromPeer: String, frame: ByteArray) =
        uniffi.mycena.ingestWebrtcFrame(fromPeer, frame)
    fun postWebRtcSignal(
        toPeer: String,
        sessionId: String,
        kind: UByte,
        payload: String,
        iceMlineIndex: UInt?,
    ) = uniffi.mycena.postWebrtcSignal(toPeer, sessionId, kind, payload, iceMlineIndex)
    fun drainWebRtcSignalingInbound(max: UInt): List<uniffi.mycena.WebRtcSignalFrame> =
        uniffi.mycena.drainWebrtcSignalingInbound(max)
    fun drainWebRtcNegotiationRequests(max: UInt): List<String> =
        uniffi.mycena.drainWebrtcNegotiationRequests(max)
    fun webrtcStunServers(): List<String> = uniffi.mycena.webrtcStunServers()
    fun setThermalState(state: ThermalState) = uniffi.mycena.setThermalState(state)
    fun energyProfile(): EnergyProfileData = uniffi.mycena.energyProfile()
    fun addBootstrapPeer(multiaddr: String) = uniffi.mycena.addBootstrapPeer(multiaddr)
    fun connectInvite(raw: String) = uniffi.mycena.connectInvite(raw)
    fun connectTransportInvite(raw: String) = uniffi.mycena.connectTransportInvite(raw)
    fun relayCircuitMultiaddr(remotePeerId: String): String? =
        uniffi.mycena.relayCircuitMultiaddr(remotePeerId)
    fun ingestBleFrame(fromPeer: String, frame: ByteArray) =
        uniffi.mycena.ingestBleFrame(fromPeer, frame)
    fun peerIdFromBleFrame(frame: ByteArray): String? =
        uniffi.mycena.peerIdFromBleFrame(frame)
    fun drainBleOutbound(toPeer: String?, max: UInt): List<BleOutboundFrame> =
        uniffi.mycena.drainBleOutbound(toPeer, max)
    fun drainBearerOutbound(toPeer: String?, max: UInt): List<BleOutboundFrame> =
        uniffi.mycena.drainBearerOutbound(toPeer, max)
    fun queueBearerBulk(toPeer: String, payload: ByteArray) =
        uniffi.mycena.queueBearerBulk(toPeer, payload)
    fun registerBleLink(peerId: String, deviceId: String) =
        uniffi.mycena.registerBleLink(peerId, deviceId)
    fun ingestBleStreamFrame(fromPeer: String, frame: ByteArray) =
        uniffi.mycena.ingestBleStreamFrame(fromPeer, frame)
    fun drainBleStreamOutbound(toPeer: String, max: UInt): List<ByteArray> =
        uniffi.mycena.drainBleStreamOutbound(toPeer, max)
    fun blePeerAddrs(): List<String> = uniffi.mycena.blePeerAddrs()
    fun setEventCallback(callback: NodeEventCallback) = uniffi.mycena.setEventCallback(callback)
    fun walletAddress(): String = uniffi.mycena.walletAddress()
    fun walletBalance(): WalletBalance = uniffi.mycena.walletBalance()
    fun sendTransaction(toAddress: String, amountMuon: ULong, memo: String?) =
        uniffi.mycena.sendTransaction(toAddress, amountMuon, memo)
    fun walletRecentTransactions(limit: UInt): List<TxInfo> =
        uniffi.mycena.walletRecentTransactions(limit)
    fun requestFaucet() = uniffi.mycena.requestFaucet()
    fun currentConnectivityMode(): ConnectivityMode = uniffi.mycena.currentConnectivityMode()
    fun getHotWalletConfig(): HotWalletConfig = uniffi.mycena.getHotWalletConfig()
    fun setHotWalletConfig(config: HotWalletConfig) = uniffi.mycena.setHotWalletConfig(config)
    fun setColdWalletAddress(address: String?) = uniffi.mycena.setColdWalletAddress(address)
    fun hotWalletStatus(): HotWalletStatus = uniffi.mycena.hotWalletStatus()
    fun buildPaymentRequestUri(toAddress: String, amountMuon: ULong, memo: String?): String =
        uniffi.mycena.buildPaymentRequestUri(toAddress, amountMuon, memo)
    fun parsePaymentRequestUri(uri: String): PaymentRequestData? =
        uniffi.mycena.parsePaymentRequestUri(uri)

    fun listContacts(): List<ContactInfo> = uniffi.mycena.listContacts()
    fun listAcceptedContacts(): List<ContactInfo> = uniffi.mycena.listAcceptedContacts()
    fun listPendingContacts(): List<ContactInfo> = uniffi.mycena.listPendingContacts()
    fun addContact(peerId: String, displayName: String, accepted: Boolean): ContactInfo =
        uniffi.mycena.addContact(peerId, displayName, accepted)
    fun acceptContact(peerId: String): ContactInfo = uniffi.mycena.acceptContact(peerId)
    fun rejectContact(peerId: String) = uniffi.mycena.rejectContact(peerId)
    fun blockContact(peerId: String): ContactInfo = uniffi.mycena.blockContact(peerId)
    fun unblockContact(peerId: String): ContactInfo = uniffi.mycena.unblockContact(peerId)
    fun peerReputation(peerId: String): PeerReputationData? = uniffi.mycena.peerReputation(peerId)
    fun removeContact(peerId: String) = uniffi.mycena.removeContact(peerId)

    fun listGroups(): List<GroupInfo> = uniffi.mycena.listGroups()
    fun createGroup(name: String): GroupInfo = uniffi.mycena.createGroup(name)
    fun importGroupInvite(json: String): GroupInfo? = uniffi.mycena.importGroupInvite(json)
    fun exportGroupInvite(groupId: String): String = uniffi.mycena.exportGroupInvite(groupId)
    fun sendGroupMessage(groupId: String, body: String) = uniffi.mycena.sendGroupMessage(groupId, body)
    fun deleteGroup(groupId: String) = uniffi.mycena.deleteGroup(groupId)
    fun groupChatHistory(groupId: String, limit: UInt): List<ChatMessage> =
        uniffi.mycena.groupChatHistory(groupId, limit)

    fun listInstalledApps(): List<MiniAppInfo> = uniffi.mycena.listInstalledApps()
    fun previewMiniappInstall(bundleData: ByteArray): MiniAppInstallPreview =
        uniffi.mycena.previewMiniappInstall(bundleData)
    fun installApp(
        bundleData: ByteArray,
        listingAppId: String?,
        allowSideload: Boolean,
        allowDowngrade: Boolean = false,
    ): MiniAppInfo = uniffi.mycena.installApp(bundleData, listingAppId, allowSideload, allowDowngrade)
    fun installOfficialBundle(appId: String): MiniAppInfo =
        uniffi.mycena.installOfficialBundle(appId)
    fun uninstallApp(appId: String) = uniffi.mycena.uninstallApp(appId)
    fun getAppFile(appId: String, path: String): ByteArray? = uniffi.mycena.getAppFile(appId, path)
    fun miniappGetRuntime(appId: String): String = uniffi.mycena.miniappGetRuntime(appId)
    fun miniappInvokeWasmRun(appId: String): Int = uniffi.mycena.miniappInvokeWasmRun(appId)
    fun browseAppStore(): List<AppStoreListing> = uniffi.mycena.browseAppStore()
    fun miniappStorageGet(appId: String, key: String): String? =
        uniffi.mycena.miniappStorageGet(appId, key)
    fun miniappStorageSet(appId: String, key: String, value: String) =
        uniffi.mycena.miniappStorageSet(appId, key, value)
    fun miniappStorageDelete(appId: String, key: String) =
        uniffi.mycena.miniappStorageDelete(appId, key)

    fun miniappBridgeCall(appId: String, method: String, argsJson: String): String =
        uniffi.mycena.miniappBridgeCall(appId, method, argsJson)
    fun miniappIssueBridgeSession(appId: String): String =
        uniffi.mycena.miniappIssueBridgeSession(appId)
    fun miniappRevokeBridgeSession(appId: String) =
        uniffi.mycena.miniappRevokeBridgeSession(appId)
    fun miniappGetPolicy(appId: String): uniffi.mycena.MiniAppPolicyData =
        uniffi.mycena.miniappGetPolicy(appId)
    fun miniappSetSafeMode(appId: String, enabled: Boolean) =
        uniffi.mycena.miniappSetSafeMode(appId, enabled)
    fun miniappReportApp(appId: String) = uniffi.mycena.miniappReportApp(appId)

    fun miniappIssueCapability(appId: String, permission: String, sessionToken: String): String =
        uniffi.mycena.miniappIssueCapability(appId, permission, sessionToken)

    fun miniappPublishRevocation(appId: String, reason: String) =
        uniffi.mycena.miniappPublishRevocation(appId, reason)
}
