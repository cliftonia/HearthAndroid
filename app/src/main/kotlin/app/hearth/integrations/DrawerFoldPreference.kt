package app.hearth.integrations

import android.content.Context
import androidx.core.content.edit

/** Which sections of the chat list are folded away, as `DrawerFolds` writes them, kept from one launch to the next. */
class DrawerFoldPreference(
    val load: () -> String,
    val save: (String) -> Unit,
) {
    companion object {
        private const val FILE = "layout"
        private const val FOLDS_KEY = "drawerFoldedSections"

        /** Kept in the app's own preferences, on the device only. */
        fun live(context: Context): DrawerFoldPreference {
            val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return DrawerFoldPreference(
                load = { preferences.getString(FOLDS_KEY, "").orEmpty() },
                save = { value -> preferences.edit { putString(FOLDS_KEY, value) } },
            )
        }
    }
}
