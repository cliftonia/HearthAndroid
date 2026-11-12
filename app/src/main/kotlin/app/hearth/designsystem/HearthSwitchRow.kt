package app.hearth.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** A label with a switch at its end. The whole row is the switch, so it is one thing to tap and one thing to hear. */
@Composable
fun HearthSwitchRow(label: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(isOn, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HearthTheme.colors.ink, style = HearthTheme.type.subhead, modifier = Modifier.weight(1f).padding(end = HearthTheme.spacing.xl))
        Switch(checked = isOn, onCheckedChange = null)
    }
}
