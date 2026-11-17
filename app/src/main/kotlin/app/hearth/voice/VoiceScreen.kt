package app.hearth.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticEffect
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.HearthTheme
import app.hearth.integrations.CameraLeave
import app.hearth.integrations.DeviceCamera
import app.hearth.shared.rememberMicrophoneRequest

// The ember is this screen's own drawing, so its measures are kept here and not in the theme.

/** The area that takes a tap to cut a reply short; a little larger than the ember at its fullest. */
private val EmberTouchSize = 180.dp

/** The soft halo of the ember, at its fullest. */
private val EmberHaloSize = 170.dp

/** The solid heart of the ember. */
private val EmberCoreSize = 104.dp

/** How far the ember shrinks at the low point of a breath, and where it rests when nothing is happening. */
private const val EMBER_REST_SCALE = 0.86f

/** The length of half a breath in milliseconds: quick while speaking, slow otherwise. */
private const val EMBER_SPEAKING_MILLISECONDS = 520
private const val EMBER_CALM_MILLISECONDS = 1300

/** The most lines of caption shown at once, while the camera's picture takes none of the room. */
private const val CAPTION_MAXIMUM_LINES = 6

/**
 * A spoken conversation: the phone listens, the model answers aloud, and so on until it is ended. With the camera
 * on, its live picture sits above the ember and one still picture goes with each thing said.
 *
 * @param camera Draws the camera's live picture.
 * @param cameraLeave Puts the phone's question about the camera to the user, through this screen.
 */
@Composable
fun VoiceScreen(presenter: VoicePresenter, camera: DeviceCamera, cameraLeave: CameraLeave, modifier: Modifier = Modifier) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
    // Each turn of the conversation is felt: listening, thinking, speaking.
    HapticEffect(HapticKind.LIGHT, viewModel.state)
    val colors = HearthTheme.colors
    CameraLifetime(presenter, cameraLeave)
    val requestMicrophone = rememberMicrophoneRequest()
    LaunchedEffect(Unit) { presenter.startAction(requestMicrophone()) }
    val canInterrupt = viewModel.state == VoiceStateKind.THINKING || viewModel.state == VoiceStateKind.SPEAKING
    val title = stringResource(
        when (viewModel.state) {
            VoiceStateKind.LISTENING -> R.string.voice_listening
            VoiceStateKind.THINKING -> R.string.voice_thinking
            VoiceStateKind.SPEAKING -> R.string.voice_speaking
            VoiceStateKind.MUTED -> R.string.voice_muted
            VoiceStateKind.UNAVAILABLE -> R.string.voice_unavailable
        },
    )
    val caption = when (viewModel.failure) {
        VoiceFailureKind.PERMISSION_DENIED -> stringResource(R.string.voice_permission_denied)
        VoiceFailureKind.DICTATION_UNAVAILABLE -> stringResource(R.string.error_dictation_unavailable)
        VoiceFailureKind.SPEECH_FAILED -> stringResource(R.string.error_speech_failed)
        null -> viewModel.caption.ifEmpty {
            when (viewModel.state) {
                VoiceStateKind.LISTENING -> stringResource(R.string.voice_hint)
                VoiceStateKind.MUTED -> stringResource(R.string.voice_muted_hint)
                VoiceStateKind.THINKING, VoiceStateKind.SPEAKING, VoiceStateKind.UNAVAILABLE -> ""
            }
        }
    }
    val showsCamera = viewModel.camera.isPreviewShown
    val emberScale by animateFloatAsState(if (showsCamera) VoiceCameraLayout.EMBER_SCALE else 1f, label = "emberScale")
    Column(modifier.fillMaxSize().padding(HearthTheme.spacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            listOf(viewModel.modelName, viewModel.voiceName).filter { it.isNotEmpty() }.joinToString(" · "),
            color = colors.mute,
            style = HearthTheme.type.footnote,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // On a phone held on its side there is no room to stack them, so the card stands beside the ember.
            val isSideBySide = showsCamera && VoiceCameraLayout.isSideBySide(maxWidth.value, maxHeight.value)
            val card = if (isSideBySide) {
                VoiceCameraLayout.sideCardSize(maxWidth.value - HearthTheme.spacing.gapXl.value, maxHeight.value)
            } else {
                VoiceCameraLayout.cardSize(maxWidth.value, maxHeight.value)
            }
            val cardSize = Modifier.size(card.width.dp, card.height.dp)
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.gapXl, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSideBySide) VoiceCameraCard(viewModel, presenter, camera, cardSize)
                Column(
                    Modifier.weight(1f, fill = !isSideBySide),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (showsCamera) HearthTheme.spacing.xxxl else HearthTheme.spacing.gapXl, Alignment.CenterVertically),
                ) {
                    AnimatedVisibility(showsCamera && !isSideBySide) { VoiceCameraCard(viewModel, presenter, camera, cardSize) }
                    val interruptLabel = stringResource(R.string.voice_interrupt)
                    Box(
                        Modifier
                            .size(EmberTouchSize * emberScale)
                            .clip(HearthTheme.shapes.circle)
                            // A button, named for what it does, only while there is something to interrupt; otherwise
                            // it only decorates and a screen reader passes it by.
                            .then(
                                if (canInterrupt) {
                                    Modifier
                                        .clickable(onClickLabel = interruptLabel, role = Role.Button, onClick = presenter::interruptAction)
                                        .semantics { contentDescription = interruptLabel }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) { Ember(viewModel.state, Modifier.requiredSize(EmberHaloSize).scale(emberScale)) }
                    Text(title, style = HearthTheme.type.display, color = colors.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text(
                        caption,
                        color = colors.mute,
                        style = HearthTheme.type.lead,
                        textAlign = TextAlign.Center,
                        maxLines = if (showsCamera) VoiceCameraLayout.CAPTION_MAXIMUM_LINES else CAPTION_MAXIMUM_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        VoiceControls(viewModel, presenter)
    }
}

/**
 * Keeps the camera to the time this screen is in front of the user: it lends the screen's way of asking for
 * leave, and turns the camera off when the app is covered or put away and when the screen goes, however it goes.
 * Turning the phone round is none of those.
 */
@Composable
private fun CameraLifetime(presenter: VoicePresenter, cameraLeave: CameraLeave) {
    val activity = LocalContext.current.findActivity()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), cameraLeave::answered)
    DisposableEffect(presenter, cameraLeave) {
        cameraLeave.bind { permission.launch(Manifest.permission.CAMERA) }
        onDispose {
            val isLeaving = activity?.isChangingConfigurations != true
            cameraLeave.unbind(isLeaving)
            if (isLeaving) presenter.closedAction()
        }
    }
    // The phone's own question about the camera covers the app without taking it off the screen.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (activity?.isChangingConfigurations != true) presenter.leftForegroundAction(isOffScreen = false)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) presenter.leftForegroundAction(isOffScreen = true)
    }
}

/** The window this screen is drawn in, which knows whether it is only being redrawn. */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The mark of the conversation, breathing while the phone listens or speaks. */
@Composable
private fun Ember(state: VoiceStateKind, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val isLively = state == VoiceStateKind.LISTENING || state == VoiceStateKind.SPEAKING || state == VoiceStateKind.THINKING
    val transition = rememberInfiniteTransition(label = "ember")
    val pulse by transition.animateFloat(
        initialValue = EMBER_REST_SCALE,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (state == VoiceStateKind.SPEAKING) EMBER_SPEAKING_MILLISECONDS else EMBER_CALM_MILLISECONDS),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(
        modifier.scale(if (isLively) pulse else EMBER_REST_SCALE).background(colors.pineSoft, HearthTheme.shapes.circle),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(EmberCoreSize).background(if (isLively) colors.pine else colors.mute, HearthTheme.shapes.circle))
    }
}
