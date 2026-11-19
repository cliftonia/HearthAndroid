package app.hearth.integrations

import android.content.Context
import androidx.core.content.edit

/** Whether the user has put the chat list away on a wide screen, kept from one launch to the next. */
class SideListPreference(
    val isHidden: () -> Boolean,
    val setHidden: (Boolean) -> Unit,
) {
    companion object {
        private const val FILE = "layout"
        private const val HIDDEN_KEY = "isSideListHidden"

        /** Kept in the app's own preferences, on the device only. */
        fun live(context: Context): SideListPreference {
            val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return SideListPreference(
                isHidden = { preferences.getBoolean(HIDDEN_KEY, false) },
                setHidden = { isHidden -> preferences.edit { putBoolean(HIDDEN_KEY, isHidden) } },
            )
        }
    }
}
