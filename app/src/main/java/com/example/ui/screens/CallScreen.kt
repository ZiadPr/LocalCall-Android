package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun CallScreen(
    peerName: String,
    peerIp: String,
    isConnecting: Boolean, // Outgoing call is awaiting answer
    durationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isSpeaking: Boolean, // Speaking animation state linked to stream callbacks
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onEndCall: () -> Unit
) {
    // Dynamic speaking scale animation of the central avatar ring
    val speakingScale by animateFloatAsState(
        targetValue = if (isSpeaking && !isConnecting) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "speakingScale"
    )

    // Glowing breathe animation
    val transition = rememberInfiniteTransition(label = "ring_glow")
    val glowScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper Title / Timer Status Panel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = "⚡ LocalCall",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AccentTeal,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isConnecting) "CONNECTING PEER..." else "SECURE VOICE CHANNEL",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextGray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isConnecting) "Awaiting accept..." else formatDuration(durationSeconds),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConnecting) AccentTeal.copy(alpha = 0.6f) else OnBackgroundDark
            )
        }

        // Concentric Speaking Breath Indicator Layer
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSpeaking && !isConnecting) {
                // Expanding glowing halo indicating active speaking stream
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(glowScale)
                        .background(AccentTeal.copy(alpha = 0.15f), CircleShape)
                )
            }

            // Central avatar with spring speak bouncing representation
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(speakingScale)
                    .background(Color(0xFF1E293B), CircleShape)
                    .border(2.dp, if (isSpeaking && !isConnecting) AccentTeal else GrayBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peerName.take(1).uppercase(),
                    color = AccentTeal,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Active peer metadata details
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = peerName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "IP: $peerIp",
                fontSize = 14.sp,
                color = TextGray
            )
        }

        // Controls Area (Mute, Speaker, and End Call with Touch Target >= 48dp)
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Audio Routing Control Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Mute Control
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) AccentTeal else Color(0xFF1E293B))
                            .clickable { onMuteToggle() }
                            .testTag("mute_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        MicIcon(tint = if (isMuted) Color.Black else Color.White)
                    }
                    Text(
                        text = if (isMuted) "Muted" else "Mute",
                        color = if (isMuted) AccentTeal else TextGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Speaker Route Control
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isSpeakerOn) AccentTeal else Color(0xFF1E293B))
                            .clickable { onSpeakerToggle() }
                            .testTag("speaker_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        SpeakerIcon(tint = if (isSpeakerOn) Color.Black else Color.White)
                    }
                    Text(
                        text = if (isSpeakerOn) "Speaker" else "Earpiece",
                        color = if (isSpeakerOn) AccentTeal else TextGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Central Large Red End Call Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(DeclineRed)
                        .clickable { onEndCall() }
                        .testTag("end_call_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "End Call Session",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "End Call",
                    color = DeclineRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Custom canvas vector Mic Icon drawing
@Composable
fun MicIcon(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        
        // Microphone sound collector body: centered rounded oblong rectangle
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.38f, h * 0.2f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.45f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f, w * 0.12f)
        )
        // Outer arc casing supporting stand grip
        val arcPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, h * 0.42f)
            quadraticTo(w * 0.25f, h * 0.72f, w * 0.5f, h * 0.72f)
            quadraticTo(w * 0.75f, h * 0.72f, w * 0.75f, h * 0.42f)
        }
        drawPath(
            path = arcPath,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        // Vertical supporting line
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.72f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.88f),
            strokeWidth = 2.5.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // Horizontal stable base plate support line
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.88f),
            end = androidx.compose.ui.geometry.Offset(w * 0.7f, h * 0.88f),
            strokeWidth = 2.5.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

// Custom canvas vector Speaker Icon drawing
@Composable
fun SpeakerIcon(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        
        // Custom path representing classical speaker casing shape
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.22f, h * 0.35f)
            lineTo(w * 0.42f, h * 0.35f)
            lineTo(w * 0.65f, h * 0.15f)
            lineTo(w * 0.65f, h * 0.85f)
            lineTo(w * 0.42f, h * 0.65f)
            lineTo(w * 0.22f, h * 0.65f)
            close()
        }
        drawPath(path = path, color = tint)
        
        // Single wave arc representing voice frequency radiation
        val wavePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.78f, h * 0.35f)
            quadraticTo(w * 0.88f, h * 0.5f, w * 0.78f, h * 0.65f)
        }
        drawPath(
            path = wavePath,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

// Simple formatter to format call seconds into elegant presentation: MM:SS
private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
