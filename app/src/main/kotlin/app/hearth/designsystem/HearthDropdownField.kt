package app.hearth.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A choice of one from a list, as Material's exposed dropdown menu: an outlined field holding the [label] and the
 * [value] chosen, with the ▼ that opens the menu under it. [menu] lays out the menu's items; each is handed `close`,
 * to put the menu away once it is chosen. A line beneath, [supportingText], says more about the choice where given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearthDropdownField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var isOpen by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = isOpen, onExpandedChange = { isOpen = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isOpen) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) { menu { isOpen = false } }
    }
}
