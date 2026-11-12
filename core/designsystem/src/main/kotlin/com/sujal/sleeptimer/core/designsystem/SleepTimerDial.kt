@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sujal.sleeptimer.core.designsystem

import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SleepTimerDial(
    presentation: DialPresentation,
    onMinutesChange: (Int) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(presentation)
    val change by rememberUpdatedState(onMinutesChange)
    val finish by rememberUpdatedState(onChangeFinished)
    val view = LocalView.current
    var lastEmitted by remember { mutableIntStateOf(presentation.minutes) }
    LaunchedEffect(presentation.minutes) { lastEmitted = presentation.minutes }
    val update: (Int) -> Unit = { minutes ->
        val bounded = minutes.coerceIn(1, MINUTES_PER_TURN)
        if (bounded != lastEmitted) {
            lastEmitted = bounded
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            change(bounded)
        }
    }
    val interactive = Modifier.dialInput(presentation, update, finish)
    Box(
        modifier = modifier.then(interactive).semantics(mergeDescendants = true) {
            contentDescription = presentation.controlLabel
            stateDescription = "${presentation.spokenDuration}. ${presentation.endLabel}"
        },
        contentAlignment = Alignment.Center,
    ) {
        if (presentation.running) {
            val stroke = with(LocalDensity.current) { Stroke(12.dp.toPx(), cap = StrokeCap.Round) }
            val animate = ValueAnimator.areAnimatorsEnabled()
            CircularWavyProgressIndicator(
                progress = { presentation.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize().padding(10.dp).clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
                stroke = stroke,
                trackStroke = stroke,
                amplitude = { if (animate) (it / WAVE_FLATTEN_POINT).coerceIn(0f, 1f) else 0f },
                wavelength = 80.dp,
                waveSpeed = if (animate) 10.dp else 0.dp,
            )
        } else {
            IdleDial(presentation.minutes, update, finish, Modifier.fillMaxSize())
        }
        DialText(presentation)
    }
}

@Composable
private fun IdleDial(minutes: Int, onChange: (Int) -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val value by rememberUpdatedState(minutes)
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.secondaryContainer
    val thumbRadius = with(LocalDensity.current) { 14.dp.toPx() }
    val touchRadius = with(LocalDensity.current) { 28.dp.toPx() }
    val stroke = with(LocalDensity.current) { Stroke(12.dp.toPx(), cap = StrokeCap.Round) }
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) / 2f - thumbRadius
                val angle = value.coerceIn(1, MINUTES_PER_TURN) * DEGREES_PER_MINUTE
                val nearThumb = (down.position - dialPoint(center, radius, angle)).getDistance() <= touchRadius
                if (!nearThumb) return@awaitEachGesture
                down.consume()
                val dragState = DialDrag(value, pointerAngle(down.position, center))
                drag(down.id) { pointer ->
                    pointer.consume()
                    change(dragState.move(pointerAngle(pointer.position, center)))
                }
                finish()
            }
        }.clearAndSetSemantics {},
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - thumbRadius
        val sweep = minutes.coerceIn(1, MINUTES_PER_TURN) * DEGREES_PER_MINUTE
        val origin = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        val trackSweep = (FULL_TURN - sweep - THUMB_GAP - TOP_GAP).coerceAtLeast(0f)
        drawArc(track, -QUARTER_TURN + sweep + THUMB_GAP, trackSweep, false, origin, arcSize, style = stroke)
        val activeSweep = (sweep - TOP_GAP - THUMB_GAP).coerceAtLeast(0f)
        drawArc(primary, -QUARTER_TURN + TOP_GAP, activeSweep, false, origin, arcSize, style = stroke)
        drawCircle(primary, thumbRadius, dialPoint(center, radius, sweep))
    }
}

private fun pointerAngle(point: Offset, center: Offset): Float =
    ((atan2(point.y - center.y, point.x - center.x) * HALF_TURN / PI + QUARTER_TURN + FULL_TURN) % FULL_TURN).toFloat()

private fun dialPoint(center: Offset, radius: Float, angle: Float): Offset {
    val radians = (angle - QUARTER_TURN) * PI / HALF_TURN
    return Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
}

private const val THUMB_GAP = 9f
private const val TOP_GAP = 3f
private const val WAVE_FLATTEN_POINT = 0.03f

@Composable
private fun DialText(presentation: DialPresentation) {
    Column(
        modifier = Modifier.width(230.dp).padding(horizontal = 4.dp).clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (presentation.message != null) {
            Text(
                text = presentation.message,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = presentation.countdown,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(fontSize = 72.sp, fontFeatureSettings = "tnum", letterSpacing = (-2).sp),
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 72.sp, stepSize = 1.sp),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = presentation.endLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val QUARTER_TURN = 90f
