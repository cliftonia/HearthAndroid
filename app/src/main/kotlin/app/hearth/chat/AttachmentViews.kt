package app.hearth.chat

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
import app.hearth.R
import app.hearth.designsystem.HearthSmallIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.AttachedDocument
import app.hearth.integrations.AttachmentLoader

/** One attached photo, shown small. A photo that will not decode leaves a plain tile in its place. */
@Composable
fun PhotoThumbnail(base64: String, sideLength: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(base64) { AttachmentLoader.bitmap(base64)?.asImageBitmap() }
    val shape = HearthTheme.shapes.tile
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

/** The name of one attached file. [trailing] is room for a button at its end, such as one to take the file off. */
@Composable
fun DocumentChip(document: AttachedDocument, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val colors = HearthTheme.colors
    val label = stringResource(R.string.message_attached_file, document.name)
    Row(
        modifier
            .background(colors.bubble, HearthTheme.shapes.tile)
            .padding(start = HearthTheme.spacing.l)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s),
    ) {
        Icon(Icons.Outlined.Description, null, tint = colors.pine, modifier = Modifier.size(HearthTheme.sizes.iconS))
        Text(
            document.name,
            color = colors.ink,
            style = HearthTheme.type.footnote,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = HearthTheme.sizes.chipLabelMaxWidth).padding(vertical = HearthTheme.spacing.chipInsetTall),
        )
        trailing()
        Box(Modifier.size(HearthTheme.spacing.xs))
    }
}

/** The photos and files waiting to go with the next message, each with a way to take it off again. */
@Composable
fun PendingAttachments(viewModel: ChatViewModel, presenter: ChatPresenter, modifier: Modifier = Modifier) {
    if (viewModel.pendingImages.isEmpty() && viewModel.pendingDocuments.isEmpty()) return
    val colors = HearthTheme.colors
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        viewModel.pendingImages.forEachIndexed { index, image ->
            Box {
                PhotoThumbnail(image, HearthTheme.sizes.thumbnailS)
                HearthSmallIconButton(
                    Icons.Outlined.Close,
                    stringResource(R.string.composer_remove_photo),
                    HearthTheme.sizes.controlXs,
                    modifier = Modifier.align(Alignment.TopEnd).padding(HearthTheme.spacing.xxs),
                    containerColor = colors.card,
                ) { presenter.removeImageAction(index) }
            }
        }
        viewModel.pendingDocuments.forEach { document ->
            DocumentChip(document) {
                HearthSmallIconButton(
                    Icons.Outlined.Close,
                    stringResource(R.string.composer_remove_file, document.name),
                    HearthTheme.sizes.controlS,
                    tint = colors.mute,
                ) { presenter.removeDocumentAction(document.id) }
            }
        }
    }
}
