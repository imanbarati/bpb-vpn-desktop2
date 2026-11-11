package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VpnStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RedDisconnect
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ConnectionButton(
    status: VpnStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    val primaryColor = when (status) {
        VpnStatus.CONNECTED -> EmeraldConnected
        VpnStatus.CONNECTING -> AmberWarning
        VpnStatus.DISCONNECTING -> RedDisconnect
        VpnStatus.ERROR -> RedDisconnect
        VpnStatus.DISCONNECTED -> CyanPrimary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(190.dp)
    ) {
        // Outer animated glowing rings for active or connecting states
        if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
            Canvas(modifier = Modifier.size(185.dp * pulseScale)) {
                drawCircle(
                    color = primaryColor.copy(alpha = pulseAlpha),
                    radius = size.minDimension / 2
                )
            }
        }

        // Secondary subtle outer border
        Canvas(modifier = Modifier.size(170.dp)) {
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.6f),
                        primaryColor.copy(alpha = 0.1f),
                        primaryColor.copy(alpha = 0.7f)
                    )
                ),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Core Button Circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(145.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            CyberSurfaceVariant,
                            Color(0xFF0C1425)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    color = primaryColor.copy(alpha = 0.8f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = primaryColor),
                    onClick = onClick
                )
                .testTag("vpn_power_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (status) {
                    VpnStatus.CONNECTED -> {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Connected",
                            tint = EmeraldConnected,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "CONNECTED",
                            color = EmeraldConnected,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                    VpnStatus.CONNECTING -> {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Connecting",
                            tint = AmberWarning,
                            modifier = Modifier
                                .size(38.dp)
                                .rotate(rotateAngle)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "CONNECTING",
                            color = AmberWarning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    VpnStatus.DISCONNECTING -> {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Disconnecting",
                            tint = RedDisconnect,
                            modifier = Modifier
                                .size(38.dp)
                                .rotate(rotateAngle)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "STOPPING",
                            color = RedDisconnect,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    VpnStatus.ERROR -> {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Error",
                            tint = RedDisconnect,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "RETRY",
                            color = RedDisconnect,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                    VpnStatus.DISCONNECTED -> {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Disconnected",
                            tint = CyanPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "CONNECT",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "TAP TO START",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
