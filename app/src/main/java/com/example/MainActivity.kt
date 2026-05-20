package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.TextGray
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.CallState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F0F0F) // Explicit Slate #0F0F0F custom dark styling
                ) {
                    MainContent()
                }
            }
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel()

    // Secure audio stream permission check
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasAudioPermission = granted
        }
    )

    // Dynamic State Monitoring Hooks
    val myProfile by viewModel.isProfileSaved.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val localIp by viewModel.localIp.collectAsStateWithLifecycle()
    val peers by viewModel.discoveredUsers.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val activeCallUser by viewModel.activeCallUser.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val callDurationSeconds by viewModel.callDurationSeconds.collectAsStateWithLifecycle()
    val isRemoteSpeaking by viewModel.isRemoteSpeaking.collectAsStateWithLifecycle()

    if (!hasAudioPermission) {
        // Elegant Interactive Permission panel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 450.dp)
                    .background(Color(0xFF1E293B), shape = RoundedCornerShape(24.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(AccentTeal.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    MicIcon(tint = AccentTeal, modifier = Modifier.size(36.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Microphone Needed",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "LocalCall connects audio streams offline over local Wi-Fi. We need microphone access so your peer can hear your voice clearly during calls.",
                    color = TextGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentTeal,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("grant_permission_button")
                ) {
                    Text(
                        text = "Grant Microphone Permission",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        // Conditional routing based on the spec
        if (myProfile == null || myProfile == false) {
            SetupScreen(onSaveProfile = { name ->
                viewModel.saveProfile(name)
            })
        } else {
            // Profile is active and loaded
            when (callState) {
                CallState.IDLE -> {
                    HomeScreen(
                        username = username,
                        localIp = localIp,
                        peers = peers,
                        onInitiateCall = { peer ->
                            viewModel.initiateCall(peer)
                        }
                    )
                }
                CallState.INCOMING -> {
                    val activePeer = activeCallUser
                    if (activePeer != null) {
                        IncomingCallScreen(
                            callerName = activePeer.username,
                            callerIp = activePeer.ip,
                            onAccept = { viewModel.acceptCall() },
                            onDecline = { viewModel.declineCall() }
                        )
                    }
                }
                CallState.OUTGOING, CallState.CONNECTED -> {
                    val activePeer = activeCallUser
                    if (activePeer != null) {
                        CallScreen(
                            peerName = activePeer.username,
                            peerIp = activePeer.ip,
                            isConnecting = (callState == CallState.OUTGOING),
                            durationSeconds = callDurationSeconds,
                            isMuted = isMuted,
                            isSpeakerOn = isSpeakerOn,
                            isSpeaking = isRemoteSpeaking,
                            onMuteToggle = { viewModel.toggleMute() },
                            onSpeakerToggle = { viewModel.toggleSpeaker() },
                            onEndCall = { viewModel.endCall() }
                        )
                    }
                }
            }
        }
    }
}
