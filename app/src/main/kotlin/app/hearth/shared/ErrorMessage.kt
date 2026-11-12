package app.hearth.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hearth.R

/** A failure in the user's language. Only a server's own complaint is shown as the server worded it. */
@Composable
fun errorMessage(error: ErrorViewConfiguration): String = when (error.kind) {
    ErrorKind.SERVER_NOT_CONFIGURED -> stringResource(R.string.error_server_not_configured)
    ErrorKind.NO_MODEL -> stringResource(R.string.error_no_model)
    ErrorKind.UNREACHABLE -> stringResource(R.string.error_unreachable)
    ErrorKind.SAVE_FAILED -> stringResource(R.string.error_save_failed)
    ErrorKind.SERVER -> error.detail.ifEmpty { stringResource(R.string.error_title) }
    ErrorKind.FILES_UNREADABLE -> stringResource(R.string.error_files_unreadable)
    ErrorKind.SPEECH_NOT_CONFIGURED -> stringResource(R.string.error_speech_not_configured)
    ErrorKind.SPEECH_FAILED -> stringResource(R.string.error_speech_failed)
    ErrorKind.DICTATION_DENIED -> stringResource(R.string.error_dictation_denied)
    ErrorKind.DICTATION_UNAVAILABLE -> stringResource(R.string.error_dictation_unavailable)
}
