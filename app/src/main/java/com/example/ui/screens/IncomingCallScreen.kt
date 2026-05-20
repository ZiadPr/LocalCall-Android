package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
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
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DeclineRed
import com.example.ui.theme.TextGray

@Composable
fun IncomingCallScreen(
    callerName: String,
    callerIp: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // Continuous expanding pulse circular animation rings
    val transition = rememberInfiniteTransition(label = "ring_pulse")
    
    val ring1Scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Scale"
    )
    val ring1Alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Alpha"
    )

    val ring2Scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Scale"
    )
    val ring2Alpha by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App Name and Incoming Label
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
                text = "INCOMING VOICE CALL",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextGray,
                letterSpacing = 1.sp
            )
        }

        // Concentric Breathing Pulse and Avatar Component
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulse Ring 1
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(ring1Scale)
                    .border(2.dp, AccentTeal.copy(alpha = ring1Alpha), CircleShape)
            )

            // Pulse Ring 2
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(ring2Scale)
                    .border(2.dp, AccentTeal.copy(alpha = ring2Alpha), CircleShape)
            )

            // Central Avatar Card
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color(0xFF1E293B), CircleShape)
                    .border(4.dp, AccentTeal, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = callerName.take(1).uppercase(),
                    color = AccentTeal,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // User Profile & IP Details
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = callerName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "IP: $callerIp",
                fontSize = 14.sp,
                color = TextGray
            )
        }

        // Action Toggles (Accept & Decline with Touch Target >= 48dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline Button (Red Circle)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(DeclineRed)
                        .clickable { onDecline() }
                        .testTag("decline_call_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Decline Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Decline",
                    color = TextGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Accept Button (Green Circle)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AccentTeal)
                        .clickable { onAccept() }
                        .testTag("accept_call_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Accept Call",
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = "Accept",
                    color = AccentTeal,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
