package app.hearth.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthPageRow
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.SyncFailureKind

/** The row in the settings that leads to the sync page, saying whether sync is on. */
@Composable
internal fun SyncCard(presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val settings by presenter.syncSettings.collectAsStateWithLifecycle()
    val state = stringResource(if (settings.isEnabled) R.string.sync_on else R.string.sync_off)
    HearthCard {
        HearthPageRow(stringResource(R.string.sync_open, state), presenter::syncAction) {
            Text(stringResource(R.string.sync_title), color = colors.ink, style = HearthTheme.type.subhead, modifier = Modifier.weight(1f))
            Text(state, color = colors.mute, style = HearthTheme.type.subhead)
        }
    }
}

/** Where the hub is, its access token, the switch, syncing now, and how the last sync went. Everything here takes effect at once. */
@Composable
internal fun SyncPage(presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val settings by presenter.syncSettings.collectAsStateWithLifecycle()
    val address by presenter.syncAddress.collectAsStateWithLifecycle()
    val tokenDraft by presenter.syncTokenDraft.collectAsStateWithLifecycle()
    val hasToken by presenter.hasSyncToken.collectAsStateWithLifecycle()
    val isSyncing by presenter.isSyncing.collectAsStateWithLifecycle()
    val failure by presenter.syncFailure.collectAsStateWithLifecycle()
    val lastSyncedAt by presenter.lastSyncedAt.collectAsStateWithLifecycle()
    val check by presenter.syncCheck.collectAsStateWithLifecycle()
    val canEnable = canEnableSync(address)
    HearthCard(footer = stringResource(R.string.sync_hub_footer)) {
        OutlinedTextField(
            value = address,
            onValueChange = presenter::syncAddressDraftAction,
            label = { Text(stringResource(R.string.sync_hub_address)) },
            placeholder = { Text("https://") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { presenter.syncAddressAction() }),
            modifier = Modifier.fillMaxWidth(),
        )
        TokenField(presenter, tokenDraft, hasToken)
    }
    HearthCard(footer = stringResource(if (canEnable) R.string.sync_switch_detail else R.string.sync_needs_address)) {
        HearthSwitchRow(stringResource(R.string.sync_switch), settings.isEnabled, enabled = canEnable) { isOn ->
            presenter.syncEnabledAction(isOn)
        }
    }
    HearthCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xl)) {
            val syncNow = stringResource(R.string.sync_now)
            OutlinedButton(
                onClick = presenter::syncNowAction,
                enabled = settings.isEnabled && !isSyncing,
                modifier = Modifier.semantics { contentDescription = syncNow },
            ) { Text(syncNow) }
            val test = stringResource(R.string.sync_check)
            OutlinedButton(
                onClick = presenter::checkHubAction,
                enabled = canEnable && check != SyncCheck.Checking,
                modifier = Modifier.semantics { contentDescription = test },
            ) { Text(test) }
        }
        check?.let { result ->
            val (message, colour) = when (result) {
                SyncCheck.Checking -> stringResource(R.string.settings_checking) to colors.mute
                SyncCheck.Succeeded -> stringResource(R.string.sync_check_succeeded) to colors.online
                is SyncCheck.Failed -> syncFailureWords(result.failure) to colors.danger
            }
            Text(
                message,
                color = colour,
                style = HearthTheme.type.footnoteSnug,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    HearthCard(title = stringResource(R.string.sync_status)) {
        val status = syncStatus(isSyncing, failure, lastSyncedAt)
        val words = syncStatusWords(status)
        val colour: Color = if (status is SyncStatusKind.Failed && status.failure !is SyncFailureKind.TooLarge) colors.danger else colors.mute
        Text(
            words,
            color = colour,
            style = HearthTheme.type.footnoteSnug,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** A field for a new token, never filled with the saved one, and the buttons that keep or forget it. */
@Composable
private fun TokenField(presenter: SettingsPresenter, draft: String, hasToken: Boolean) {
    OutlinedTextField(
        value = draft,
        onValueChange = presenter::syncTokenDraftAction,
        label = { Text(stringResource(R.string.sync_token)) },
        placeholder = { Text(stringResource(if (hasToken) R.string.sync_token_saved_placeholder else R.string.sync_token_placeholder)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrect = false,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { presenter.saveSyncTokenAction() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m)) {
        val save = stringResource(R.string.sync_save_token)
        TextButton(
            onClick = presenter::saveSyncTokenAction,
            enabled = draft.isNotBlank(),
            modifier = Modifier.semantics { contentDescription = save },
        ) { Text(save) }
        val remove = stringResource(R.string.sync_remove_token)
        TextButton(
            onClick = presenter::removeSyncTokenAction,
            enabled = hasToken,
            modifier = Modifier.semantics { contentDescription = remove },
        ) { Text(remove, color = if (hasToken) HearthTheme.colors.danger else Color.Unspecified) }
    }
    Text(stringResource(R.string.sync_token_footer), color = HearthTheme.colors.mute, style = HearthTheme.type.captionSnug)
}

/** The status line in words. */
@Composable
private fun syncStatusWords(status: SyncStatusKind): String = when (status) {
    SyncStatusKind.Syncing -> stringResource(R.string.sync_in_progress)
    SyncStatusKind.Never -> stringResource(R.string.sync_never)
    is SyncStatusKind.Failed -> syncFailureWords(status.failure)
    is SyncStatusKind.LastSynced -> {
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        stringResource(R.string.sync_last_synced, DateUtils.formatDateTime(LocalContext.current, status.at, flags))
    }
}

/** A failure in words the user can act on. */
@Composable
private fun syncFailureWords(failure: SyncFailureKind): String = when (failure) {
    SyncFailureKind.NotConfigured -> stringResource(R.string.sync_failed_not_configured)
    SyncFailureKind.Unreachable -> stringResource(R.string.sync_failed_unreachable)
    SyncFailureKind.TokenNeeded -> stringResource(R.string.sync_failed_token_needed)
    SyncFailureKind.TokenRefused -> stringResource(R.string.sync_failed_token_refused)
    SyncFailureKind.Unreadable -> stringResource(R.string.sync_failed_unreadable)
    is SyncFailureKind.HubFailed -> stringResource(R.string.sync_failed_hub, failure.status)
    is SyncFailureKind.TooLarge -> pluralStringResource(R.plurals.sync_too_large, failure.count, failure.count)
}
