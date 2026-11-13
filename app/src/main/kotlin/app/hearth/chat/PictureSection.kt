package app.hearth.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import app.hearth.R
import app.hearth.designsystem.HearthTheme
import app.hearth.integrations.PictureLibrary
import app.hearth.shared.sharePicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A picture the app made, shown in a reply, with ways to keep it: save it to the phone's Pictures, or share it. */
@Composable
internal fun PictureSection(picture: String, pictures: PictureLibrary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A picture can be large, so it is unpacked off the main thread; nothing is drawn until it is ready.
    val bitmap by produceState<Bitmap?>(null, picture) { value = withContext(Dispatchers.Default) { PictureLibrary.bitmap(picture) } }
    val shown = bitmap ?: return

    fun save() {
        scope.launch {
            val message = if (pictures.save(picture)) R.string.picture_saved else R.string.picture_save_failed
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val leave = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) save() else Toast.makeText(context, R.string.picture_save_failed, Toast.LENGTH_SHORT).show()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m)) {
        Image(
            shown.asImageBitmap(),
            stringResource(R.string.picture_title),
            modifier = Modifier
                .sizeIn(maxWidth = HearthTheme.sizes.pictureMaxSide, maxHeight = HearthTheme.sizes.pictureMaxSide)
                .aspectRatio(shown.width.toFloat() / shown.height)
                .clip(HearthTheme.shapes.card),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)) {
            PictureButton(Icons.Outlined.SaveAlt, stringResource(R.string.picture_save)) {
                val storage = Manifest.permission.WRITE_EXTERNAL_STORAGE
                val hasLeave = ContextCompat.checkSelfPermission(context, storage) == PackageManager.PERMISSION_GRANTED
                if (PictureLibrary.needsLeaveToSave && !hasLeave) leave.launch(storage) else save()
            }
            PictureButton(Icons.Outlined.Share, stringResource(R.string.message_share)) {
                scope.launch { pictures.shareAddress(picture)?.let { sharePicture(context, it, PictureLibrary.JPEG_TYPE) } }
            }
        }
    }
}

/** One of the ways to keep a picture: a symbol and a word in an outlined pill. */
@Composable
private fun PictureButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = HearthTheme.colors
    Row(
        Modifier
            .clip(HearthTheme.shapes.pill)
            .border(HearthTheme.strokes.hairline, colors.line, HearthTheme.shapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HearthTheme.spacing.xl, vertical = HearthTheme.spacing.chipInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s),
    ) {
        Icon(icon, null, tint = colors.ink, modifier = Modifier.size(HearthTheme.sizes.iconXs))
        Text(label, color = colors.ink, style = HearthTheme.type.footnote)
    }
}
