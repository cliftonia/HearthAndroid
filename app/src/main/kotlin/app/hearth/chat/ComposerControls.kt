package app.hearth.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthRoundButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.EffortChoiceKind
import app.hearth.domain.ThinkingLevel
import app.hearth.integrations.AttachmentLoader
import app.hearth.shared.rememberDocumentPicker
import app.hearth.shared.rememberMicrophoneRequest
import kotlinx.coroutines.launch

/** The most photos that can be picked from the library in one go. */
private const val MAXIMUM_PHOTOS_PER_PICK = 4

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

/**
 * The name of an effort, in the effort menu and beside it. A model that cannot think has a single ordinary choice,
 * which is not named after a level it would ignore.
 */
@Composable
fun EffortChoiceKind.title(canThink: Boolean): String = when (this) {
    is EffortChoiceKind.Thinking -> if (canThink) level.title() else stringResource(R.string.model_effort_standard)
    EffortChoiceKind.Research -> stringResource(R.string.research_title)
}

/** One of the composer's options that is either on or off, shown in the accent colour while on. */
@Composable
internal fun ComposerToggle(icon: ImageVector, label: String, isOn: Boolean, onToggle: () -> Unit) {
    IconToggleButton(checked = isOn, onCheckedChange = { onToggle() }) {
        Icon(icon, label, tint = if (isOn) HearthTheme.colors.pine else HearthTheme.colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconL))
    }
}

/**
 * The ways to add a photo or a file to the message being written, the way to make what is written a scheduled task,
 * where there is a picture server the switch that makes the next message ask for a picture, and where replies can be
 * spoken the switch that has each one read aloud. [isReadingAloud] is `null` where there is no speech server.
 */
@Composable
internal fun AttachMenu(
    canSee: Boolean,
    canMakePictures: Boolean,
    isPictureMode: Boolean,
    isReadingAloud: Boolean?,
    presenter: ChatPresenter,
    attachments: AttachmentLoader,
) {
    val scope = rememberCoroutineScope()
    var isOpen by remember { mutableStateOf(false) }
    // Kept across the trip to the camera, during which Android may put the app away.
    var cameraAddress by rememberSaveable { mutableStateOf<String?>(null) }

    fun addPhotos(addresses: List<String>) {
        if (addresses.isNotEmpty()) scope.launch { presenter.addImagesAction(attachments.photos(addresses)) }
    }

    val pickFiles = rememberDocumentPicker(
        attachments,
        onRead = { presenter.addDocumentsAction(it.documents, it.unreadableCount) },
        onUnavailable = presenter::filesUnavailableAction,
    )
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAXIMUM_PHOTOS_PER_PICK)) { uris ->
        addPhotos(uris.map(Uri::toString))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { wasTaken ->
        if (wasTaken) addPhotos(listOfNotNull(cameraAddress))
    }

    fun takePhoto() {
        val address = attachments.newCameraAddress()
        cameraAddress = address
        try {
            camera.launch(address.toUri())
        } catch (failure: ActivityNotFoundException) {
            Log.w(ChatPresenter.TAG, "No camera app: ${failure.javaClass.simpleName}")
            presenter.cameraUnavailableAction()
        }
    }
    // An app that uses the camera itself, as a voice conversation does, may borrow the phone's camera app only
    // with the same leave. It is asked for when a photo is first to be taken.
    val context = LocalContext.current
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) takePhoto() else presenter.cameraDeniedAction()
    }
    Box {
        HearthIconButton(Icons.Outlined.Add, stringResource(R.string.composer_attach), tint = HearthTheme.colors.mute) { isOpen = true }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.composer_choose_file)) },
                leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(HearthTheme.sizes.iconS)) },
                onClick = {
                    isOpen = false
                    pickFiles()
                },
            )
            // Photos are offered only where they can be used: by a model that can look at them, or by the picture server.
            if (canSee) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.composer_take_photo)) },
                    leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(HearthTheme.sizes.iconS)) },
                    onClick = {
                        isOpen = false
                        val isAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (isAllowed) takePhoto() else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.composer_photo_library)) },
                    leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(HearthTheme.sizes.iconS)) },
                    onClick = {
                        isOpen = false
                        library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
            // Below the ways to attach, what Hearth makes and does: a picture, replies read aloud, a task, offered whether
            // or not sync is on (without it, the tasks page says why none can be added), and last a new project.
            HorizontalDivider(color = HearthTheme.colors.line)
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
            if (isReadingAloud != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.composer_read_replies)) },
                    leadingIcon = {
                        val icon = if (isReadingAloud) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.VolumeUp
                        Icon(icon, null, Modifier.size(HearthTheme.sizes.iconS))
                    },
                    modifier = Modifier.semantics { selected = isReadingAloud },
                    onClick = {
                        isOpen = false
                        presenter.toggleAutoReadAction()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.composer_schedule_task)) },
                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(HearthTheme.sizes.iconS)) },
                onClick = {
                    isOpen = false
                    presenter.scheduleTaskAction()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.project_new)) },
                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null, Modifier.size(HearthTheme.sizes.iconS)) },
                onClick = {
                    isOpen = false
                    presenter.newProjectAction()
                },
            )
        }
    }
}

/** Starts and stops dictation, asking for the microphone the first time it is needed. */
@Composable
internal fun DictationButton(isDictating: Boolean, presenter: ChatPresenter) {
    val colors = HearthTheme.colors
    val scope = rememberCoroutineScope()
    val requestMicrophone = rememberMicrophoneRequest()
    val label = stringResource(if (isDictating) R.string.composer_stop_dictation else R.string.composer_dictate)
    HearthIconButton(Icons.Outlined.Mic, label, tint = if (isDictating) colors.danger else colors.mute) {
        if (isDictating) {
            presenter.dictationAction()
        } else {
            scope.launch { if (requestMicrophone()) presenter.dictationAction() else presenter.dictationDeniedAction() }
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
                Haptics.play(HapticKind.LIGHT)
                presenter.sendAction()
            }
        }
    }
}
