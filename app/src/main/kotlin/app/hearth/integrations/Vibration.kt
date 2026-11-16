package app.hearth.integrations

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

/** Whether the device has anything to tap with. A tablet often has not, and then there is no haptics switch to offer. */
fun hasVibrator(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.hasVibrator() == true
} else {
    context.getSystemService(Vibrator::class.java)?.hasVibrator() == true
}
