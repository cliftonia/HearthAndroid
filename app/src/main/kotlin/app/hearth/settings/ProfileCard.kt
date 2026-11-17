package app.hearth.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthAvatar
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthPageRow
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ServerSettings
import app.hearth.domain.UserProfile
import app.hearth.domain.clipped
import app.hearth.integrations.ProfilePhotoLoader

/** The most that can be typed as a name. */
private const val MAXIMUM_NAME_LENGTH = 80

/** The kept photo as a picture to draw; `null` when there is none or it no longer decodes. */
@Composable
internal fun rememberAvatar(photo: ByteArray?): ImageBitmap? = remember(photo) { photo?.let(ProfilePhotoLoader::bitmap)?.asImageBitmap() }

/** The row at the top of the settings: the avatar and the name, leading to the profile page. */
@Composable
internal fun ProfileCard(profile: UserProfile, avatar: ImageBitmap?, presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val name = profile.displayName ?: stringResource(R.string.profile_no_name)
    HearthCard {
        HearthPageRow(stringResource(R.string.profile_open, name), presenter::profileAction, spacing = HearthTheme.spacing.xl) {
            HearthAvatar(avatar, profile.initials, HearthTheme.sizes.avatarS)
            Column(Modifier.weight(1f)) {
                Text(name, color = colors.ink, style = HearthTheme.type.subheadStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.profile_title), color = colors.mute, style = HearthTheme.type.footnote)
            }
        }
    }
}

/** Who the user is and how they like to be answered: the photo, the names and the instructions. */
@Composable
internal fun ProfilePage(settings: ServerSettings, avatar: ImageBitmap?, presenter: SettingsPresenter, photos: ProfilePhotoLoader) {
    val error by presenter.profileError.collectAsStateWithLifecycle()
    AvatarSection(avatar, settings.profile.initials, presenter, photos)
    HearthCard(footer = stringResource(R.string.profile_names_footer)) {
        NameField(stringResource(R.string.profile_full_name), settings.fullName) { text -> presenter.updateAction { it.copy(fullName = text) } }
        NameField(stringResource(R.string.profile_nickname), settings.nickname) { text -> presenter.updateAction { it.copy(nickname = text) } }
    }
    val instructionsTitle = stringResource(R.string.profile_instructions)
    HearthCard(instructionsTitle, stringResource(R.string.profile_instructions_footer)) {
        OutlinedTextField(
            value = settings.customInstructions,
            onValueChange = { text -> presenter.updateAction { it.copy(customInstructions = text) } },
            // The card's title names the field; the example stays in view until something is written.
            placeholder = { Text(stringResource(R.string.settings_instructions_placeholder)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = instructionsTitle },
        )
    }
    if (error == ProfileErrorKind.PHOTO_FAILED) ProblemLine(stringResource(R.string.profile_photo_failed))
}

/** The avatar, with the control beneath it that chooses a photo or takes it away. */
@Composable
private fun AvatarSection(avatar: ImageBitmap?, initials: String, presenter: SettingsPresenter, photos: ProfilePhotoLoader) {
    val colors = HearthTheme.colors
    var isMenuOpen by remember { mutableStateOf(false) }
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) presenter.setPhotoAction { photos.avatarJpeg(uri.toString()) }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HearthAvatar(avatar, initials, HearthTheme.sizes.avatarL, isLarge = true)
        Box {
            TextButton(onClick = { isMenuOpen = true }) { Text(stringResource(R.string.profile_edit_photo), color = colors.pine) }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_choose_photo)) },
                    onClick = {
                        isMenuOpen = false
                        library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_remove_photo), color = if (avatar != null) colors.danger else colors.mute) },
                    enabled = avatar != null,
                    onClick = {
                        isMenuOpen = false
                        presenter.removePhotoAction()
                    },
                )
            }
        }
    }
}

/** A one-line field for a name, with a keyboard that capitalises each word and does not correct it. */
@Composable
private fun NameField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.clipped(MAXIMUM_NAME_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A problem on a page, said beneath what went wrong and announced as it appears. */
@Composable
internal fun ProblemLine(message: String) {
    val colors = HearthTheme.colors
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = colors.danger, modifier = Modifier.size(HearthTheme.sizes.iconS))
        Text(message, color = colors.danger, style = HearthTheme.type.footnoteSnug)
    }
}
