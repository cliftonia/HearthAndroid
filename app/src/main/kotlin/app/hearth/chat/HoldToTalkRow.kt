package app.hearth.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.hearth.R
import app.hearth.designsystem.HearthRoundButton
import app.hearth.designsystem.HearthTheme

// These measures belong to the held composer alone, so they are kept here and not in the theme.

/** How long a finger rests before it counts as holding. Anything shorter is a tap. */
private const val HOLD_DELAY_MILLIS = 300L

/** How far past the field's edge a held finger may wander before it counts as having slid off. */
private val SlideOffAllowance = 24.dp

/** One dot of the row that shows how loud the voice is, at rest. */
private val LevelDotSide = 6.dp

/** How long a dot takes to follow the voice: quick enough to keep up with speech, slow enough that it swells and does not flicker. */
private const val LEVEL_DOT_MILLIS = 150

/** How dim the send arrow is while there is nothing yet to send. */
private const val WAITING_SEND_ALPHA = 0.35f

/**
 * What the composer shows while it is held down and listening: a stop control, what to do, how loud the voice
 * is, and the send arrow waiting its turn. It lies over the composer's own row and takes its touches.
 *
 * @param level How loud the voice is, from 0 to 1.
 * @param onStop Stops the recording, for a finger that would sooner tap than let go.
 */
@Composable
internal fun HoldToTalkRow(level: Double, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    Row(
        // Whatever is touched here is not passed down to the controls hidden beneath.
        modifier.pointerInput(Unit) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        HearthRoundButton(
            icon = Icons.Filled.Stop,
            label = stringResource(R.string.composer_stop_dictation),
            containerColor = colors.pine,
            contentColor = colors.onPine,
            size = HearthTheme.sizes.controlL,
            iconSize = HearthTheme.sizes.iconM,
            modifier = Modifier.padding(spacing.xs),
            onClick = onStop,
        )
        Text(stringResource(R.string.composer_release_to_stop), color = colors.ink, style = HearthTheme.type.input, maxLines = 1)
        VoiceLevelDotsRow(level, Modifier.weight(1f))
        Box(
            Modifier
                .padding(spacing.xs)
                .size(HearthTheme.sizes.controlL)
                .alpha(WAITING_SEND_ALPHA)
                .background(colors.pine, HearthTheme.shapes.circle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = colors.onPine, modifier = Modifier.size(HearthTheme.sizes.iconM))
        }
    }
}

/** Small dots that swell with the voice. They are decoration: a screen reader is told nothing of them. */
@Composable
private fun VoiceLevelDotsRow(level: Double, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m),
    ) {
        VoiceLevelDots.of(level).dots.forEach { dot ->
            val scale by animateFloatAsState(dot.scale.toFloat(), tween(LEVEL_DOT_MILLIS), label = "voiceLevelDot")
            Box(Modifier.size(LevelDotSide).scale(scale).background(HearthTheme.colors.pine, HearthTheme.shapes.circle))
        }
    }
}

/** How a touch on the resting field ended. */
private enum class TouchEndKind { LIFTED, SLID_OFF }

/**
 * Tells a tap on the resting composer's field from a hold.
 *
 * A touch that lifts quickly is a tap, and is passed on so the field can take the keyboard. One that stays is a
 * hold, which lasts until the finger lifts, slides off the field, or the touch is taken away by the system, as
 * when the app leaves the screen. A screen reader never meets this layer; it has the field and the microphone button.
 *
 * @param onPressChanged Told as a finger comes to rest on the field and again when it has gone, hold or no hold.
 * @param onHoldEnded Called once for every hold that began, however it ends.
 */
internal fun Modifier.holdToTalkTouch(
    onTap: () -> Unit,
    onPressChanged: (Boolean) -> Unit,
    onHoldBegan: () -> Unit,
    onHoldEnded: () -> Unit,
): Modifier = pointerInput(Unit) {
    val allowance = SlideOffAllowance.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressChanged(true)
        try {
            val isOnField: (Offset) -> Boolean = { position ->
                position.x >= -allowance && position.x <= size.width + allowance && position.y >= -allowance && position.y <= size.height + allowance
            }
            // A finger that travels before the hold has begun is scrolling or opening the chat list, not pressing.
            val quick = withTimeoutOrNull(HOLD_DELAY_MILLIS) {
                awaitTouchEnd(down, isOnField) { change -> (change.position - down.position).getDistance() > viewConfiguration.touchSlop }
            }
            when (quick) {
                TouchEndKind.LIFTED -> onTap()
                TouchEndKind.SLID_OFF -> Unit
                null -> {
                    onHoldBegan()
                    try {
                        // The held finger is this layer's own: nothing behind it scrolls or slides while it is down.
                        awaitTouchEnd(down, isOnField) { change ->
                            change.consume()
                            false
                        }
                    } finally {
                        // Reached as well when the system takes the touch away, which no lift ever reports.
                        onHoldEnded()
                    }
                }
            }
        } finally {
            onPressChanged(false)
        }
    }
}

/**
 * Waits for the finger that went down to lift or to leave the field.
 *
 * @param hasStrayed Shown each movement of the finger; says whether it has gone somewhere that ends the touch.
 */
private suspend fun AwaitPointerEventScope.awaitTouchEnd(
    down: PointerInputChange,
    isOnField: (Offset) -> Boolean,
    hasStrayed: (PointerInputChange) -> Boolean,
): TouchEndKind {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return TouchEndKind.SLID_OFF
        // A touch the system takes away arrives as a lift that has already been used up; it is not a tap.
        val wasTakenAway = !change.pressed && change.isConsumed
        if (wasTakenAway || !isOnField(change.position) || hasStrayed(change)) return TouchEndKind.SLID_OFF
        if (!change.pressed) return TouchEndKind.LIFTED
    }
}
