package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.ConnectionTest
import app.hearth.R
import app.hearth.SettingsPresenter
import app.hearth.domain.Appearance
import app.hearth.domain.Memory
import app.hearth.domain.ServerSettings

@Composable
fun SettingsScreen(
    presenter: SettingsPresenter,
    /** Asks the phone to confirm who is holding it, and reports whether it did. */
    authenticate: (onResult: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by presenter.settings.collectAsStateWithLifecycle()
    val memories by presenter.memories.collectAsStateWithLifecycle()
    val test by presenter.connectionTest.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            HearthIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.settings_back), onClick = presenter::doneAction)
            Text(stringResource(R.string.drawer_settings), style = HearthTheme.brand, color = HearthTheme.colors.ink, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            ServerCard(settings, test, presenter)
            InstructionsCard(settings, presenter)
            SearchCard(settings, presenter)
            VoiceCard(settings, presenter)
            MemoryCard(settings, memories, presenter)
            PrivacyCard(settings, presenter, authenticate)
            ThemeCard(settings, presenter)
        }
    }
}

/** A titled group of settings, with a note beneath it. */
@Composable
private fun Card(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = HearthTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = colors.mute, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        Column(
            Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        footer?.let { Text(it, color = colors.mute, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun SwitchRow(label: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(isOn, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HearthTheme.colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = isOn, onCheckedChange = null)
    }
}

@Composable
private fun AddressField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrect = false, capitalization = KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ServerCard(settings: ServerSettings, test: ConnectionTest?, presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    Card(stringResource(R.string.settings_servers), stringResource(R.string.settings_servers_footer)) {
        AddressField(stringResource(R.string.settings_ollama), settings.ollamaAddress) { text -> presenter.updateAction { it.copy(ollamaAddress = text) } }
        AddressField(stringResource(R.string.settings_search), settings.searchAddress) { text -> presenter.updateAction { it.copy(searchAddress = text) } }
        AddressField(stringResource(R.string.settings_speech), settings.speechAddress) { text -> presenter.updateAction { it.copy(speechAddress = text) } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = presenter::testConnectionAction, enabled = test != ConnectionTest.Checking) {
                Text(stringResource(R.string.settings_test))
            }
            val (message, colour) = when (test) {
                null -> "" to colors.mute
                is ConnectionTest.Checking -> stringResource(R.string.settings_checking) to colors.mute
                is ConnectionTest.Succeeded -> pluralStringResource(R.plurals.settings_test_ok, test.modelCount, test.modelCount) to colors.online
                is ConnectionTest.InvalidAddress -> stringResource(R.string.settings_invalid_address) to colors.danger
                is ConnectionTest.Failed -> errorMessage(test.error) to colors.danger
            }
            Text(message, color = colour, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        }
    }
}

/** Which voice reads replies, chosen from those the speech server offers or typed by hand. */
@Composable
private fun VoiceCard(settings: ServerSettings, presenter: SettingsPresenter) {
    if (settings.speechUrl == null) return
    val voices by presenter.voices.collectAsStateWithLifecycle()
    var isOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settings.speechUrl) { presenter.loadVoicesAction() }
    Card(stringResource(R.string.settings_voice)) {
        Box {
            OutlinedTextField(
                value = settings.voice,
                onValueChange = { text -> presenter.updateAction { it.copy(voice = text.trim()) } },
                label = { Text(stringResource(R.string.settings_voice_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrect = false, capitalization = KeyboardCapitalization.None),
                trailingIcon = {
                    if (voices.isNotEmpty()) {
                        HearthIconButton(Icons.Outlined.ExpandMore, stringResource(R.string.settings_voice), tint = HearthTheme.colors.mute) { isOpen = true }
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
        SwitchRow(stringResource(R.string.composer_read_replies), settings.isAutoReadEnabled) { isOn -> presenter.updateAction { it.copy(isAutoReadEnabled = isOn) } }
    }
}

@Composable
private fun PrivacyCard(settings: ServerSettings, presenter: SettingsPresenter, authenticate: (onResult: (Boolean) -> Unit) -> Unit) {
    Card(stringResource(R.string.settings_privacy), stringResource(R.string.settings_require_unlock_footer)) {
        SwitchRow(stringResource(R.string.settings_require_unlock), settings.isLockEnabled) { isOn ->
            // Turning the lock on or off both need the holder of the phone to prove who they are.
            authenticate { isConfirmed -> if (isConfirmed) presenter.updateAction { it.copy(isLockEnabled = isOn) } }
        }
    }
}

@Composable
private fun InstructionsCard(settings: ServerSettings, presenter: SettingsPresenter) {
    Card(stringResource(R.string.settings_instructions), stringResource(R.string.settings_instructions_footer)) {
        OutlinedTextField(
            value = settings.customInstructions,
            onValueChange = { text -> presenter.updateAction { it.copy(customInstructions = text) } },
            placeholder = { Text(stringResource(R.string.settings_instructions_placeholder)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 3,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(stringResource(R.string.settings_follow_ups), settings.isFollowUpsEnabled) { isOn -> presenter.updateAction { it.copy(isFollowUpsEnabled = isOn) } }
    }
}

@Composable
private fun SearchCard(settings: ServerSettings, presenter: SettingsPresenter) {
    Card(stringResource(R.string.composer_search), stringResource(R.string.settings_read_pages_footer)) {
        SwitchRow(stringResource(R.string.settings_read_pages), settings.isPageReadingEnabled) { isOn -> presenter.updateAction { it.copy(isPageReadingEnabled = isOn) } }
    }
}

@Composable
private fun MemoryCard(settings: ServerSettings, memories: List<Memory>, presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    var draft by rememberSaveable { mutableStateOf("") }
    var isConfirmingClear by remember { mutableStateOf(false) }
    Card(stringResource(R.string.memory_title), stringResource(R.string.memory_footer)) {
        SwitchRow(stringResource(R.string.memory_toggle), settings.isMemoryEnabled) { isOn -> presenter.updateAction { it.copy(isMemoryEnabled = isOn) } }
    }
    if (!settings.isMemoryEnabled) return
    Card(stringResource(R.string.memory_title), stringResource(R.string.memory_list_footer)) {
        if (memories.isEmpty()) Text(stringResource(R.string.memory_none), color = colors.mute, fontSize = 14.sp)
        memories.forEach { memory ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(memory.text, color = colors.ink, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                HearthIconButton(Icons.Outlined.Close, stringResource(R.string.memory_delete, memory.text), tint = colors.mute) {
                    presenter.deleteMemoryAction(memory.id)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(200) },
                placeholder = { Text(stringResource(R.string.memory_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            HearthIconButton(Icons.Outlined.Add, stringResource(R.string.memory_add), tint = colors.pine) {
                if (draft.isNotBlank()) presenter.addMemoryAction(draft)
                draft = ""
            }
        }
        if (memories.isNotEmpty()) {
            TextButton(onClick = { isConfirmingClear = true }) { Text(stringResource(R.string.memory_clear), color = colors.danger) }
        }
    }
    if (isConfirmingClear) {
        AlertDialog(
            onDismissRequest = { isConfirmingClear = false },
            title = { Text(stringResource(R.string.memory_clear_question)) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingClear = false
                    presenter.clearMemoriesAction()
                }) { Text(stringResource(R.string.memory_clear), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingClear = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun ThemeCard(settings: ServerSettings, presenter: SettingsPresenter) {
    val titles = mapOf(
        Appearance.SYSTEM to R.string.settings_theme_system,
        Appearance.LIGHT to R.string.settings_theme_light,
        Appearance.DARK to R.string.settings_theme_dark,
    )
    Card(stringResource(R.string.settings_theme)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Appearance.entries.forEachIndexed { index, appearance ->
                SegmentedButton(
                    selected = settings.appearance == appearance,
                    onClick = { presenter.updateAction { it.copy(appearance = appearance) } },
                    shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size),
                ) { Text(stringResource(titles.getValue(appearance))) }
            }
        }
    }
}
