package app.hearth.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthPermissionNotice
import app.hearth.designsystem.HearthRoundButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.VoiceCameraControlKind
import app.hearth.domain.VoiceCameraNoticeKind
import app.hearth.domain.VoiceCameraPhaseKind
import app.hearth.integrations.openAppSettings

/** How faint the camera button is while the chosen model cannot see. */
private const val DISABLED_ALPHA = 0.4f

/**
 * The buttons along the bottom of a voice conversation: the camera, mute and end, with whatever has to be said
 * about the camera above them.
 */
@Composable
internal fun VoiceControls(viewModel: VoiceViewModel, presenter: VoicePresenter, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val isMuted = viewModel.state == VoiceStateKind.MUTED
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxxl)) {
        CameraNotice(viewModel)
        Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.gapXxl), verticalAlignment = Alignment.CenterVertically) {
            // A phone's cameras do not come and go, so the button is either there for good or not at all.
            if (viewModel.cameraControl != VoiceCameraControlKind.HIDDEN) CameraButton(viewModel, presenter)
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
                containerColor = colors.dangerFill,
                contentColor = colors.onDangerFill,
                size = HearthTheme.sizes.controlXl,
                iconSize = HearthTheme.sizes.iconXl,
                onClick = presenter::endAction,
            )
        }
    }
}

/** Turns the camera on and off. For a model that cannot see it is shown, faint, and does nothing. */
@Composable
private fun CameraButton(viewModel: VoiceViewModel, presenter: VoicePresenter) {
    val colors = HearthTheme.colors
    val isOn = viewModel.camera.phase != VoiceCameraPhaseKind.OFF
    val isEnabled = viewModel.cameraControl == VoiceCameraControlKind.ENABLED
    val fade = if (isEnabled) 1f else DISABLED_ALPHA
    // A screen reader hears what the camera does after the button's name, as a hint is read after a name; it is
    // not a state. Why the camera cannot be used is written on the screen beneath, so it is not repeated here.
    val name = stringResource(if (isOn) R.string.voice_camera_turn_off else R.string.voice_camera_turn_on)
    val label = if (isEnabled) stringResource(R.string.voice_camera_named_hint, name, stringResource(R.string.voice_camera_hint)) else name
    HearthRoundButton(
        icon = if (isOn) Icons.Filled.Videocam else Icons.Outlined.Videocam,
        label = label,
        containerColor = (if (isOn) colors.pineSoft else colors.bubble).copy(alpha = fade),
        contentColor = (if (isOn) colors.pine else colors.ink).copy(alpha = fade),
        size = HearthTheme.sizes.controlXl,
        iconSize = HearthTheme.sizes.iconXl,
        isEnabled = isEnabled,
    ) {
        Haptics.play(HapticKind.LIGHT)
        presenter.cameraAction()
    }
}

/** Why the camera is off when the user would expect otherwise. Nothing is drawn while there is nothing to say. */
@Composable
private fun CameraNotice(viewModel: VoiceViewModel) {
    val context = LocalContext.current
    val width = Modifier.widthIn(max = VoiceCameraLayout.MAXIMUM_WIDTH.dp)
    when {
        viewModel.cameraControl == VoiceCameraControlKind.DISABLED -> Text(
            stringResource(R.string.voice_camera_model_cannot_see),
            color = HearthTheme.colors.mute,
            style = HearthTheme.type.footnoteSnug,
            textAlign = TextAlign.Center,
            modifier = width,
        )
        viewModel.camera.notice == VoiceCameraNoticeKind.PERMISSION_REFUSED -> HearthPermissionNotice(
            Icons.Outlined.VideocamOff,
            stringResource(R.string.voice_camera_denied),
            actionTitle = stringResource(R.string.voice_camera_open_settings),
            modifier = width,
        ) { openAppSettings(context) }
        viewModel.camera.notice == VoiceCameraNoticeKind.UNAVAILABLE -> HearthPermissionNotice(
            Icons.Outlined.VideocamOff,
            stringResource(R.string.voice_camera_unavailable),
            actionTitle = null,
            modifier = width,
        )
        else -> Unit
    }
}
