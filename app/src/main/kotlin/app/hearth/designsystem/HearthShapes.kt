package app.hearth.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Every corner in the app, named for what it rounds, from the tightest to the fullest. */
class HearthShapes {
    /** A thin accent bar, such as the one beside a quotation. */
    val bar: Shape = RoundedCornerShape(2.dp)

    /** A line of text that can be tapped. */
    val control: Shape = RoundedCornerShape(8.dp)

    /** Small panels set into a reply: notices and tables. */
    val inset: Shape = RoundedCornerShape(10.dp)

    /** Photo thumbnails, file chips, rows of a list, code and the model's thinking. */
    val tile: Shape = RoundedCornerShape(12.dp)

    /** Search fields, banners and suggested questions. */
    val field: Shape = RoundedCornerShape(14.dp)

    /** A group of settings. */
    val card: Shape = RoundedCornerShape(16.dp)

    /** A message the user sent. */
    val bubble: Shape = RoundedCornerShape(20.dp)

    /** The composer. */
    val composer: Shape = RoundedCornerShape(24.dp)

    /** Fully rounded ends, whatever the height. */
    val pill: Shape = RoundedCornerShape(50)

    /** Round buttons, dots and halos. */
    val circle: Shape = CircleShape
}
