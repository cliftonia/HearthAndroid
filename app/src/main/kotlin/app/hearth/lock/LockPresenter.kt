package app.hearth.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app is locked, and the way to lock and unlock it.
 *
 * With the lock switched on the app starts locked. It has no delegate: what must happen when it locks is
 * handed in as [onLock], since the coordinator has other presenters to quieten.
 */
class LockPresenter(private val isLockEnabled: () -> Boolean, private val onLock: () -> Unit) {
    private val state = MutableStateFlow(isLockEnabled())
    val isLocked: StateFlow<Boolean> = state.asStateFlow()

    /** Called when the app leaves the screen: with the lock on, it must be unlocked afresh on return. */
    fun backgroundedAction() {
        if (!isLockEnabled() || state.value) return
        state.value = true
        onLock()
    }

    /** Called once the phone has confirmed who is holding it. */
    fun unlockedAction() {
        state.value = false
    }
}
