package com.prasoft.deskguard.ui.components

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prasoft.deskguard.radar.RadarStatus
import com.prasoft.deskguard.ui.theme.CyberAmber
import com.prasoft.deskguard.ui.theme.CyberBackground
import com.prasoft.deskguard.ui.theme.CyberBorder
import com.prasoft.deskguard.ui.theme.CyberCrimson
import com.prasoft.deskguard.ui.theme.CyberCyan
import com.prasoft.deskguard.ui.theme.CyberEmerald
import com.prasoft.deskguard.ui.theme.CyberSurface
import com.prasoft.deskguard.ui.theme.CyberTextSecondary
import kotlin.math.cos
import kotlin.math.sin

// Compatibility aliases
val CyberGreen = CyberEmerald
val CyberRed = CyberCrimson
val CyberBlue = CyberCyan
val CyberDark = CyberBackground

@Composable
fun CircularRadarScanner(
    status: RadarStatus,
    currentRssi: Int,
    delta: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweepTransition")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val ambientGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientGlow"
    )

    val primaryColor = when (status) {
        RadarStatus.MOTION_DETECTED -> CyberCrimson
        RadarStatus.CALIBRATING -> CyberCyan
        RadarStatus.ARMED_MONITORING -> CyberEmerald
        RadarStatus.PHONE_MOVING -> CyberAmber
        else -> CyberBorder
    }

    Box(
        modifier = modifier
            .size(260.dp)
            .clip(CircleShape)
            .background(CyberSurface)
            .border(1.5.dp, primaryColor.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f

            // Outer compass tick marks
            val totalTicks = 36
            for (i in 0 until totalTicks) {
                val angleDeg = i * (360f / totalTicks)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val isMajor = i % 9 == 0
                val tickLength = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                val tickColor = if (isMajor) primaryColor.copy(alpha = 0.7f) else primaryColor.copy(alpha = 0.25f)

                val startX = center.x + (maxRadius - tickLength) * cos(angleRad).toFloat()
                val startY = center.y + (maxRadius - tickLength) * sin(angleRad).toFloat()
                val endX = center.x + maxRadius * cos(angleRad).toFloat()
                val endY = center.y + maxRadius * sin(angleRad).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Concentric range rings
            val ringCount = 4
            for (i in 1..ringCount) {
                val radius = (maxRadius - 12.dp.toPx()) * (i.toFloat() / ringCount)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.12f),
                    radius = radius,
                    center = center,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = if (i == 2 || i == 4) PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) else null
                    )
                )
            }

            // Crosshair lines
            drawLine(
                color = primaryColor.copy(alpha = 0.18f),
                start = Offset(center.x, 12.dp.toPx()),
                end = Offset(center.x, size.height - 12.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.18f),
                start = Offset(12.dp.toPx(), center.y),
                end = Offset(size.width - 12.dp.toPx(), center.y),
                strokeWidth = 1.dp.toPx()
            )

            // Alert ripple effect
            if (status == RadarStatus.MOTION_DETECTED) {
                drawCircle(
                    color = CyberCrimson.copy(alpha = (1.4f - pulseScale).coerceIn(0.1f, 0.7f)),
                    radius = (maxRadius * 0.75f) * pulseScale,
                    center = center,
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }

            // Real Phosphor Sweep Trail
            val isSweeping = status == RadarStatus.ARMED_MONITORING ||
                    status == RadarStatus.CALIBRATING ||
                    status == RadarStatus.MOTION_DETECTED

            if (isSweeping) {
                val arcRadius = maxRadius - 12.dp.toPx()
                val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)

                // Draw 6 slices of fading trail
                val trailSteps = 8
                val trailAngle = 55f
                for (s in 0 until trailSteps) {
                    val stepFraction = s.toFloat() / trailSteps
                    val start = sweepAngle - trailAngle + (s * (trailAngle / trailSteps))
                    val sliceAlpha = (stepFraction * stepFraction * 0.38f)
                    drawArc(
                        color = primaryColor.copy(alpha = sliceAlpha),
                        startAngle = start,
                        sweepAngle = trailAngle / trailSteps + 0.5f,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize
                    )
                }

                // Rotating leading beam line
                val rad = Math.toRadians(sweepAngle.toDouble())
                val beamEnd = Offset(
                    center.x + arcRadius * cos(rad).toFloat(),
                    center.y + arcRadius * sin(rad).toFloat()
                )

                drawLine(
                    color = primaryColor,
                    start = center,
                    end = beamEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Glowing tip beacon
                drawCircle(
                    color = primaryColor,
                    radius = 3.5.dp.toPx(),
                    center = beamEnd
                )
            }
        }

        // Center HUD Telemetry Reticle
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(CyberBackground.copy(alpha = 0.92f))
                .border(1.5.dp, primaryColor.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                RadarStatus.MOTION_DETECTED -> {
                    Text(
                        text = "ALERT",
                        color = CyberCrimson,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                RadarStatus.CALIBRATING -> {
                    Text(
                        text = "CALIB",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                RadarStatus.PHONE_MOVING -> {
                    Text(
                        text = "STILL!",
                        color = CyberAmber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                else -> {
                    Text(
                        text = "$currentRssi dBm",
                        color = primaryColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun SignalOscilloscope(
    signalHistory: List<Float>,
    baseline: Float,
    status: RadarStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ProbeDotPulse")
    val probePulse by infiniteTransition.animateFloat(
        initialValue = 3.5f,
        targetValue = 6.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ProbePulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(145.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CyberSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            // Gridlines & Calibrated dBm labels
            val dbmLabels = listOf("-40 dBm", "-60 dBm", "-80 dBm")
            for (i in 1..3) {
                val y = height * (i.toFloat() / 4)
                drawLine(
                    color = Color(0xFF1E2633),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }

            if (signalHistory.size < 2) return@Canvas

            val minRssi = -90f
            val maxRssi = -35f
            fun mapRssiToY(rssi: Float): Float {
                val normalized = (rssi.coerceIn(minRssi, maxRssi) - minRssi) / (maxRssi - minRssi)
                return height - (normalized * height)
            }

            // Baseline reference guide
            if (baseline != 0f) {
                val baselineY = mapRssiToY(baseline)
                drawLine(
                    color = CyberCyan.copy(alpha = 0.55f),
                    start = Offset(0f, baselineY),
                    end = Offset(width, baselineY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
                    cap = StrokeCap.Round
                )
            }

            // Smooth cubic bezier spline
            val stepX = width / (signalHistory.size - 1)
            val strokePath = Path()
            val fillPath = Path()

            strokePath.moveTo(0f, mapRssiToY(signalHistory[0]))
            fillPath.moveTo(0f, height)
            fillPath.lineTo(0f, mapRssiToY(signalHistory[0]))

            for (i in 0 until signalHistory.size - 1) {
                val x0 = i * stepX
                val y0 = mapRssiToY(signalHistory[i])
                val x1 = (i + 1) * stepX
                val y1 = mapRssiToY(signalHistory[i + 1])

                val cx = (x0 + x1) / 2f
                strokePath.cubicTo(cx, y0, cx, y1, x1, y1)
                fillPath.cubicTo(cx, y0, cx, y1, x1, y1)
            }

            val lastX = (signalHistory.size - 1) * stepX
            val lastY = mapRssiToY(signalHistory.last())
            fillPath.lineTo(lastX, height)
            fillPath.close()

            val strokeColor = if (status == RadarStatus.MOTION_DETECTED) CyberCrimson else CyberEmerald

            // Draw glowing gradient fill under wave
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(strokeColor.copy(alpha = 0.32f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw main wave stroke
            drawPath(
                path = strokePath,
                color = strokeColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Glowing Leading Probe Dot on latest point
            drawCircle(
                color = strokeColor.copy(alpha = 0.35f),
                radius = (probePulse * 1.8f).dp.toPx(),
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }

        // Header overlay
        Text(
            text = "RF MULTIPATH OSCILLOSCOPE",
            color = CyberTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
        )
    }
}
