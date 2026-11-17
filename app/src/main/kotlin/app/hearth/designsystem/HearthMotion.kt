package app.hearth.designsystem

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

// How one screen gives way to another, as Material's motion system draws it. Compose plays every one of these at the
// phone's animator duration scale, so they are slower, faster or gone as the user has set it in the phone's settings.

/** How long a whole transition between screens takes, in milliseconds. */
private const val TRANSITION_MILLIS = 300

/** How long the screen being left takes to fade, in milliseconds: the first part of a transition. */
private const val FADE_OUT_MILLIS = 90

/** How far a page moves along the shared axis, as a fraction of its width: about Material's 30dp on a phone. */
private const val SHARED_AXIS_SHIFT_FRACTION = 0.08f

/** How small a screen starts in a fade through, as a fraction of its size. */
private const val FADE_THROUGH_SCALE = 0.92f

/**
 * Material's shared axis along x: between a list and a page it leads to. Going [forward], the new page comes in a
 * little from the end as the old one slips the other way, each fading; going back, the other way round.
 */
fun sharedAxisX(forward: Boolean): ContentTransform {
    val sign = if (forward) 1 else -1
    val enter = slideInHorizontally(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing)) { (it * SHARED_AXIS_SHIFT_FRACTION).toInt() * sign } +
        fadeIn(tween(TRANSITION_MILLIS - FADE_OUT_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing))
    val exit = slideOutHorizontally(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing)) { -(it * SHARED_AXIS_SHIFT_FRACTION).toInt() * sign } +
        fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutSlowInEasing))
    return enter togetherWith exit
}

/**
 * Material's fade through: between screens that are not one inside the other, such as the chat and the settings. The
 * old one fades out, then the new one fades in as it grows a little into place.
 */
fun fadeThrough(): ContentTransform {
    val enter = fadeIn(tween(TRANSITION_MILLIS - FADE_OUT_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(TRANSITION_MILLIS - FADE_OUT_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing), initialScale = FADE_THROUGH_SCALE)
    val exit = fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutSlowInEasing))
    return enter togetherWith exit
}
