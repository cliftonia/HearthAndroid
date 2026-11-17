package app.hearth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ServerSettings

/**
 * Which voice reads replies, chosen from those the speech server offers or typed by hand, a sample of it, and whether
 * replies are read aloud. Reached only while there is a speech server; the voices are asked for as the page opens,
 * since the address can no longer be changing.
 */
@Composable
internal fun VoicePage(settings: ServerSettings, presenter: SettingsPresenter) {
    val voices by presenter.voices.collectAsStateWithLifecycle()
    var isOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settings.speechUrl) { presenter.loadVoicesAction() }
    HearthCard {
        Box {
            OutlinedTextField(
                value = settings.voice,
                onValueChange = { text -> presenter.updateAction { it.copy(voice = text.trim()) } },
                label = { Text(stringResource(R.string.settings_voice_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                trailingIcon = {
                    if (voices.isNotEmpty()) {
                        HearthIconButton(Icons.Outlined.ExpandMore, stringResource(R.string.settings_voice_choose), tint = HearthTheme.colors.mute) { isOpen = true }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice) },
                        onClick = {
                            isOpen = false
                            presenter.updateAction { it.copy(voice = voice) }
                        },
                    )
                }
            }
        }
        VoicePreviewRow(presenter)
        HearthSwitchRow(stringResource(R.string.composer_read_replies), settings.isAutoReadEnabled) { isOn ->
            presenter.updateAction { it.copy(isAutoReadEnabled = isOn) }
        }
    }
}

/** Plays a short sample, so a voice can be heard before it is kept. */
@Composable
private fun VoicePreviewRow(presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val preview by presenter.voicePreview.collectAsStateWithLifecycle()
    val sample = stringResource(R.string.settings_voice_sample)
    val isPlaying = preview == VoicePreviewKind.PLAYING
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xl)) {
        OutlinedButton(onClick = { presenter.previewVoiceAction(sample) }) {
            Icon(
                if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(HearthTheme.sizes.iconS),
            )
            Text(
                stringResource(if (isPlaying) R.string.settings_voice_sample_stop else R.string.settings_voice_sample_play),
                Modifier.padding(start = HearthTheme.spacing.s),
            )
        }
        // Always there, so that TalkBack hears the failure as it is written into it.
        Text(
            if (preview == VoicePreviewKind.FAILED) stringResource(R.string.settings_voice_sample_failed) else "",
            color = colors.danger,
            style = HearthTheme.type.footnoteSnug,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
