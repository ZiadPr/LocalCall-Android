package com.example.network

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.*
import java.util.ArrayList

class WebRTCManager(
    private val context: Context,
    private val onIceCandidateGenerated: (IceCandidate) -> Unit,
    private val onRemoteStreamAdded: () -> Unit
) {
    companion object {
        private const val TAG = "WebRTCManager"
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null
    private var localMediaStream: MediaStream? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
            Log.d(TAG, "PeerConnectionFactory initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PeerConnectionFactory", e)
        }
    }

    fun startCall(onOfferCreated: (SessionDescription) -> Unit) {
        setupPeerConnection()
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set (Offer)")
                            onOfferCreated(desc)
                        }
                    }, desc)
                }
            }
        }, constraints)
    }

    fun handleOffer(offerSdp: String, onAnswerCreated: (SessionDescription) -> Unit) {
        setupPeerConnection()
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set (Offer)")
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if (desc != null) {
                            peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                                override fun onSetSuccess() {
                                    Log.d(TAG, "Local description set (Answer)")
                                    onAnswerCreated(desc)
                                }
                            }, desc)
                        }
                    }
                }, constraints)
            }
        }, sessionDescription)
    }

    fun handleAnswer(answerSdp: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set (Answer)")
            }
        }, sessionDescription)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
        Log.d(TAG, "Added remote ICE candidate")
    }

    private fun setupPeerConnection() {
        if (peerConnection != null) return

        val rtcConfig = PeerConnection.RTCConfiguration(ArrayList<PeerConnection.IceServer>()).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            keyType = PeerConnection.KeyType.ECDSA
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                Log.d(TAG, "onSignalingChange: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "onIceConnectionReceivingChange: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "onIceCandidate: $candidate")
                onIceCandidateGenerated(candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {
                Log.d(TAG, "onIceCandidatesRemoved")
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "onAddStream: ${stream.id}")
                if (stream.audioTracks.isNotEmpty()) {
                    Log.d(TAG, "Remote stream audio track detected")
                    onRemoteStreamAdded()
                }
            }

            override fun onRemoveStream(stream: MediaStream) {
                Log.d(TAG, "onRemoveStream")
            }

            override fun onDataChannel(channel: DataChannel) {
                Log.d(TAG, "onDataChannel")
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded")
            }

            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                Log.d(TAG, "onAddTrack")
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        val audioConstraints = MediaConstraints()
        localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("local_audio_track_id", localAudioSource)
        localAudioTrack?.setEnabled(true)

        localMediaStream = peerConnectionFactory?.createLocalMediaStream("local_stream_id").apply {
            this?.addTrack(localAudioTrack)
        }
        localMediaStream?.let {
            peerConnection?.addStream(it)
        }

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup audio manager mode", e)
        }
    }

    fun setMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
        Log.d(TAG, "Audio muted state: $isMuted")
    }

    fun setSpeakerphone(isEnabled: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = isEnabled
            Log.d(TAG, "Speakerphone set to: $isEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle speakerphone", e)
        }
    }

    fun stopCall() {
        try {
            peerConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing peerConnection", e)
        }
        peerConnection = null

        try {
            localAudioSource?.dispose()
        } catch (e: Exception) {
            // ignore
        }
        localAudioSource = null
        localAudioTrack = null
        localMediaStream = null

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            // ignore
        }
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(reason: String?) {}
    override fun onSetFailure(reason: String?) {}
}
