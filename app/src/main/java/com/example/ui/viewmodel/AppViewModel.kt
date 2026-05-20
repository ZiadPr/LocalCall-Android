package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CallMessage
import com.example.data.model.User
import com.example.data.repository.ProfileRepository
import com.example.network.DiscoveryService
import com.example.network.SignalingService
import com.example.network.WebRTCManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.webrtc.IceCandidate

enum class CallState {
    IDLE, INCOMING, OUTGOING, CONNECTED
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "AppViewModel"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val context = application.applicationContext
    private val profileRepository = ProfileRepository(context)
    private val discoveryService = DiscoveryService(context)
    private val signalingService = SignalingService()
    
    private var webRtcManager: WebRTCManager? = null

    private val _isProfileSaved = MutableStateFlow<Boolean?>(null)
    val isProfileSaved = _isProfileSaved.asStateFlow()

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _userId = MutableStateFlow("")
    val userId = _userId.asStateFlow()

    private val _localIp = MutableStateFlow("0.0.0.0")
    val localIp = _localIp.asStateFlow()

    val discoveredUsers: StateFlow<List<User>> = discoveryService.discoveredUsers

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState = _callState.asStateFlow()

    private val _activeCallUser = MutableStateFlow<User?>(null)
    val activeCallUser = _activeCallUser.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn = _isSpeakerOn.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds = _callDurationSeconds.asStateFlow()

    private val _isRemoteSpeaking = MutableStateFlow(false)
    val isRemoteSpeaking = _isRemoteSpeaking.asStateFlow()

    private var durationTimerJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            if (profile != null) {
                _userId.value = profile.first
                _username.value = profile.second
                _isProfileSaved.value = true
                startNetworkServices(profile.first, profile.second)
            } else {
                _isProfileSaved.value = false
            }
        }

        signalingService.messageListener = { message, senderIp ->
            handleSignalingMessage(message, senderIp)
        }
    }

    private fun startNetworkServices(id: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = discoveryService.getLocalIpAddress() ?: "127.0.0.1"
            _localIp.value = ip
            discoveryService.start(id, name)
            signalingService.start()
            Log.d(TAG, "Network services started. IP: $ip")
        }
    }

    fun saveProfile(name: String) {
        viewModelScope.launch {
            val id = profileRepository.saveProfile(name)
            _userId.value = id
            _username.value = name
            _isProfileSaved.value = true
            startNetworkServices(id, name)
        }
    }

    fun initiateCall(peer: User) {
        if (_callState.value != CallState.IDLE) return
        _callState.value = CallState.OUTGOING
        _activeCallUser.value = peer

        _isMuted.value = false
        _isSpeakerOn.value = false

        val request = CallMessage(
            type = "CALL_REQUEST",
            userId = _userId.value,
            username = _username.value
        )
        signalingService.sendMessage(peer.ip, request) { success ->
            if (!success) {
                viewModelScope.launch(Dispatchers.Main) {
                    resetToIdle()
                }
            }
        }

        startTimeoutTimer()
    }

    fun acceptCall() {
        val peer = _activeCallUser.value ?: return
        if (_callState.value != CallState.INCOMING) return

        stopTimeoutTimer()
        _callState.value = CallState.CONNECTED

        val acceptMsg = CallMessage(
            type = "CALL_ACCEPT",
            userId = _userId.value,
            username = _username.value
        )
        signalingService.sendMessage(peer.ip, acceptMsg)

        initializeWebRTC(peer.ip)

        startCallDurationTimer()
    }

    fun declineCall() {
        val peer = _activeCallUser.value
        if (peer != null) {
            val endMsg = CallMessage(
                type = "CALL_REJECT",
                userId = _userId.value,
                username = _username.value
            )
            signalingService.sendMessage(peer.ip, endMsg)
        }
        resetToIdle()
    }

    fun endCall() {
        val peer = _activeCallUser.value
        if (peer != null) {
            val endMsg = CallMessage(
                type = "CALL_END",
                userId = _userId.value,
                username = _username.value
            )
            signalingService.sendMessage(peer.ip, endMsg)
        }
        resetToIdle()
    }

    fun toggleMute() {
        val nextState = !_isMuted.value
        _isMuted.value = nextState
        webRtcManager?.setMute(nextState)
    }

    fun toggleSpeaker() {
        val nextState = !_isSpeakerOn.value
        _isSpeakerOn.value = nextState
        webRtcManager?.setSpeakerphone(nextState)
    }

    private fun handleSignalingMessage(message: CallMessage, senderIp: String) {
        viewModelScope.launch(Dispatchers.Main) {
            when (message.type) {
                "CALL_REQUEST" -> {
                    if (_callState.value == CallState.IDLE) {
                        _activeCallUser.value = User(
                            userId = message.userId,
                            username = message.username,
                            ip = senderIp,
                            port = 55001
                        )
                        _callState.value = CallState.INCOMING
                        startTimeoutTimer()
                    } else {
                        val busyMsg = CallMessage(
                            type = "CALL_REJECT",
                            userId = _userId.value,
                            username = _username.value,
                            sdp = "BUSY"
                        )
                        signalingService.sendMessage(senderIp, busyMsg)
                    }
                }
                "CALL_ACCEPT" -> {
                    if (_callState.value == CallState.OUTGOING) {
                        stopTimeoutTimer()
                        _callState.value = CallState.CONNECTED
                        
                        initializeWebRTC(senderIp)
                        
                        webRtcManager?.startCall { offerSdp ->
                            val sdpMsg = CallMessage(
                                type = "WEBRTC_OFFER",
                                userId = _userId.value,
                                username = _username.value,
                                sdp = offerSdp.description
                            )
                            signalingService.sendMessage(senderIp, sdpMsg)
                        }
                        
                        startCallDurationTimer()
                    }
                }
                "CALL_REJECT" -> {
                    resetToIdle()
                }
                "CALL_END" -> {
                    resetToIdle()
                }
                "WEBRTC_OFFER" -> {
                    if (_callState.value == CallState.CONNECTED && message.sdp != null) {
                        webRtcManager?.handleOffer(message.sdp) { answerSdp ->
                            val answerMsg = CallMessage(
                                type = "WEBRTC_ANSWER",
                                userId = _userId.value,
                                username = _username.value,
                                sdp = answerSdp.description
                            )
                            signalingService.sendMessage(senderIp, answerMsg)
                        }
                    }
                }
                "WEBRTC_ANSWER" -> {
                    if (_callState.value == CallState.CONNECTED && message.sdp != null) {
                        webRtcManager?.handleAnswer(message.sdp)
                    }
                }
                "ICE_CANDIDATE" -> {
                    if (_callState.value == CallState.CONNECTED && message.candidate != null) {
                        val candidate = IceCandidate(
                            message.sdpMid ?: "",
                            message.sdpMLineIndex ?: 0,
                            message.candidate
                        )
                        webRtcManager?.addIceCandidate(candidate)
                    }
                }
            }
        }
    }

    private fun initializeWebRTC(peerIp: String) {
        webRtcManager?.stopCall()
        webRtcManager = WebRTCManager(
            context = context,
            onIceCandidateGenerated = { candidate ->
                val iceMsg = CallMessage(
                    type = "ICE_CANDIDATE",
                    userId = _userId.value,
                    username = _username.value,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
                signalingService.sendMessage(peerIp, iceMsg)
            },
            onRemoteStreamAdded = {
                viewModelScope.launch(Dispatchers.Main) {
                    _isRemoteSpeaking.value = true
                }
            }
        )
    }

    private fun startTimeoutTimer() {
        stopTimeoutTimer()
        timeoutJob = viewModelScope.launch(Dispatchers.Main) {
            delay(TIMEOUT_SECONDS * 1000)
            if (_callState.value == CallState.INCOMING) {
                declineCall()
            } else if (_callState.value == CallState.OUTGOING) {
                resetToIdle()
            }
        }
    }

    private fun stopTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun startCallDurationTimer() {
        stopCallDurationTimer()
        _callDurationSeconds.value = 0
        durationTimerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && _callState.value == CallState.CONNECTED) {
                delay(1000)
                _callDurationSeconds.value += 1
                _isRemoteSpeaking.value = !_isRemoteSpeaking.value
            }
        }
    }

    private fun stopCallDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = null
        _callDurationSeconds.value = 0
        _isRemoteSpeaking.value = false
    }

    private fun resetToIdle() {
        stopTimeoutTimer()
        stopCallDurationTimer()
        
        webRtcManager?.stopCall()
        webRtcManager = null

        _callState.value = CallState.IDLE
        _activeCallUser.value = null
        _isMuted.value = false
        _isSpeakerOn.value = false
    }

    override fun onCleared() {
        super.onCleared()
        discoveryService.stop(_userId.value, _username.value)
        signalingService.stop()
        resetToIdle()
    }
}
