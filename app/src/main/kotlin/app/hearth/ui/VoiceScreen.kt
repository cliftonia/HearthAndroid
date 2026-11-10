package app.hearth.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.VoiceFailureKind
import app.hearth.VoicePresenter
import app.hearth.VoiceStateKind

/** A spoken conversation: the phone listens, the model answers aloud, and so on until it is ended. */
@Composable
fun VoiceScreen(presenter: VoicePresenter, modifier: Modifier = Modifier) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
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
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(listOf(viewModel.modelName, viewModel.voiceName).filter { it.isNotEmpty() }.joinToString(" · "), color = colors.mute, fontSize = 13.sp)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            val interruptLabel = stringResource(R.string.voice_interrupt)
            Box(
                Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canInterrupt, onClickLabel = interruptLabel, role = Role.Button, onClick = presenter::interruptAction),
                contentAlignment = Alignment.Center,
            ) { Ember(viewModel.state) }
            Text(title, style = HearthTheme.display, color = colors.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(caption, color = colors.mute, fontSize = 17.sp, lineHeight = 25.sp, textAlign = TextAlign.Center, maxLines = 6)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = presenter::muteAction, modifier = Modifier.size(64.dp).background(colors.bubble, CircleShape)) {
                Icon(
                    if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    stringResource(if (isMuted) R.string.voice_unmute else R.string.voice_mute),
                    tint = colors.ink,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = presenter::endAction, modifier = Modifier.size(64.dp).background(colors.danger, CircleShape)) {
                Icon(Icons.Filled.Close, stringResource(R.string.voice_end), tint = colors.onPine, modifier = Modifier.size(28.dp))
            }
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
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (state == VoiceStateKind.SPEAKING) 520 else 1300), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.size(170.dp).scale(if (isLively) pulse else 0.86f).background(colors.pineSoft, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(104.dp).background(if (isLively) colors.pine else colors.mute, CircleShape))
    }
}

/** Shown in place of everything else until the phone has confirmed who is holding it. */
@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    // The phone asks at once; the button is there in case the first asking is dismissed.
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        modifier.fillMaxSize().background(colors.paper).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        HearthMark(64.dp)
        Text(stringResource(R.string.brand), style = HearthTheme.display, color = colors.ink)
        androidx.compose.material3.Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
    }
}
