package app.hearth.shared

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R

/**
 * The snackbar that says a chat was deleted or a memory forgotten, with a button that undoes it. Material's snackbar
 * keeps the time, longer for a screen reader user, and says when it is over; the presenter carries the delete out then.
 */
@Composable
fun UndoSnackbarHost(presenter: UndoPresenter, modifier: Modifier = Modifier) {
    val held by presenter.held.collectAsStateWithLifecycle()
    val hostState = remember { SnackbarHostState() }
    val chatDeleted = stringResource(R.string.undo_chat_deleted)
    val memoryForgotten = stringResource(R.string.undo_memory_forgotten)
    val undo = stringResource(R.string.undo_action)
    // A new delete, or none, takes the place of the snackbar showing; the presenter has already carried the old one out.
    LaunchedEffect(held) {
        val current = held ?: return@LaunchedEffect
        val message = when (current.target) {
            is UndoTarget.Chat -> chatDeleted
            is UndoTarget.Memory -> memoryForgotten
        }
        when (hostState.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)) {
            SnackbarResult.ActionPerformed -> presenter.undoAction(current.serial)
            SnackbarResult.Dismissed -> presenter.expiredAction(current.serial)
        }
    }
    SnackbarHost(hostState, modifier)
}
