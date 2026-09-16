package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioRecorderManager
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

/**
 * Custom Composable component that draws the real-time audio waveform by consuming
 * the amplitude flow directly from the [AudioRecorderManager].
 *
 * Renders an animated, scrolling multi-colored soundwave with a glowing live recording cursor,
 * horizontal baseline guide, and smooth amplitude interpolation.
 */
@Composable
fun RealtimeAudioWaveform(
    recordingManager: AudioRecorderManager,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    barWidth: Dp = 4.dp,
    barSpacing: Dp = 3.dp,
    minBarHeight: Dp = 6.dp,
    showCursor: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    cursorColor: Color = MaterialTheme.colorScheme.primary,
    accentColors: List<Color> = listOf(
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary
    )
) {
    RealtimeAudioWaveform(
        amplitudeFlow = recordingManager.amplitudeFlow,
        modifier = modifier,
        isPaused = isPaused,
        barWidth = barWidth,
        barSpacing = barSpacing,
        minBarHeight = minBarHeight,
        showCursor = showCursor,
        primaryColor = primaryColor,
        cursorColor = cursorColor,
        accentColors = accentColors
    )
}

/**
 * Overload of [RealtimeAudioWaveform] that consumes any [Flow] of amplitude lists,
 * enabling flexible consumption from ViewModel StateFlows, Repositories, or Managers.
 */
@Composable
fun RealtimeAudioWaveform(
    amplitudeFlow: Flow<List<Float>>,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    barWidth: Dp = 4.dp,
    barSpacing: Dp = 3.dp,
    minBarHeight: Dp = 6.dp,
    showCursor: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    cursorColor: Color = MaterialTheme.colorScheme.primary,
    accentColors: List<Color> = listOf(
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary
    )
) {
    // Consume amplitude flow as Compose state with lifecycle awareness
    val amplitudes by amplitudeFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Ambient pulse effect for live recording head and low-energy baseline bars
    val ambientPulse = if (!isPaused) {
        val infiniteTransition = rememberInfiniteTransition(label = "realtime_waveform_pulse")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambient_pulse"
        )
        pulse
    } else {
        1.0f
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .testTag("realtime_audio_waveform")
            .semantics {
                contentDescription = if (isPaused) {
                    "Real-time audio waveform paused"
                } else {
                    "Live audio waveform visualizer"
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            val centerY = height / 2f

            // Baseline guide line across canvas center
            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.2f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            val barWidthPx = barWidth.toPx()
            val barSpacingPx = barSpacing.toPx()
            val totalBarStep = barWidthPx + barSpacingPx
            val minBarHeightPx = minBarHeight.toPx()

            // Calculate how many bars fit horizontally on the screen
            val cursorMargin = if (showCursor) 16.dp.toPx() else 0f
            val drawableWidth = width - cursorMargin
            val maxVisibleBars = (drawableWidth / totalBarStep).toInt().coerceAtLeast(1)

            val ampCount = amplitudes.size
            val padCount = if (ampCount < maxVisibleBars) maxVisibleBars - ampCount else 0
            val ampOffset = if (ampCount >= maxVisibleBars) ampCount - maxVisibleBars else 0

            // Draw each amplitude bar centered vertically without temporary list allocation
            val colorCount = accentColors.size.coerceAtLeast(1)
            for (index in 0 until maxVisibleBars) {
                val x = index * totalBarStep
                if (x + barWidthPx > drawableWidth && showCursor) break

                val rawAmp = if (index < padCount) {
                    0.05f
                } else {
                    val srcIdx = ampOffset + (index - padCount)
                    if (srcIdx in 0 until ampCount) amplitudes[srcIdx] else 0.05f
                }

                val amp = if (isPaused) {
                    rawAmp * 0.45f
                } else {
                    (rawAmp * if (rawAmp < 0.15f) ambientPulse else 1f).coerceIn(0.04f, 1f)
                }

                // Vertical symmetric bar around center
                val barHeight = max(minBarHeightPx, amp * (height * 0.82f))
                val top = centerY - (barHeight / 2f)

                // Multi-color palette assignment
                val barColor = if (isPaused) {
                    onSurfaceVariant.copy(alpha = 0.4f)
                } else {
                    when {
                        index % 11 == 0 -> accentColors[1 % colorCount]
                        index % 7 == 0 -> accentColors[2 % colorCount]
                        index % 5 == 0 -> accentColors[0 % colorCount]
                        else -> primaryColor
                    }
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, top),
                    size = Size(barWidthPx, barHeight),
                    cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
                )
            }

            // Live recording playhead / cursor at the leading edge
            if (showCursor) {
                val cursorX = width - (10.dp.toPx())

                // Vertical cursor line
                drawLine(
                    color = cursorColor,
                    start = Offset(cursorX, 10.dp.toPx()),
                    end = Offset(cursorX, height - 10.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                if (!isPaused) {
                    // Outer glowing pulsing halo
                    drawCircle(
                        color = cursorColor.copy(alpha = 0.35f),
                        radius = (9.dp * ambientPulse).toPx(),
                        center = Offset(cursorX, centerY)
                    )
                }

                // Inner solid cursor bead
                drawCircle(
                    color = cursorColor,
                    radius = 5.5.dp.toPx(),
                    center = Offset(cursorX, centerY)
                )
            }
        }
    }
}
