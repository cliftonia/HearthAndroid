package app.hearth.drawer

/** The sections of the chat list that fold away beneath their heading. The dated groups never fold. */
enum class DrawerFoldKind(val stored: String) {
    PINNED("pinned"),
    SCHEDULED_TASKS("scheduledTasks"),
    PROJECTS("projects"),
}

/** Which sections of the chat list are folded, kept as a short line of text so that it lasts between launches. */
object DrawerFolds {
    /** Reads the folds back from their stored text. Words it does not know, as a later version might write, are passed over. */
    fun parse(stored: String): Set<DrawerFoldKind> =
        stored.split(",").mapNotNull { word -> DrawerFoldKind.entries.firstOrNull { it.stored == word } }.toSet()

    /** The text to store, in a fixed order so that the same folds are always written the same way. */
    fun stored(folds: Set<DrawerFoldKind>): String =
        DrawerFoldKind.entries.filter { it in folds }.joinToString(",") { it.stored }

    /** Folds an open section, or opens a folded one. */
    fun toggled(folds: Set<DrawerFoldKind>, kind: DrawerFoldKind): Set<DrawerFoldKind> =
        if (kind in folds) folds - kind else folds + kind
}

/** The section this group folds as, or `null` for a dated group, which is always open. */
val DrawerSectionKind.foldKind: DrawerFoldKind?
    get() = when (this) {
        DrawerSectionKind.PINNED -> DrawerFoldKind.PINNED
        DrawerSectionKind.SCHEDULED_TASKS -> DrawerFoldKind.SCHEDULED_TASKS
        DrawerSectionKind.TODAY, DrawerSectionKind.YESTERDAY, DrawerSectionKind.PREVIOUS_WEEK, DrawerSectionKind.EARLIER -> null
    }
