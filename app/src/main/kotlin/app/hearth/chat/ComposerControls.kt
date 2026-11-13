package app.hearth.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthRoundButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ThinkingLevel
import app.hearth.integrations.AttachmentLoader
import kotlinx.coroutines.launch

/** The most photos that can be picked from the library in one go. */
private const val MAXIMUM_PHOTOS_PER_PICK = 4

/** The kinds of file offered for attaching: those whose text can be read. */
private val ATTACHABLE_FILE_TYPES = arrayOf("application/pdf", "text/*", "application/json", "application/xml")

/** The name of a thinking level, in the user's language. */
@Composable
fun ThinkingLevel.title(): String = stringResource(
    when (this) {
        ThinkingLevel.OFF -> R.string.composer_thinking_off
        ThinkingLevel.LOW -> R.string.composer_thinking_light
        ThinkingLevel.MEDIUM -> R.string.composer_thinking_medium
        ThinkingLevel.HIGH -> R.string.composer_thinking_high
    },
)

/** One of the composer's options that is either on or off, shown in the accent colour while on. */
@Composable
internal fun ComposerToggle(icon: ImageVector, label: String, isOn: Boolean, onToggle: () -> Unit) {
    IconToggleButton(checked = isOn, onCheckedChange = { onToggle() }) {
        Icon(icon, label, tint = if (isOn) HearthTheme.colors.pine else HearthTheme.colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconL))
    }
}

/**
 * The ways to add a photo or a file to the message being written, and, where there is a picture server, the
 * switch that makes the next message ask for a picture.
 */
@Composable
internal fun AttachMenu(canSee: Boolean, canMakePictures: Boolean, isPictureMode: Boolean, presenter: ChatPresenter, attachments: AttachmentLoader) {
    val scope = rememberCoroutineScope()
    var isOpen by remember { mutableStateOf(false) }
    // Kept across the trip to the camera, during which Android may put the app away.
    var cameraAddress by rememberSaveable { mutableStateOf<String?>(null) }

    fun addPhotos(addresses: List<String>) {
        if (addresses.isNotEmpty()) scope.launch { presenter.addImagesAction(attachments.photos(addresses)) }
    }

    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val read = attachments.documents(uris.map(Uri::toString))
                presenter.addDocumentsAction(read.documents, read.unreadableCount)
            }
        }
    }
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAXIMUM_PHOTOS_PER_PICK)) { uris ->
        addPhotos(uris.map(Uri::toString))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { wasTaken ->
        if (wasTaken) addPhotos(listOfNotNull(cameraAddress))
    }
    Box {
        HearthIconButton(Icons.Outlined.Add, stringResource(R.string.composer_attach), tint = HearthTheme.colors.mute) { isOpen = true }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            if (canMakePictures) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.picture_mode)) },
                    leadingIcon = { Icon(if (isPictureMode) Icons.Outlined.Check else Icons.Outlined.Brush, null, Modifier.size(HearthTheme.sizes.iconS)) },
                    modifier = Modifier.semantics { selected = isPictureMode },
                    onClick = {
                        isOpen = false
                        presenter.togglePictureModeAction()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.composer_choose_file)) },
                onClick = {
                    isOpen = false
                    files.launch(ATTACHABLE_FILE_TYPES)
                },
            )
            // Photos are offered only where they can be used: by a model that can look at them, or by the picture server.
            if (canSee) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.composer_take_photo)) },
                    onClick = {
                        isOpen = false
                        val address = attachments.newCameraAddress()
                        cameraAddress = address
                        camera.launch(Uri.parse(address))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.composer_photo_library)) },
                    onClick = {
                        isOpen = false
                        library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
        }
    }
}

/** Starts and stops dictation, asking for the microphone the first time it is needed. */
@Composable
internal fun DictationButton(isDictating: Boolean, presenter: ChatPresenter) {
    val context = LocalContext.current
    val colors = HearthTheme.colors
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) presenter.dictationAction() else presenter.dictationDeniedAction()
    }
    val label = stringResource(if (isDictating) R.string.composer_stop_dictation else R.string.composer_dictate)
    HearthIconButton(Icons.Outlined.Mic, label, tint = if (isDictating) colors.danger else colors.mute) {
        val isAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (isDictating || isAllowed) presenter.dictationAction() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
}

/** Shows how hard the model is to think, and opens the menu that changes it. */
@Composable
internal fun ThinkingMenu(level: ThinkingLevel, onSelect: (ThinkingLevel) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    val colors = HearthTheme.colors
    val openLabel = stringResource(R.string.composer_thinking)
    Box {
        TextButton(onClick = { isOpen = true }, modifier = Modifier.semantics { onClick(label = openLabel, action = null) }) {
            Icon(
                Icons.Outlined.Lightbulb,
                openLabel,
                tint = if (level == ThinkingLevel.OFF) colors.mute else colors.pine,
                modifier = Modifier.padding(end = HearthTheme.spacing.xs).size(HearthTheme.sizes.iconS),
            )
            Text(level.title(), color = if (level == ThinkingLevel.OFF) colors.mute else colors.pine, style = HearthTheme.type.control)
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            ThinkingLevel.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title(), fontWeight = if (option == level) FontWeight.SemiBold else null) },
                    onClick = {
                        isOpen = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** Sends the message; stops a reply on its way; or, with nothing written, opens a voice conversation. */
@Composable
internal fun SendButton(viewModel: ChatViewModel, canTalk: Boolean, presenter: ChatPresenter) {
    val colors = HearthTheme.colors
    val focusManager = LocalFocusManager.current
    val offersVoice = !viewModel.isStreaming && !viewModel.canSend && canTalk && viewModel.selectedModel != null
    val isEnabled = viewModel.isStreaming || viewModel.canSend || offersVoice
    val (icon, label) = when {
        viewModel.isStreaming -> Icons.Filled.Stop to R.string.composer_stop
        offersVoice -> Icons.Filled.GraphicEq to R.string.composer_voice
        else -> Icons.Filled.ArrowUpward to R.string.composer_send
    }
    HearthRoundButton(
        icon = icon,
        label = stringResource(label),
        containerColor = if (isEnabled) colors.pine else colors.line,
        contentColor = if (isEnabled) colors.onPine else colors.mute,
        size = HearthTheme.sizes.controlL,
        iconSize = HearthTheme.sizes.iconM,
        modifier = Modifier.padding(HearthTheme.spacing.xs),
        isEnabled = isEnabled,
    ) {
        when {
            viewModel.isStreaming -> presenter.stopAction()
            offersVoice -> presenter.voiceAction()
            else -> {
                // Sending puts the keyboard away, leaving the whole screen for the reply.
                focusManager.clearFocus()
                presenter.sendAction()
            }
        }
    }
}
