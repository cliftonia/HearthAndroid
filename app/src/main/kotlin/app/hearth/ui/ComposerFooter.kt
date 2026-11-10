package app.hearth.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.hearth.ChatPresenter
import app.hearth.ChatViewModel
import app.hearth.R
import app.hearth.addDocumentsAction
import app.hearth.addImagesAction
import app.hearth.cancelEditAction
import app.hearth.dictationAction
import app.hearth.dictationDeniedAction
import app.hearth.domain.ServerSettings
import app.hearth.domain.ThinkingLevel
import app.hearth.integrations.AttachmentLoader
import app.hearth.toggleAutoReadAction
import kotlinx.coroutines.launch

@Composable
fun ThinkingLevel.title(): String = stringResource(
    when (this) {
        ThinkingLevel.OFF -> R.string.composer_thinking_off
        ThinkingLevel.LOW -> R.string.composer_thinking_light
        ThinkingLevel.MEDIUM -> R.string.composer_thinking_medium
        ThinkingLevel.HIGH -> R.string.composer_thinking_high
    },
)

/** Where a message is written, with the controls that shape the reply. */
@Composable
fun ComposerFooter(
    viewModel: ChatViewModel,
    settings: ServerSettings,
    presenter: ChatPresenter,
    attachments: AttachmentLoader,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val model = viewModel.selectedModel
    val placeholder = model?.let { stringResource(R.string.composer_placeholder_model, it.displayName) } ?: stringResource(R.string.composer_placeholder)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(colors.card, RoundedCornerShape(24.dp))
            .border(1.dp, colors.line, RoundedCornerShape(24.dp))
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
    ) {
        if (viewModel.editingMessageId != null) {
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.composer_editing), color = colors.pine, fontSize = 13.sp, modifier = Modifier.weight(1f))
                HearthIconButton(Icons.Outlined.Close, stringResource(R.string.composer_cancel_edit), tint = colors.mute, onClick = presenter::cancelEditAction)
            }
        }
        PendingAttachments(viewModel, presenter, Modifier.padding(start = 8.dp, bottom = 8.dp))
        BasicTextField(
            value = viewModel.draft,
            onValueChange = presenter::draftAction,
            textStyle = TextStyle(color = colors.ink, fontSize = 16.sp, lineHeight = 22.sp),
            cursorBrush = SolidColor(colors.pine),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp, max = 140.dp).padding(horizontal = 8.dp).semantics { contentDescription = placeholder },
            decorationBox = { field ->
                Box {
                    if (viewModel.draft.isEmpty()) Text(placeholder, color = colors.mute, fontSize = 16.sp, maxLines = 1)
                    field()
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                AttachMenu(canSee = model?.canSee == true, presenter = presenter, attachments = attachments)
                if (model?.canThink == true) ThinkingMenu(settings.thinkingLevel, presenter::thinkingLevelAction)
                // A model that can call tools searches when it sees fit; any other must be told to.
                if (model != null && !model.canUseTools && settings.searchUrl != null) {
                    Toggle(Icons.Outlined.Language, stringResource(R.string.composer_search), viewModel.isSearchForced, presenter::toggleSearchAction)
                }
                if (settings.speechUrl != null) {
                    val icon = if (settings.isAutoReadEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff
                    Toggle(icon, stringResource(R.string.composer_read_replies), settings.isAutoReadEnabled, presenter::toggleAutoReadAction)
                }
            }
            DictationButton(viewModel.isDictating, presenter)
            SendButton(viewModel, canTalk = settings.speechUrl != null, presenter = presenter)
        }
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, isOn: Boolean, onToggle: () -> Unit) {
    IconToggleButton(checked = isOn, onCheckedChange = { onToggle() }) {
        Icon(icon, label, tint = if (isOn) HearthTheme.colors.pine else HearthTheme.colors.mute, modifier = Modifier.size(22.dp))
    }
}

/** The ways to add a photo or a file to the message being written. */
@Composable
private fun AttachMenu(canSee: Boolean, presenter: ChatPresenter, attachments: AttachmentLoader) {
    val scope = rememberCoroutineScope()
    var isOpen by remember { mutableStateOf(false) }
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
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris -> addPhotos(uris.map(Uri::toString)) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { wasTaken ->
        if (wasTaken) addPhotos(listOfNotNull(cameraAddress))
    }
    Box {
        HearthIconButton(Icons.Outlined.Add, stringResource(R.string.composer_attach), tint = HearthTheme.colors.mute) { isOpen = true }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.composer_choose_file)) },
                onClick = {
                    isOpen = false
                    files.launch(arrayOf("application/pdf", "text/*", "application/json", "application/xml"))
                },
            )
            // Photos are offered only to models that can look at them.
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

@Composable
private fun DictationButton(isDictating: Boolean, presenter: ChatPresenter) {
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

@Composable
private fun ThinkingMenu(level: ThinkingLevel, onSelect: (ThinkingLevel) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    val colors = HearthTheme.colors
    val openLabel = stringResource(R.string.composer_thinking)
    Box {
        TextButton(onClick = { isOpen = true }, modifier = Modifier.semantics { onClick(label = openLabel, action = null) }) {
            Icon(
                Icons.Outlined.Lightbulb,
                openLabel,
                tint = if (level == ThinkingLevel.OFF) colors.mute else colors.pine,
                modifier = Modifier.padding(end = 4.dp).size(18.dp),
            )
            Text(level.title(), color = if (level == ThinkingLevel.OFF) colors.mute else colors.pine, fontSize = 14.sp)
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
private fun SendButton(viewModel: ChatViewModel, canTalk: Boolean, presenter: ChatPresenter) {
    val colors = HearthTheme.colors
    val offersVoice = !viewModel.isStreaming && !viewModel.canSend && canTalk && viewModel.selectedModel != null
    val isEnabled = viewModel.isStreaming || viewModel.canSend || offersVoice
    val (icon, label) = when {
        viewModel.isStreaming -> Icons.Filled.Stop to R.string.composer_stop
        offersVoice -> Icons.Filled.GraphicEq to R.string.composer_voice
        else -> Icons.Filled.ArrowUpward to R.string.composer_send
    }
    IconButton(
        onClick = {
            when {
                viewModel.isStreaming -> presenter.stopAction()
                offersVoice -> presenter.voiceAction()
                else -> presenter.sendAction()
            }
        },
        enabled = isEnabled,
        modifier = Modifier.padding(4.dp).size(40.dp).background(if (isEnabled) colors.pine else colors.line, CircleShape),
    ) {
        Icon(icon, stringResource(label), tint = if (isEnabled) colors.onPine else colors.mute, modifier = Modifier.size(20.dp))
    }
}
