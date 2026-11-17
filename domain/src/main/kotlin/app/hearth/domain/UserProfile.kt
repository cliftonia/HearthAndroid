package app.hearth.domain

/** Who the user is, as far as they have said: their name in full and what they like to be called. */
class UserProfile(fullName: String = "", nickname: String = "") {
    val fullName: String = singleLine(fullName)
    val nickname: String = singleLine(nickname)

    /** Whether neither name has been given. */
    val isEmpty: Boolean get() = fullName.isEmpty() && nickname.isEmpty()

    /** What to call the user: the nickname, or failing that the first word of the full name. `null` when neither is given. */
    val preferredName: String?
        get() = nickname.ifEmpty { null } ?: words(fullName).firstOrNull()

    /** The name to show beside the avatar: the full name, or failing that the nickname. `null` when neither is given. */
    val displayName: String?
        get() = listOf(fullName, nickname).firstOrNull { it.isNotEmpty() }

    /**
     * Up to two letters for the avatar: the first of the first and last words of the full name, or of the
     * nickname when there is no full name. Empty when neither is given.
     */
    val initials: String
        get() {
            val words = words(fullName.ifEmpty { nickname })
            val chosen = if (words.size > 1) listOf(words.first(), words.last()) else words
            return chosen.joinToString("") { word -> word.take(Character.charCount(word.codePointAt(0))).uppercase() }
        }

    override fun equals(other: Any?): Boolean = other is UserProfile && other.fullName == fullName && other.nickname == nickname

    override fun hashCode(): Int = 31 * fullName.hashCode() + nickname.hashCode()

    override fun toString(): String = "UserProfile(fullName=$fullName, nickname=$nickname)"

    private companion object {
        /** A name trimmed and on one line, so that it reads as a name wherever it is put. */
        fun singleLine(name: String): String = name.split(TextPatterns.NEWLINES).filter { it.isNotEmpty() }.joinToString(" ").trim()

        fun words(name: String): List<String> = name.split(" ").filter { it.isNotEmpty() }
    }
}
