package app.hearth.shared

/**
 * How the phone can confirm who is holding it, which the lock's switch and the lock screen are named for. Android
 * cannot say reliably which kind of biometric is set up, so a fingerprint and a face are named together.
 */
enum class UnlockMethodKind {
    /** A fingerprint or a face is set up, with the screen lock behind it. */
    BIOMETRIC,

    /** Only the screen lock: a PIN, a pattern or a password. */
    SCREEN_LOCK,

    /** Nothing is set up, so the phone cannot confirm anyone. */
    NONE,
}
