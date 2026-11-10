package app.hearth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hearth.ChatPresenter
import app.hearth.ChatViewModel
import app.hearth.R
import app.hearth.domain.AttachedDocument
import app.hearth.integrations.AttachmentLoader
import app.hearth.removeDocumentAction
import app.hearth.removeImageAction

/** One attached photo, shown small. */
@Composable
fun PhotoThumbnail(base64: String, sideLength: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(base64) { AttachmentLoader.bitmap(base64)?.asImageBitmap() }
    val shape = RoundedCornerShape(12.dp)
    if (bitmap == null) {
        Box(modifier.size(sideLength).background(HearthTheme.colors.bubble, shape))
    } else {
        Image(
            bitmap,
            stringResource(R.string.message_attached_photo),
            contentScale = ContentScale.Crop,
            modifier = modifier.size(sideLength).clip(shape),
        )
    }
}

/** The name of one attached file. */
@Composable
fun DocumentChip(document: AttachedDocument, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val colors = HearthTheme.colors
    val label = stringResource(R.string.message_attached_file, document.name)
    Row(
        modifier.background(colors.bubble, RoundedCornerShape(12.dp)).padding(start = 10.dp).semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Description, null, tint = colors.pine, modifier = Modifier.size(18.dp))
        Text(
            document.name,
            color = colors.ink,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp).padding(vertical = 9.dp),
        )
        trailing()
        Box(Modifier.size(4.dp))
    }
}

/** The photos and files waiting to go with the next message, each with a way to take it off again. */
@Composable
fun PendingAttachments(viewModel: ChatViewModel, presenter: ChatPresenter, modifier: Modifier = Modifier) {
    if (viewModel.pendingImages.isEmpty() && viewModel.pendingDocuments.isEmpty()) return
    val colors = HearthTheme.colors
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        viewModel.pendingImages.forEachIndexed { index, image ->
            Box {
                PhotoThumbnail(image, 64.dp)
                HearthIconButton(
                    Icons.Outlined.Close,
                    stringResource(R.string.composer_remove_photo),
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(24.dp).background(colors.card, CircleShape),
                ) { presenter.removeImageAction(index) }
            }
        }
        viewModel.pendingDocuments.forEach { document ->
            DocumentChip(document) {
                HearthIconButton(
                    Icons.Outlined.Close,
                    stringResource(R.string.composer_remove_file, document.name),
                    modifier = Modifier.size(32.dp),
                    tint = colors.mute,
                ) { presenter.removeDocumentAction(document.id) }
            }
        }
    }
}
