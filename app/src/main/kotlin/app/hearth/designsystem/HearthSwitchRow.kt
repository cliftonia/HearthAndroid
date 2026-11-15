package app.hearth.designsystem

import androidx.compose.foundation.layout.Column
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

/**
 * A label with a switch at its end, and beneath the label, where given, one line on what the switch does. The
 * whole row is the switch, so it is one thing to tap and one thing to hear. Throwing it is felt as a light tap.
 */
@Composable
fun HearthSwitchRow(label: String, isOn: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(
            isOn,
            role = Role.Switch,
            onValueChange = {
                Haptics.play(HapticKind.LIGHT)
                onChange(it)
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = HearthTheme.spacing.xl)) {
            Text(label, color = HearthTheme.colors.ink, style = HearthTheme.type.subhead)
            detail?.let { Text(it, color = HearthTheme.colors.mute, style = HearthTheme.type.footnoteSnug) }
        }
        Switch(checked = isOn, onCheckedChange = null)
    }
}
