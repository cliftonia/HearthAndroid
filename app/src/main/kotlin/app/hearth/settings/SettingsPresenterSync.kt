package app.hearth.settings

import android.util.Log
import app.hearth.domain.SyncFailureKind
import app.hearth.domain.SyncHubCheck
import app.hearth.domain.SyncSettings
import app.hearth.domain.SyncStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// The sync page: where the hub is, its token, the switch, and syncing now. Each takes effect at once, like the
// notification switch. The address typed is kept when it is submitted, when the page is left, and before anything
// that uses it.

/** What the sync page is built from. */
class SyncControls(
    val store: SyncStore,
    /** Runs a sync now and returns when it is done. */
    val syncNow: suspend () -> Unit,
    /** Checks the hub at an address, with a token or none. */
    val check: suspend (String, String?) -> SyncHubCheck,
)

/** How the last test of the hub went. */
sealed interface SyncCheck {
    data object Checking : SyncCheck

    data object Succeeded : SyncCheck

    data class Failed(val failure: SyncFailureKind) : SyncCheck
}

/** What the status line says, for the page to put into words. */
sealed interface SyncStatusKind {
    data object Syncing : SyncStatusKind

    data class Failed(val failure: SyncFailureKind) : SyncStatusKind

    data class LastSynced(val at: Long) : SyncStatusKind

    data object Never : SyncStatusKind
}

/** The status line: syncing, the last failure, when it last synced, or that it has not. */
fun syncStatus(isSyncing: Boolean, failure: SyncFailureKind?, lastSyncedAt: Long?): SyncStatusKind = when {
    isSyncing -> SyncStatusKind.Syncing
    failure != null -> SyncStatusKind.Failed(failure)
    lastSyncedAt != null -> SyncStatusKind.LastSynced(lastSyncedAt)
    else -> SyncStatusKind.Never
}

internal val SettingsPresenter.syncStore: SyncStore get() = dependencies.sync.store

/** The address as typed on the sync page; kept by [syncAddressAction]. */
val SettingsPresenter.syncAddress: StateFlow<String> get() = syncAddressState

/** A new token being typed. The saved one is never put back in the field. */
val SettingsPresenter.syncTokenDraft: StateFlow<String> get() = syncTokenDraftState

/** How the last test of the hub went; `null` before one has been run. */
val SettingsPresenter.syncCheck: StateFlow<SyncCheck?> get() = syncCheckState

val SettingsPresenter.syncSettings: StateFlow<SyncSettings> get() = syncStore.settings
val SettingsPresenter.hasSyncToken: StateFlow<Boolean> get() = syncStore.hasToken
val SettingsPresenter.isSyncing: StateFlow<Boolean> get() = syncStore.isSyncing
val SettingsPresenter.syncFailure: StateFlow<SyncFailureKind?> get() = syncStore.failure
val SettingsPresenter.lastSyncedAt: StateFlow<Long?> get() = syncStore.lastSyncedAt

/** Whether the switch can be turned on: only with an address to sync with. */
fun canEnableSync(address: String): Boolean = SyncSettings(hubAddress = address).hasHub

/** Opens the sync page, filled from what is saved. The token is never read into it. */
fun SettingsPresenter.syncAction() {
    syncAddressState.value = syncStore.settings.value.hubAddress
    syncTokenDraftState.value = ""
    syncCheckState.value = null
    pageState.value = SettingsPageKind.SYNC
}

fun SettingsPresenter.syncAddressDraftAction(text: String) {
    syncAddressState.value = text
}

fun SettingsPresenter.syncTokenDraftAction(text: String) {
    syncTokenDraftState.value = text
}

/** Keeps the address as typed. With no usable address, sync is turned off. */
fun SettingsPresenter.syncAddressAction() {
    scope.launch { keepSyncSettings(syncStore.settings.value.isEnabled) }
}

/** Keeps the switch as it now stands, with the address typed, and syncs at once when it is turned on. */
fun SettingsPresenter.syncEnabledAction(isOn: Boolean) {
    scope.launch {
        val wasEnabled = syncStore.settings.value.isEnabled
        keepSyncSettings(isOn)
        if (syncStore.settings.value.isEnabled && !wasEnabled) dependencies.sync.syncNow()
    }
}

/**
 * Seals the typed token in the phone's keystore and empties the field. The typed address is kept first, so that the
 * token is saved for the hub it was typed for, and is not forgotten when that address is kept later.
 */
fun SettingsPresenter.saveSyncTokenAction() {
    scope.launch {
        keepSyncSettings(syncStore.settings.value.isEnabled)
        keepTypedToken()
    }
}

/** Forgets the saved token. */
fun SettingsPresenter.removeSyncTokenAction() {
    scope.launch { keepToken(null) }
}

/** Keeps what is typed, the address before the token since a new address forgets the token, and syncs now. */
fun SettingsPresenter.syncNowAction() {
    scope.launch {
        keepSyncSettings(syncStore.settings.value.isEnabled)
        keepTypedToken()
        dependencies.sync.syncNow()
    }
}

/** Checks that the hub at the typed address answers and lets this device in. */
fun SettingsPresenter.checkHubAction() {
    scope.launch {
        keepSyncSettings(syncStore.settings.value.isEnabled)
        keepTypedToken()
        syncCheckState.value = SyncCheck.Checking
        val url = syncStore.settings.value.hubUrl
        syncCheckState.value = if (url == null) {
            SyncCheck.Failed(SyncFailureKind.NotConfigured)
        } else {
            when (val check = dependencies.sync.check(url, syncStore.token())) {
                is SyncHubCheck.Reachable -> SyncCheck.Succeeded
                is SyncHubCheck.Failed -> SyncCheck.Failed(check.failure)
            }
        }
    }
}

/** Keeps the typed address with the switch as given; without a usable address the switch is off. */
private suspend fun SettingsPresenter.keepSyncSettings(isEnabled: Boolean) {
    val address = syncAddressState.value.trim()
    val settings = SyncSettings(hubAddress = address, isEnabled = isEnabled && canEnableSync(address))
    if (settings == syncStore.settings.value) return
    if (settings.hubUrl != syncStore.settings.value.hubUrl) syncCheckState.value = null
    syncStore.update(settings)
}

/** Keeps the token being typed, if there is one. */
private suspend fun SettingsPresenter.keepTypedToken() {
    val token = syncTokenDraftState.value.trim()
    if (token.isNotEmpty()) keepToken(token)
}

/** Seals the token, or forgets it with `null`, off the main thread: the keystore and the preferences are slow. */
private suspend fun SettingsPresenter.keepToken(token: String?) {
    try {
        syncStore.setToken(token)
        syncTokenDraftState.value = ""
        syncCheckState.value = null
    } catch (failure: Exception) {
        // Never the token itself, nor what the keystore said about it.
        Log.e(SettingsPresenter.TAG, "Keeping the hub token failed: ${failure.javaClass.simpleName}")
    }
}
