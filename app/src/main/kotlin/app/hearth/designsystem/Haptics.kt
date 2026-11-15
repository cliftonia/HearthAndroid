package app.hearth.designsystem

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** The taps the app answers with. Each is named for how it feels, not for what caused it. */
enum class HapticKind {
    /** A light tap: something was sent, pinned or switched. */
    LIGHT,

    /** A softer, rounder tap: something finished by itself. */
    SOFT,

    /** The tick of a choice moving from one thing to another. */
    SELECTION,

    /** Something was done and is now there, such as a copy or a picture. */
    SUCCESS,

    /** A caution, as something is destroyed. */
    WARNING,

    /** Something went wrong. */
    ERROR,
}

/**
 * The one place a haptic is played from, so that the switch in Settings silences them all.
 *
 * A screen either calls [play] from a button's action or hangs a [HapticEffect] on a value that changes. A phone
 * or tablet with nothing to tap with, or with touch feedback turned off in its own settings, plays nothing.
 */
object Haptics {
    /** Whether haptics are wanted. [HapticsHost] keeps this in step with the switch in Settings. */
    @Volatile
    var isEnabled: Boolean = true

    /** What plays a haptic once it is known to be wanted. The window puts its own here; so may a test. */
    @Volatile
    var output: (HapticKind) -> Unit = {}

    /** Plays the haptic, unless haptics are switched off. */
    fun play(kind: HapticKind) {
        if (isEnabled) output(kind)
    }
}

/** The system's name for each tap. The fuller ones came with Android 11; before it the nearest older one stands in. */
@SuppressLint("InlinedApi") // Each newer name is used only on the versions that have it, as the version handed in says.
internal fun HapticKind.feedbackConstant(sdk: Int = Build.VERSION.SDK_INT): Int = when (this) {
    HapticKind.LIGHT -> HapticFeedbackConstants.VIRTUAL_KEY
    HapticKind.SOFT -> HapticFeedbackConstants.CLOCK_TICK
    HapticKind.SELECTION -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
    HapticKind.SUCCESS -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    HapticKind.WARNING -> HapticFeedbackConstants.LONG_PRESS
    HapticKind.ERROR -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
}

/**
 * Gives [Haptics] the window to play through and keeps it in step with the switch in Settings. Placed once, at
 * the root of what the window draws.
 */
@Composable
fun HapticsHost(isEnabled: Boolean) {
    val view = LocalView.current
    SideEffect { Haptics.isEnabled = isEnabled }
    DisposableEffect(view) {
        val output: (HapticKind) -> Unit = { kind -> view.performHapticFeedback(kind.feedbackConstant()) }
        Haptics.output = output
        // A window that has gone is let go of, unless a newer one has already taken its place.
        onDispose { if (Haptics.output === output) Haptics.output = {} }
    }
}

/**
 * Plays a haptic each time [trigger] changes, when [condition] holds for the change. Nothing is played for the
 * value the screen is first drawn with.
 *
 * @param condition Given the old value and the new; the haptic is played only when it returns `true`.
 */
@Composable
fun <T> HapticEffect(kind: HapticKind, trigger: T, condition: (old: T, new: T) -> Boolean = { _, _ -> true }) {
    val previous = remember { mutableListOf(trigger) }
    LaunchedEffect(trigger) {
        val old = previous[0]
        previous[0] = trigger
        if (old != trigger && condition(old, trigger)) Haptics.play(kind)
    }
}
