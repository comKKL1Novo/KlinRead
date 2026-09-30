package com.klin.read.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours for the slider, so it can be tinted for a specific surface. */
data class SliderColors(
    val track: Color,
    val active: Color,
    val thumb: Color
)

/**
 * A slider with a round thumb.
 *
 * Material's slider draws a capsule thumb, which does not match the flat design,
 * so the track and knob are drawn here.
 *
 * A single `pointerInput` handles the whole gesture. Two earlier attempts failed
 * in a vertically scrolling parent:
 *
 *  - two pointerInput modifiers (drag plus tap) on one node meant the tap
 *    detector consumed the stream before the drag handler saw it;
 *  - reporting the release value as `value` rather than the dragged value meant
 *    release wrote the old setting back, so the thumb sprang back.
 *
 * `onValueChangeFinished` therefore receives the final value.
 */
@Composable
fun RoundSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    colors: SliderColors? = null,
    trackHeight: Dp = 3.dp,
    thumbSize: Dp = 20.dp
) {
    val c = LocalColors.current
    val resolved = colors ?: SliderColors(
        track = c.surfaceMuted,
        active = c.accent,
        thumb = c.accent
    )
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    // The value under the finger, so release can report it even though the
    // caller's `value` has not caught up yet.
    var draggedValue by remember { mutableStateOf(value) }
    val current = draggedValue

    // Tracked in pixels so the thumb can be positioned without a dp round-trip.
    var trackWidthPx by remember { mutableIntStateOf(0) }
    val thumbPx = with(LocalDensity.current) { thumbSize.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 48dp is the minimum comfortable touch target. The visual track is a
            // few dp tall, so without this the slider is almost impossible to grab.
            .height(48.dp)
            .onSizeChanged { trackWidthPx = it.width }
            // A single gesture modifier handles the whole interaction.
            //
            // Two earlier versions were wrong in ways that were hard to see on the
            // device: attaching a drag detector and a tap detector as two separate
            // `pointerInput` modifiers meant the second consumed the stream before
            // the first saw it; and reporting the release value as the caller's
            // `value` (which lags behind the drag) made the thumb spring back.
            //
            // Handling the initial down event here also means a plain tap jumps
            // the thumb instead of doing nothing.
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                val travel = { (size.width - thumbPx).coerceAtLeast(1f) }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    draggedValue = valueAt(down.position.x, travel(), valueRange)
                    onValueChange(draggedValue)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        // Consume so the enclosing list does not steal the drag.
                        change.consume()
                        draggedValue = valueAt(change.position.x, travel(), valueRange)
                        onValueChange(draggedValue)
                    }
                    // Report the value actually dragged to, so the caller persists
                    // that rather than the stale stored one.
                    onValueChangeFinished?.invoke(draggedValue)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Unfilled track.
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(resolved.track)
        )
        // Filled portion.
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(resolved.active)
        )
        // Round thumb, positioned in pixels: converting to dp here would misplace
        // it at any density other than 1x.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val travel = (trackWidthPx - placeable.width).coerceAtLeast(0)
                    // Follow the value under the finger while dragging; fall back
                    // to the caller's value otherwise.
                    val shown = ((current - valueRange.start) / span).coerceIn(0f, 1f)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative((travel * shown).toInt(), 0)
                    }
                }
                .size(thumbSize)
                .clip(CircleShape)
                .background(resolved.thumb)
        )
    }
}

/**
 * Maps a touch x-coordinate to a value.
 *
 * The usable travel excludes the thumb's own width so the knob stays fully on the
 * track at both ends instead of hanging off the edge.
 */
private fun valueAt(
    x: Float,
    travel: Float,
    range: ClosedFloatingPointRange<Float>
): Float {
    val fraction = (x / travel).coerceIn(0f, 1f)
    return range.start + fraction * (range.endInclusive - range.start)
}

/**
 * Labelled slider row used by the settings screens.
 *
 * [onValueChange] fires continuously so the caller can preview the effect live;
 * [onValueChangeFinished] fires once on release for the final commit.
 */
@Composable
fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    Column(modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = c.ink, fontSize = 15.sp)
            Text(valueLabel, color = c.inkMuted, fontSize = 13.sp)
        }
        RoundSlider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}
