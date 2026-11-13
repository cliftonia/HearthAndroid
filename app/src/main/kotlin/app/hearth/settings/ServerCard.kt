package app.hearth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ServerSettings
import app.hearth.shared.errorMessage

/** The addresses of the chat, search, speech and picture servers, and a button that tries the first of them. */
@Composable
internal fun ServerCard(settings: ServerSettings, test: ConnectionTest?, presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    HearthCard(stringResource(R.string.settings_servers), stringResource(R.string.settings_servers_footer)) {
        AddressField(stringResource(R.string.settings_ollama), settings.ollamaAddress) { text -> presenter.updateAction { it.copy(ollamaAddress = text) } }
        AddressField(stringResource(R.string.settings_search), settings.searchAddress) { text -> presenter.updateAction { it.copy(searchAddress = text) } }
        AddressField(stringResource(R.string.settings_speech), settings.speechAddress) { text -> presenter.updateAction { it.copy(speechAddress = text) } }
        AddressField(stringResource(R.string.settings_image), settings.imageAddress) { text -> presenter.updateAction { it.copy(imageAddress = text) } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xl)) {
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
            Text(message, color = colour, style = HearthTheme.type.footnoteSnug, modifier = Modifier.weight(1f))
        }
    }
}

/** A one-line field for a server address, with a keyboard that neither corrects nor capitalises it. */
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
