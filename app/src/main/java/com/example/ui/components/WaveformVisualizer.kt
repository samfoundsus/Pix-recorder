package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Live dynamic waveform during active recording, scrolling horizontally with vibrant Pixel colors.
 */
@Composable
fun LiveRecordingWaveform(
    amplitudes: List<Float>,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val ambientPulse = if (!isPaused) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambient"
        )
        pulse
    } else 1.0f

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas
            val centerY = height / 2f

            // Baseline guide line
            drawLine(
                color = onSurfaceVariantColor.copy(alpha = 0.2f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )

            val barWidth = 4.dp.toPx()
            val barSpacing = 3.dp.toPx()
            val totalBarStep = barWidth + barSpacing
            val maxBars = (width / totalBarStep).toInt().coerceAtLeast(1)

            val ampCount = amplitudes.size
            val padCount = if (ampCount < maxBars) maxBars - ampCount else 0
            val ampOffset = if (ampCount >= maxBars) ampCount - maxBars else 0

            for (index in 0 until maxBars) {
                val x = index * totalBarStep
                val rawAmp = if (index < padCount) {
                    0.05f
                } else {
                    val srcIdx = ampOffset + (index - padCount)
                    if (srcIdx in 0 until ampCount) amplitudes[srcIdx] else 0.05f
                }

                val amp = if (isPaused) rawAmp * 0.5f else (rawAmp * if (rawAmp < 0.15f) ambientPulse else 1f).coerceIn(0.04f, 1f)
                val barHeight = max(6.dp.toPx(), amp * (height * 0.82f))
                val top = centerY - barHeight / 2f

                // Pixel multi-color waveform gradient
                val barColor = when {
                    isPaused -> onSurfaceVariantColor.copy(alpha = 0.4f)
                    index % 7 == 0 -> tertiaryColor
                    index % 11 == 0 -> secondaryColor
                    else -> primaryColor
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }

            // Signature center record cursor line with soft glow and knob
            val cursorX = width - (12.dp.toPx())
            drawLine(
                color = primaryColor,
                start = Offset(cursorX, 8f),
                end = Offset(cursorX, height - 8f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            if (!isPaused) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    radius = (8.dp * ambientPulse).toPx(),
                    center = Offset(cursorX, centerY)
                )
            }
            drawCircle(
                color = primaryColor,
                radius = 5.5.dp.toPx(),
                center = Offset(cursorX, centerY)
            )
            drawCircle(
                color = onPrimaryColor,
                radius = 2.2.dp.toPx(),
                center = Offset(cursorX, centerY)
            )
        }
    }
}

/**
 * Interactive scrubbable waveform for playback with played/unplayed segments and scrubber handle.
 */
@Composable
fun PlaybackWaveform(
    amplitudes: List<Float>,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false
) {
    val progress = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val safeAmps: List<Float> = remember(amplitudes) {
        if (amplitudes.isEmpty()) {
            List(56) { 0.25f }
        } else amplitudes
    }

    val glowPulse = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "playback_glow")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow"
        )
        pulse
    } else 1.0f

    val barCount = 56
    val sampled: List<Float> = remember(safeAmps, barCount) { resampleAmplitudes(safeAmps, barCount) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val unplayedColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(totalDurationMs) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((frac * totalDurationMs).toLong())
                }
            }
            .pointerInput(totalDurationMs) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek((frac * totalDurationMs).toLong())
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            // Baseline guide
            drawLine(
                color = onSurfaceVariantColor.copy(alpha = 0.2f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1.dp.toPx()
            )

            val barWidth = 3.5.dp.toPx()
            val availableSpace = width - (barCount * barWidth)
            val barGap = max(1.5f, availableSpace / (barCount - 1))

            val scrubX = (width * progress).coerceIn(0f, width)

            for (i in 0 until barCount) {
                val x = i * (barWidth + barGap)
                val baseAmp = sampled[i].coerceIn(0.1f, 1.0f)
                val isNearCursor = isPlaying && kotlin.math.abs(x - scrubX) < (barWidth * 3f)
                val amp = if (isNearCursor) (baseAmp * glowPulse).coerceIn(0.1f, 1.0f) else baseAmp

                val barHeight = max(8.dp.toPx(), amp * (height * 0.76f))
                val top = centerY - barHeight / 2f

                val isPlayed = (x + barWidth / 2f) <= scrubX
                val color = if (isPlayed) {
                    when {
                        i % 9 == 0 -> secondaryColor
                        i % 13 == 0 -> tertiaryColor
                        else -> primaryColor
                    }
                } else {
                    unplayedColor
                }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }

            // Scrubber Cursor Line
            drawLine(
                color = onSurfaceColor,
                start = Offset(scrubX, 4f),
                end = Offset(scrubX, height - 4f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Scrubber Handle Knob with ripple glow when playing
            if (isPlaying) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.25f),
                    radius = (8.dp * glowPulse).toPx(),
                    center = Offset(scrubX, centerY)
                )
            }

            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(scrubX, centerY)
            )
            drawCircle(
                color = onPrimaryColor,
                radius = 2.5.dp.toPx(),
                center = Offset(scrubX, centerY)
            )
        }
    }
}

/**
 * Compact mini waveform preview for list items with dynamic Material 3 colors,
 * natural speech envelope variations, and animated wave motion during playback.
 */
@Composable
fun MiniWaveformPreview(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    isPlaying: Boolean = false
) {
    val barCount = 28
    val safeAmps: List<Float> = remember(amplitudes) {
        if (amplitudes.isEmpty() || amplitudes.all { it <= 0.05f }) {
            // Generate an authentic speech envelope with realistic vocal variations and pauses
            List(barCount) { i ->
                val norm = i.toFloat() / barCount
                val wave1 = kotlin.math.sin(norm * Math.PI.toFloat() * 3.5f) * 0.32f
                val wave2 = kotlin.math.sin(norm * Math.PI.toFloat() * 7.5f) * 0.22f
                val dip = if (i in 7..10 || i in 19..21) 0.16f else 0.52f
                (dip + wave1 + wave2).coerceIn(0.12f, 0.95f)
            }
        } else {
            amplitudes
        }
    }
    val sampled: List<Float> = remember(safeAmps) { resampleAmplitudes(safeAmps, barCount) }

    val wavePhase = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "mini_wave")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
        phase
    } else 0f

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        val centerY = height / 2f

        // Subtle baseline center line
        drawLine(
            color = barColor.copy(alpha = if (isPlaying) 0.22f else 0.12f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )

        val barWidth = 2.5.dp.toPx()
        val gap = (width - (barCount * barWidth)) / (barCount - 1).coerceAtLeast(1)

        for (i in 0 until barCount) {
            val x = i * (barWidth + gap)
            val baseAmp = sampled[i].coerceIn(0.10f, 1f)
            val amp = if (isPlaying) {
                val waveMod = (kotlin.math.sin(wavePhase + i * 0.45f) * 0.35f + 0.85f).coerceIn(0.35f, 1.3f)
                (baseAmp * waveMod).coerceIn(0.14f, 1f)
            } else baseAmp

            val barHeight = max(4.dp.toPx(), amp * (height * 0.85f))
            val top = centerY - barHeight / 2f

            val alpha = if (isPlaying) 0.95f else 0.50f

            drawRoundRect(
                color = barColor.copy(alpha = alpha),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

private fun resampleAmplitudes(source: List<Float>, targetCount: Int): List<Float> {
    if (source.isEmpty()) return List(targetCount) { 0.2f }
    if (source.size == targetCount) return source

    val result = mutableListOf<Float>()
    val step = source.size.toFloat() / targetCount.toFloat()

    for (i in 0 until targetCount) {
        val srcIndex = (i * step).toInt().coerceIn(0, source.lastIndex)
        result.add(source[srcIndex])
    }
    return result
}
