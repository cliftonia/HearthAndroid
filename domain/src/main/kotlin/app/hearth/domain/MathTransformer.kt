package app.hearth.domain

/**
 * Rewrites LaTeX maths as plain Unicode text that reads well in a line: `x^2 \leq \frac{a}{b}` becomes `x² ≤ a/b`.
 *
 * This is a readable approximation, not typesetting: fractions are written on one line and
 * constructs with no plain-text form, such as matrices, are left close to how they were written.
 */
class MathTransformer {

    /** Replaces the maths inside `\( \)`, `$$ $$` and `$ $` in a line of prose, leaving the rest untouched. */
    fun readableInline(text: String): String {
        if ('$' !in text && "\\(" !in text) return text
        return INLINE_PATTERNS.fold(text) { result, pattern -> pattern.replace(result) { readable(it.groupValues[1]) } }
    }

    /** Rewrites one piece of LaTeX maths, given without its delimiters. */
    fun readable(latex: String): String {
        var text = latex
        // Fractions and roots may be nested, so those patterns are applied until they no longer match.
        text = replacing(TEXT_COMMAND, text, passes = 4) { it[1] }
        text = replacing(ROOT, text, passes = 4) { "√(${it[1]})" }
        text = replacing(FRACTION, text, passes = 4) { "${grouped(it[1])}/${grouped(it[2])}" }
        text = replacing(COMMAND, text) { SYMBOLS[it[1]] ?: it[1] }
        text = replacing(SPACING, text) { " " }
        text = replacing(SUPERSCRIPT, text) { script(it[1].ifEmpty { it[2] }, SUPERSCRIPTS, "^") }
        text = replacing(SUBSCRIPT, text) { script(it[1].ifEmpty { it[2] }, SUBSCRIPTS, "_") }
        return text.replace("{", "").replace("}", "").split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Replaces every match of the pattern with what the closure makes of its groups; group 0 is the whole match. */
    private fun replacing(pattern: Regex, text: String, passes: Int = 1, replacement: (List<String>) -> String): String {
        var result = text
        repeat(passes) {
            if (!pattern.containsMatchIn(result)) return result
            result = pattern.replace(result) { replacement(it.groupValues) }
        }
        return result
    }

    /** A numerator or denominator, bracketed unless it is a single term. */
    private fun grouped(term: String): String {
        val trimmed = term.trim()
        return if (trimmed.all { it.isLetterOrDigit() || it == '.' }) trimmed else "($trimmed)"
    }

    /** Raised or lowered characters where Unicode has them all, otherwise the marker and brackets. */
    private fun script(content: String, table: Map<Char, Char>, marker: String): String {
        val mapped = content.mapNotNull { table[it] }
        if (mapped.size == content.length) return mapped.joinToString("")
        return if (content.length == 1) marker + content else "$marker($content)"
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val TEXT_COMMAND = Regex("\\\\(?:text|mathrm|mathbf|mathit|operatorname|boxed)\\s*\\{([^{}]*)\\}")
        val ROOT = Regex("\\\\sqrt\\s*\\{([^{}]*)\\}")
        val FRACTION = Regex("\\\\d?frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}")
        val COMMAND = Regex("\\\\([A-Za-z]+)")
        val SPACING = Regex("\\\\[,;:! ]")
        val SUPERSCRIPT = Regex("\\^\\s*(?:\\{([^{}]*)\\}|(\\S))")
        val SUBSCRIPT = Regex("_\\s*(?:\\{([^{}]*)\\}|(\\S))")

        // A single dollar pair counts as maths only when it hugs its contents and they look like maths,
        // so that prices such as "$5 and $10" are left alone.
        val INLINE_PATTERNS = listOf(
            Regex("\\\\\\((.+?)\\\\\\)"),
            Regex("\\$\\$(.+?)\\$\\$"),
            Regex("(?<![\\\\$\\w])\\$(?!\\s)([^$\\n]*[\\\\^_={][^$\\n]*?)(?<!\\s)\\$(?!\\d)"),
        )

        val SUPERSCRIPTS = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ',
        )

        val SUBSCRIPTS = mapOf(
            '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
            '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
            'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 't' to 'ₜ', 'x' to 'ₓ',
        )

        val SYMBOLS = mapOf(
            "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓",
            "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡",
            "sim" to "∼", "propto" to "∝",
            "infty" to "∞", "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒", "Leftarrow" to "⇐",
            "leftrightarrow" to "↔",
            "sum" to "∑", "prod" to "∏", "int" to "∫", "partial" to "∂", "nabla" to "∇", "sqrt" to "√",
            "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆", "cup" to "∪", "cap" to "∩", "emptyset" to "∅",
            "forall" to "∀", "exists" to "∃", "neg" to "¬", "land" to "∧", "lor" to "∨", "angle" to "∠", "perp" to "⊥", "parallel" to "∥",
            "circ" to "∘", "degree" to "°", "ldots" to "…", "cdots" to "⋯", "dots" to "…", "prime" to "′",
            "left" to "", "right" to "", "quad" to " ", "qquad" to " ", "displaystyle" to "", "limits" to "",
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
            "theta" to "θ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "rho" to "ρ",
            "sigma" to "σ", "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
            "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ",
            "Psi" to "Ψ", "Omega" to "Ω",
        )
    }
}
