package app.hearth.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/** A page's declarative shadow roots, made inert before the canvas loads it. */
class CanvasShadowRootTests {
    private fun inert(markup: String) = CanvasShadowRootTransformer.inert(markup)

    @Test
    fun `every attribute that makes a shadow root is renamed, in any case and any form`() {
        listOf(
            """<div><template shadowrootmode="open"><iframe></iframe></template></div>""" to
                """<div><template data-hearth-shadowrootmode="open"><iframe></iframe></template></div>""",
            "<template SHADOWROOTMODE=closed>" to "<template data-hearth-SHADOWROOTMODE=closed>",
            "<template ShadowRootMode = 'open'>" to "<template data-hearth-ShadowRootMode = 'open'>",
            "<template shadowroot=open>" to "<template data-hearth-shadowroot=open>",
            "<template\nshadowrootmode=open\tshadowrootdelegatesfocus shadowrootclonable/shadowrootserializable>" to
                "<template\ndata-hearth-shadowrootmode=open\tdata-hearth-shadowrootdelegatesfocus " +
                "data-hearth-shadowrootclonable/data-hearth-shadowrootserializable>",
            "<template a=\"x\"shadowrootmode=open>" to "<template a=\"x\"data-hearth-shadowrootmode=open>",
            "<template title=\">\" shadowrootmode=open>" to "<template title=\">\" data-hearth-shadowrootmode=open>",
            "<template shadowrootmode" to "<template data-hearth-shadowrootmode",
        ).forEach { (markup, expected) -> assertEquals(expected, inert(markup), markup) }
    }

    @Test
    fun `a template within a template is reached too`() {
        val markup = """<div><template shadowrootmode="open"><p><template shadowrootmode="closed"><iframe></iframe></template></p></template></div>"""

        val page = inert(markup)

        assertEquals(2, Regex("data-hearth-shadowrootmode").findAll(page).count())
        assertFalse(Regex("""[\s"'/]shadowrootmode""", RegexOption.IGNORE_CASE).containsMatchIn(page))
    }

    @Test
    fun `a page without those attributes is left as it was`() {
        listOf(
            "<!doctype html><html><body><p class=\"shadow\">Shadows and roots</p><template><b>kept</b></template></body></html>",
            "<div data-shadowrootmode=\"open\" myshadowroot=\"x\" shadowrootmodes=\"y\"></div>",
            "<p>The shadowrootmodes; and shadowroot-ish things.</p>",
            "<svg><circle r=\"4\"/></svg>",
            "",
        ).forEach { assertEquals(it, inert(it)) }
    }

    @Test
    fun `renaming twice changes nothing more`() {
        val once = inert("<template shadowrootmode=open shadowroot>")

        assertEquals(once, inert(once))
    }

    @Test
    fun `a page and a drawing reach the web view with their shadow roots made inert`() {
        val policy = CanvasWebPolicy()
        val page = assertNotNull(policy.document(CanvasKind.Html, "<div><template shadowrootmode=\"closed\"><iframe></iframe></template></div>"))
        val drawing = assertNotNull(policy.document(CanvasKind.Svg, "<svg><foreignObject><div><template shadowrootmode=open></template></div></foreignObject></svg>"))

        assertTrue(page.contains("<template data-hearth-shadowrootmode=\"closed\">"))
        assertTrue(drawing.contains("<template data-hearth-shadowrootmode=open>"))
        assertEquals(CanvasShadowRootTransformer.ATTRIBUTE_NAMES.size, CanvasShadowRootTransformer.ATTRIBUTE_NAMES.toSet().size)
    }

    @Test
    fun `the script refuses document writes, and sweeps the shadow roots the parser made`() {
        val script = CanvasWebPolicy().startScript

        assertTrue("wrap(w, 'Document', 'write', 'value', refuseWrite)" in script)
        assertTrue("wrap(w, 'Document', 'writeln', 'value', refuseWrite)" in script)
        // Every open shadow root it comes across is kept and watched, and a frame it finds anywhere is sealed.
        assertTrue("getterOf(Element.prototype, 'shadowRoot')" in script)
        assertTrue("keep(shadow, watchHere)" in script)
        assertTrue("if (w && !sealed(w)) sweep(w);" in script)
    }
}
