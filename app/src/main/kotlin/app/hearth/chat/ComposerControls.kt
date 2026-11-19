package app.hearth.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
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

/** Adds the photos at these addresses to the message being written, once they have been read. */
@Composable
private fun rememberPhotoAdding(presenter: ChatPresenter, attachments: AttachmentLoader): (List<String>) -> Unit {
    val scope = rememberCoroutineScope()
    return { addresses -> if (addresses.isNotEmpty()) scope.launch { presenter.addImagesAction(attachments.photos(addresses)) } }
}

/**
 * Gives the way to open the system's photo picker, which adds the photos picked to the message being written. The
 * plus button's sheet and the button beside the picture chip both add photos through it.
 */
@Composable
internal fun rememberPhotoLibrary(presenter: ChatPresenter, attachments: AttachmentLoader): () -> Unit {
    val addPhotos = rememberPhotoAdding(presenter, attachments)
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAXIMUM_PHOTOS_PER_PICK)) { uris ->
        addPhotos(uris.map(Uri::toString))
    }
    return { library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/**
 * Beside the picture chip while a picture is being asked for: adds a photo for the picture server to change, so that
 * it is plain a photo can be changed and there is no need to go back to the plus button for one. Drawn as the
 * composer's other options are while they are off.
 */
@Composable
internal fun PicturePhotoButton(presenter: ChatPresenter, attachments: AttachmentLoader) {
    val openLibrary = rememberPhotoLibrary(presenter, attachments)
    HearthIconButton(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.picture_add_photo), tint = HearthTheme.colors.mute, onClick = openLibrary)
}

/**
 * The plus button and the sheet it opens: the ways to add a photo or a file to the message being written, where there
 * is a picture server the way to make the next message ask for a picture, the way to make what is written a scheduled
 * task, a new project, and, thrown without closing the sheet, where there is a search address the switch that has the
 * next messages researched and where replies can be spoken the switch that has each one read aloud.
 * [isResearching] is `null` where there is no search address, and [isReadingAloud] where there is no speech server.
 */
@Composable
internal fun AttachMenu(
    canSee: Boolean,
    canMakePictures: Boolean,
    isPictureMode: Boolean,
    isResearching: Boolean?,
    isReadingAloud: Boolean?,
    presenter: ChatPresenter,
    attachments: AttachmentLoader,
) {
    var isOpen by remember { mutableStateOf(false) }
    // Kept across the trip to the camera, during which Android may put the app away.
    var cameraAddress by rememberSaveable { mutableStateOf<String?>(null) }
    val addPhotos = rememberPhotoAdding(presenter, attachments)

    val pickFiles = rememberDocumentPicker(
        attachments,
        onRead = { presenter.addDocumentsAction(it.documents, it.unreadableCount) },
        onUnavailable = presenter::filesUnavailableAction,
    )
    val openLibrary = rememberPhotoLibrary(presenter, attachments)
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
    val hasCamera = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

    fun perform(choice: AttachmentChoiceKind) {
        when (choice) {
            AttachmentChoiceKind.FILE -> pickFiles()
            AttachmentChoiceKind.CAMERA -> {
                val isAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (isAllowed) takePhoto() else cameraPermission.launch(Manifest.permission.CAMERA)
            }
            AttachmentChoiceKind.PHOTO_LIBRARY -> openLibrary()
            AttachmentChoiceKind.RESEARCH -> presenter.toggleResearchAction()
            AttachmentChoiceKind.PICTURE_MODE -> presenter.togglePictureModeAction()
            AttachmentChoiceKind.READ_ALOUD -> presenter.toggleAutoReadAction()
            AttachmentChoiceKind.SCHEDULE_TASK -> presenter.scheduleTaskAction()
            AttachmentChoiceKind.NEW_PROJECT -> presenter.newProjectAction()
        }
    }

    HearthIconButton(Icons.Outlined.Add, stringResource(R.string.composer_attach), tint = HearthTheme.colors.mute) { isOpen = true }
    if (isOpen) {
        val configuration = AttachmentSheetConfiguration(
            // The photo library is a button in the sheet's heading, so the first card holds the camera and files. The
            // second holds what is made: a picture, which closes the sheet with the composer ready for what it should
            // show (a photo to change is added from the composer itself); a task, offered whether or not sync is on
            // (without it, the tasks page says why none can be added); and a new project.
            groups = listOf(
                listOfNotNull(AttachmentChoiceKind.CAMERA.takeIf { hasCamera }, AttachmentChoiceKind.FILE),
                listOfNotNull(
                    AttachmentChoiceKind.PICTURE_MODE.takeIf { canMakePictures },
                    AttachmentChoiceKind.SCHEDULE_TASK,
                    AttachmentChoiceKind.NEW_PROJECT,
                ),
            ),
            // What the next message asks for, and how replies are given.
            switches = listOfNotNull(
                AttachmentChoiceKind.RESEARCH.takeIf { isResearching != null },
                AttachmentChoiceKind.READ_ALOUD.takeIf { isReadingAloud != null },
            ),
            // Photos can be chosen only where they can be used: by a model that can look at them, or by the picture server.
            isEnabled = { it.isAvailable(canSee) },
            isOn = { it.isOn(isResearching == true, isPictureMode, isReadingAloud == true) },
        )
        AttachmentSheet(
            configuration,
            onChoose = { choice ->
                isOpen = false
                perform(choice)
            },
            // A switch is thrown where it stands, and the sheet stays open.
            onToggle = ::perform,
            onDismiss = { isOpen = false },
        )
    }
}

/** Whether the choice can be made: a photo only while there is something to look at it, anything else always. */
private fun AttachmentChoiceKind.isAvailable(canSee: Boolean): Boolean = when (this) {
    AttachmentChoiceKind.CAMERA, AttachmentChoiceKind.PHOTO_LIBRARY -> canSee
    AttachmentChoiceKind.FILE,
    AttachmentChoiceKind.RESEARCH,
    AttachmentChoiceKind.PICTURE_MODE,
    AttachmentChoiceKind.READ_ALOUD,
    AttachmentChoiceKind.SCHEDULE_TASK,
    AttachmentChoiceKind.NEW_PROJECT,
    -> true
}

/** Whether the choice is on: a switch that is thrown, or picture mode, whose row shows a tick. */
private fun AttachmentChoiceKind.isOn(isResearching: Boolean, isPictureMode: Boolean, isReadingAloud: Boolean): Boolean = when (this) {
    AttachmentChoiceKind.RESEARCH -> isResearching
    AttachmentChoiceKind.PICTURE_MODE -> isPictureMode
    AttachmentChoiceKind.READ_ALOUD -> isReadingAloud
    AttachmentChoiceKind.FILE,
    AttachmentChoiceKind.CAMERA,
    AttachmentChoiceKind.PHOTO_LIBRARY,
    AttachmentChoiceKind.SCHEDULE_TASK,
    AttachmentChoiceKind.NEW_PROJECT,
    -> false
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
