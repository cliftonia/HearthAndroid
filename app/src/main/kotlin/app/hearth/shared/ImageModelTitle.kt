package app.hearth.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hearth.R
import app.hearth.domain.ImageModelKind

/** The picture model's name with a word on what it is best at. */
@Composable
fun ImageModelKind.title(): String = stringResource(
    when (this) {
        ImageModelKind.Z_IMAGE_TURBO -> R.string.picture_model_z_image
        ImageModelKind.KREA2_TURBO -> R.string.picture_model_krea
        ImageModelKind.JUGGERNAUT_XL -> R.string.picture_model_juggernaut
    },
)

/** The picture model's name alone, as it is known. */
@Composable
fun ImageModelKind.shortName(): String = stringResource(
    when (this) {
        ImageModelKind.Z_IMAGE_TURBO -> R.string.picture_model_name_z_image
        ImageModelKind.KREA2_TURBO -> R.string.picture_model_name_krea
        ImageModelKind.JUGGERNAUT_XL -> R.string.picture_model_name_juggernaut
    },
)
