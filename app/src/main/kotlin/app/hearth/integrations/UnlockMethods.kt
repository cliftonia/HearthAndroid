package app.hearth.integrations

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.hearth.shared.UnlockMethodKind

/** What the phone has set up to confirm who is holding it. */
fun unlockMethod(context: Context): UnlockMethodKind {
    if (BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) return UnlockMethodKind.BIOMETRIC
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    return if (keyguard?.isDeviceSecure == true) UnlockMethodKind.SCREEN_LOCK else UnlockMethodKind.NONE
}

/** [unlockMethod], asked again each time the app comes back, since the user may have been to the phone's settings. */
@Composable
fun rememberUnlockMethod(): UnlockMethodKind {
    val context = LocalContext.current
    var method by remember { mutableStateOf(unlockMethod(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { method = unlockMethod(context) }
    return method
}
