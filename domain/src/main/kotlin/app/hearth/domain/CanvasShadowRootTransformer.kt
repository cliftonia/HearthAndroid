package app.hearth.domain

/**
 * Takes declarative shadow roots out of a page before the canvas loads it.
 *
 * The parser turns a `<template shadowrootmode="open">` (or `"closed"`) into a shadow root of the element around it,
 * without any script being run, so the start script never sees it made and its sweep of the page's frames does not
 * look inside it. A frame hidden there, sandboxed or not, would be beyond its reach. So every attribute that asks for
 * such a root is renamed before the page is loaded: the template is then an ordinary template, whose contents stay
 * inert and out of the page, and nothing else about the page changes.
 *
 * The names are matched wherever an attribute's name can begin and end, with no attempt to read the markup as the
 * parser would (an attribute's value may hold a `>`, a stray quote ends no attribute, and so on): renaming one too
 * many does no harm, and missing one would. So the words also change in the page's own text, where one stands alone
 * in a sentence about shadow roots.
 */
object CanvasShadowRootTransformer {
    /** The attributes that make, or describe, a declarative shadow root, as the parser reads them in any case. */
    val ATTRIBUTE_NAMES = listOf("shadowrootmode", "shadowroot", "shadowrootdelegatesfocus", "shadowrootclonable", "shadowrootserializable")

    /** What each is renamed to begin with: a data attribute, which nothing reads. */
    const val INERT_PREFIX = "data-hearth-"

    /**
     * A name, with what may stand before it (space, `/` or a quote that ends the attribute before) and after it
     * (space, `=`, `/`, `>` or the end), in any case.
     */
    private val ATTRIBUTE = Regex(
        """([\t\n\f\r "'/])(shadowroot(?:mode|delegatesfocus|clonable|serializable)?)(?=[\t\n\f\r =/>]|\z)""",
        RegexOption.IGNORE_CASE,
    )

    /** The page with every attribute that would make a declarative shadow root renamed with [INERT_PREFIX]. */
    fun inert(markup: String): String = ATTRIBUTE.replace(markup) { it.groupValues[1] + INERT_PREFIX + it.groupValues[2] }
}
