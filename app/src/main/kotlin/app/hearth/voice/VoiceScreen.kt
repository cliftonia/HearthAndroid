package app.hearth.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticEffect
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.HearthRoundButton
import app.hearth.designsystem.HearthTheme

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

/** The most lines of caption shown at once. */
private const val CAPTION_MAXIMUM_LINES = 6

/** A spoken conversation: the phone listens, the model answers aloud, and so on until it is ended. */
@Composable
fun VoiceScreen(presenter: VoicePresenter, modifier: Modifier = Modifier) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
    // Each turn of the conversation is felt: listening, thinking, speaking.
    HapticEffect(HapticKind.LIGHT, viewModel.state)
    val context = LocalContext.current
    val colors = HearthTheme.colors
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), presenter::startAction)
    LaunchedEffect(Unit) {
        val isAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (isAllowed) presenter.startAction(true) else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val isMuted = viewModel.state == VoiceStateKind.MUTED
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
    Column(modifier.fillMaxSize().padding(HearthTheme.spacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            listOf(viewModel.modelName, viewModel.voiceName).filter { it.isNotEmpty() }.joinToString(" · "),
            color = colors.mute,
            style = HearthTheme.type.footnote,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.gapXl, Alignment.CenterVertically),
        ) {
            val interruptLabel = stringResource(R.string.voice_interrupt)
            Box(
                Modifier
                    .size(EmberTouchSize)
                    .clip(HearthTheme.shapes.circle)
                    .clickable(enabled = canInterrupt, onClickLabel = interruptLabel, role = Role.Button, onClick = presenter::interruptAction),
                contentAlignment = Alignment.Center,
            ) { Ember(viewModel.state) }
            Text(title, style = HearthTheme.type.display, color = colors.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(caption, color = colors.mute, style = HearthTheme.type.lead, textAlign = TextAlign.Center, maxLines = CAPTION_MAXIMUM_LINES)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.gapXxl), verticalAlignment = Alignment.CenterVertically) {
            HearthRoundButton(
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = stringResource(if (isMuted) R.string.voice_unmute else R.string.voice_mute),
                containerColor = colors.bubble,
                contentColor = colors.ink,
                size = HearthTheme.sizes.controlXl,
                iconSize = HearthTheme.sizes.iconXl,
                onClick = presenter::muteAction,
            )
            HearthRoundButton(
                icon = Icons.Filled.Close,
                label = stringResource(R.string.voice_end),
                containerColor = colors.danger,
                contentColor = colors.onPine,
                size = HearthTheme.sizes.controlXl,
                iconSize = HearthTheme.sizes.iconXl,
                onClick = presenter::endAction,
            )
        }
    }
}

/** The mark of the conversation, breathing while the phone listens or speaks. */
@Composable
private fun Ember(state: VoiceStateKind) {
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
        Modifier.size(EmberHaloSize).scale(if (isLively) pulse else EMBER_REST_SCALE).background(colors.pineSoft, HearthTheme.shapes.circle),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(EmberCoreSize).background(if (isLively) colors.pine else colors.mute, HearthTheme.shapes.circle))
    }
}
