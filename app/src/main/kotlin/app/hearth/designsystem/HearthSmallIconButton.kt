package app.hearth.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp

/**
 * A symbol drawn smaller than a finger, such as the button that takes a photo off a message. It takes only
 * [sideLength] in its layout and is drawn at that size, but answers to a touch, and is heard by a screen reader,
 * over a whole touch target round it.
 */
@Composable
fun HearthSmallIconButton(
    icon: ImageVector,
    label: String,
    sideLength: Dp,
    modifier: Modifier = Modifier,
    tint: Color = HearthTheme.colors.ink,
    containerColor: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    // The touch target spills out round the drawn circle without moving anything beside it.
    Box(modifier.size(sideLength).wrapContentSize(unbounded = true)) {
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClickLabel = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(sideLength)
                    .clip(HearthTheme.shapes.circle)
                    .background(containerColor)
                    .indication(interactions, ripple()),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, label, tint = tint, modifier = Modifier.size(HearthTheme.sizes.iconL)) }
        }
    }
}
