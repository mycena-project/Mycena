// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.mycelium.bindings.mycelium
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC RTCDataChannel host for phone↔desktop/browser peers.
 * Signaling (SDP/ICE) uses libp2p relay via Rust; MYBR frames use medium=3.
 */
class WebRtcDiscoveryHost(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val tag = "WebRtcHost"
    private var loopJob: Job? = null
    private var factory: PeerConnectionFactory? = null
    private val sessions = ConcurrentHashMap<String, PeerEntry>()

    private data class PeerEntry(
        val sessionId: String,
        val pc: PeerConnection,
        var dc: DataChannel? = null,
    )

    fun start() {
        if (loopJob?.isActive == true) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        mycelium.setWebRtcAvailable(true)
        loopJob =
            scope.launch(Dispatchers.IO) {
                while (isActive) {
                    runCatching { tick() }.onFailure {
                        Log.w(tag, "tick failed: ${it.message}")
                    }
                    delay(TICK_MS)
                }
            }
        Log.i(tag, "WebRTC host started")
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        sessions.values.forEach { it.pc.close() }
        sessions.clear()
        factory?.dispose()
        factory = null
        mycelium.setWebRtcAvailable(false)
    }

    private suspend fun tick() {
        val negotiate = mycelium.drainWebRtcNegotiationRequests(4u)
        for (peer in negotiate) {
            createOffer(peer)
        }
        val signals = mycelium.drainWebRtcSignalingInbound(8u)
        for (signal in signals) {
            handleSignal(signal)
        }
        WebRtcRelay.flushOutbound(this)
    }

    private suspend fun createOffer(peerId: String) {
        val sessionId = UUID.randomUUID().toString()
        val entry = ensurePeer(peerId, sessionId) ?: return
        val constraints = MediaConstraints()
        entry.pc.createOffer(
            object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null) return
                    entry.pc.setLocalDescription(SimpleSdpObserver(), desc)
                    scope.launch(Dispatchers.IO) {
                        mycelium.postWebRtcSignal(
                            peerId,
                            sessionId,
                            0u,
                            desc.description,
                            null,
                        )
                    }
                }
            },
            constraints,
        )
    }

    private suspend fun handleSignal(
        signal: uniffi.mycelium.WebRtcSignalFrame,
    ) {
        val peerId = signal.fromPeer
        val sessionId = signal.sessionId
        val entry = ensurePeer(peerId, sessionId) ?: return
        when (signal.kind.toInt()) {
            0 -> {
                val offer = SessionDescription(SessionDescription.Type.OFFER, signal.payload)
                entry.pc.setRemoteDescription(
                    object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            entry.pc.createAnswer(
                                object : SimpleSdpObserver() {
                                    override fun onCreateSuccess(desc: SessionDescription?) {
                                        if (desc == null) return
                                        entry.pc.setLocalDescription(SimpleSdpObserver(), desc)
                                        scope.launch(Dispatchers.IO) {
                                            mycelium.postWebRtcSignal(
                                                peerId,
                                                sessionId,
                                                1u,
                                                desc.description,
                                                null,
                                            )
                                        }
                                    }
                                },
                                MediaConstraints(),
                            )
                        }
                    },
                    offer,
                )
            }
            1 -> {
                val answer = SessionDescription(SessionDescription.Type.ANSWER, signal.payload)
                entry.pc.setRemoteDescription(SimpleSdpObserver(), answer)
            }
            2 -> {
                val json = JSONObject(signal.payload)
                val candidate =
                    IceCandidate(
                        json.optString("sdpMid"),
                        json.optInt("sdpMLineIndex", signal.iceMlineIndex?.toInt() ?: 0),
                        json.optString("candidate"),
                    )
                entry.pc.addIceCandidate(candidate)
            }
        }
    }

    private fun ensurePeer(peerId: String, sessionId: String): PeerEntry? {
        sessions[peerId]?.let {
            if (it.sessionId == sessionId) return it
            it.pc.close()
        }
        val iceServers =
            mycelium.webrtcStunServers().map { url ->
                PeerConnection.IceServer.builder(url).createIceServer()
            }
        val rtcConfig =
            PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }
        val pc =
            factory?.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) =
                        Unit
                    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) =
                        Unit
                    override fun onIceCandidate(candidate: IceCandidate?) {
                        candidate ?: return
                        val payload =
                            JSONObject()
                                .put("candidate", candidate.sdp)
                                .put("sdpMid", candidate.sdpMid)
                                .put("sdpMLineIndex", candidate.sdpMLineIndex)
                                .toString()
                        scope.launch(Dispatchers.IO) {
                            mycelium.postWebRtcSignal(
                                peerId,
                                sessionId,
                                2u,
                                payload,
                                candidate.sdpMLineIndex.toUInt(),
                            )
                        }
                    }
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                    override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
                    override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
                    override fun onDataChannel(channel: DataChannel?) {
                        channel ?: return
                        wireDataChannel(peerId, channel)
                    }
                    override fun onRenegotiationNeeded() = Unit
                    override fun onAddTrack(
                        receiver: org.webrtc.RtpReceiver?,
                        mediaStreams: Array<out org.webrtc.MediaStream>?,
                    ) = Unit
                },
            ) ?: return null
        val init =
            DataChannel.Init().apply {
                ordered = true
            }
        val dc = pc.createDataChannel(DATA_CHANNEL_LABEL, init)
        wireDataChannel(peerId, dc)
        val entry = PeerEntry(sessionId, pc, dc)
        sessions[peerId] = entry
        return entry
    }

    private fun wireDataChannel(peerId: String, channel: DataChannel) {
        sessions[peerId]?.dc = channel
        channel.registerObserver(
            object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) = Unit
                override fun onStateChange() {
                    when (channel.state()) {
                        DataChannel.State.OPEN -> {
                            scope.launch(Dispatchers.IO) {
                                mycelium.setWebRtcLinkReady(peerId, true)
                            }
                        }
                        DataChannel.State.CLOSED, DataChannel.State.CLOSING -> {
                            scope.launch(Dispatchers.IO) {
                                mycelium.setWebRtcLinkReady(peerId, false)
                            }
                        }
                        else -> Unit
                    }
                }
                override fun onMessage(buffer: DataChannel.Buffer?) {
                    buffer ?: return
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    scope.launch(Dispatchers.IO) {
                        runCatching { mycelium.ingestWebRtcFrame(peerId, bytes) }
                    }
                }
            },
        )
    }

    suspend fun sendFrame(peerId: String, frame: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val dc = sessions[peerId]?.dc ?: return@withContext false
            if (dc.state() != DataChannel.State.OPEN) return@withContext false
            val buf = ByteBuffer.wrap(frame)
            dc.send(DataChannel.Buffer(buf, true))
        }

    private open inner class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) {
            Log.w(tag, "SDP create failed: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.w(tag, "SDP set failed: $error")
        }
    }

    companion object {
        private const val TICK_MS = 400L
        private const val DATA_CHANNEL_LABEL = "mycelium-mesh"
    }
}
