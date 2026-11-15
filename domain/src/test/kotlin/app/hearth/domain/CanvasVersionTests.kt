package app.hearth.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Which pieces of a chat count as versions of one another. */
class CanvasVersionMatcherTests {
    private fun piece(kind: CanvasKind, title: String?, index: Int = 0) =
        CanvasPiece(index = index, kind = kind, title = title, text = "", lineCount = 20, isClosed = true)

    /** A piece, another piece, whether the other stands alone in its reply, and whether it is then a version of the first. */
    private class Case(val kind: CanvasKind, val title: String?, val otherKind: CanvasKind, val otherTitle: String?, val isAlone: Boolean, val expected: Boolean)

    @Test
    fun `a piece is a version when it is the same kind and has the same title or stands alone`() {
        val python = CanvasKind.Code("python")
        val swift = CanvasKind.Code("swift")
        listOf(
            Case(CanvasKind.Html, "Stopwatch", CanvasKind.Html, "Stopwatch", isAlone = false, expected = true),
            Case(CanvasKind.Html, "Stopwatch", CanvasKind.Html, " stopwatch ", isAlone = false, expected = true),
            Case(CanvasKind.Html, "Stopwatch", CanvasKind.Html, "Timer", isAlone = false, expected = false),
            Case(CanvasKind.Html, "Stopwatch", CanvasKind.Html, "Timer", isAlone = true, expected = true),
            Case(CanvasKind.Html, null, CanvasKind.Html, null, isAlone = false, expected = false),
            Case(CanvasKind.Html, null, CanvasKind.Html, null, isAlone = true, expected = true),
            Case(CanvasKind.Html, "Logo", CanvasKind.Svg, "Logo", isAlone = true, expected = false),
            Case(CanvasKind.Markdown, "Plan", python, "Plan", isAlone = true, expected = false),
            Case(python, "Sorter", swift, "Sorter", isAlone = false, expected = true),
            Case(python, null, swift, null, isAlone = true, expected = true),
            Case(python, "Sorter", python, "Parser", isAlone = false, expected = false),
        ).forEachIndexed { number, case ->
            val isVersion = CanvasVersionMatcher().isVersion(piece(case.otherKind, case.otherTitle), case.isAlone, original = piece(case.kind, case.title))
            assertEquals(case.expected, isVersion, "case $number")
        }
    }

    @Test
    fun `the versions of a piece are gathered from the whole chat in order`() {
        val ids = (0 until 4).map { MessageId.new() }
        val replies = listOf(
            CanvasReply(ids[0], listOf(piece(CanvasKind.Html, "Stopwatch"))),
            CanvasReply(ids[1], emptyList()),
            CanvasReply(ids[2], listOf(piece(CanvasKind.Code("css"), "Styles"), piece(CanvasKind.Html, "Stopwatch", index = 1))),
            CanvasReply(ids[3], listOf(piece(CanvasKind.Html, "Stopwatch with laps"))),
        )
        val expected = listOf(CanvasReference(ids[0], 0), CanvasReference(ids[2], 1), CanvasReference(ids[3], 0))

        listOf(expected[0], expected[1]).forEach { anchor -> assertEquals(expected, CanvasVersionMatcher().versions(anchor, replies)) }
        assertEquals(1, CanvasVersionMatcher().versions(CanvasReference(ids[2], 0), replies).size)
    }

    @Test
    fun `a piece that is no longer in the chat has no versions`() {
        val replies = listOf(CanvasReply(MessageId.new(), listOf(piece(CanvasKind.Html, "Stopwatch"))))

        assertTrue(CanvasVersionMatcher().versions(CanvasReference(MessageId.new(), 0), replies).isEmpty())
        assertTrue(CanvasVersionMatcher().versions(CanvasReference(replies[0].messageId, 3), replies).isEmpty())
    }

    @Test
    fun `only code has no preview`() {
        assertTrue(CanvasKind.Html.hasPreview && CanvasKind.Svg.hasPreview && CanvasKind.Markdown.hasPreview)
        assertFalse(CanvasKind.Code("python").hasPreview)
    }
}

/** What a page shown in the canvas may load and where it may go. */
class CanvasWebPolicyTests {
    private val policy = CanvasWebPolicy()

    @Test
    fun `the page itself and places within it may be gone to`() {
        listOf("about:blank", "about:blank#section", "about:srcdoc", "ABOUT:blank").forEach { address ->
            assertEquals(CanvasNavigationDecisionKind.ALLOW, policy.navigationDecision(address), address)
        }
    }

    @Test
    fun `every way out of the page is cancelled`() {
        listOf(
            "https://example.com/",
            "http://example.com/?secret=1",
            "HTTPS://EXAMPLE.COM",
            "file:///etc/hosts",
            "content://media/external/images/1",
            "data:text/html,<p>hi</p>",
            "blob:null/1234",
            "javascript:alert(1)",
            "intent://scan/#Intent;scheme=zxing;end",
            "market://details?id=app",
            "hearth://voice",
            "tel:123",
            "sms:123",
            "mailto:someone@example.com",
            "ws://example.com/socket",
            " about:blank",
            "about",
            "//example.com/path",
            "example.com",
            "",
            null,
        ).forEach { address -> assertEquals(CanvasNavigationDecisionKind.CANCEL, policy.navigationDecision(address), address) }
    }

    @Test
    fun `everything a page asks for from the network or from the phone is answered with nothing`() {
        listOf(
            "http://example.com/a.png",
            "https://example.com/api?q=1",
            "HTTPS://EXAMPLE.COM/",
            "ws://example.com/socket",
            "wss://example.com/socket",
            "ftp://example.com/file",
            "file:///data/data/app/files/secret.json",
            "FILE:///sdcard/a.png",
            "content://app.files/secret",
            "blob:null/1234",
            "filesystem:https://example.com/temporary/a",
            "intent://example",
            "javascript:void(0)",
            "//example.com/a.js",
            "/a.js",
            "a.js",
            " data:text/plain,hi",
            "",
            null,
        ).forEach { address -> assertEquals(CanvasRequestDecisionKind.BLOCK, policy.requestDecision(address), address) }
    }

    @Test
    fun `what carries its own content may be loaded`() {
        listOf("data:image/png;base64,AAAA", "DATA:text/css,body{}", "about:blank", "about:srcdoc").forEach { address ->
            assertEquals(CanvasRequestDecisionKind.ALLOW, policy.requestDecision(address), address)
        }
    }

    @Test
    fun `the rules let the page run itself and connect to nothing`() {
        val rules = policy.contentSecurityPolicy.split("; ").associate { it.substringBefore(' ') to it.substringAfter(' ').split(' ') }

        assertEquals(listOf("'unsafe-inline'", "'unsafe-eval'", "data:", "blob:"), rules["default-src"])
        assertEquals(listOf("data:", "blob:"), rules["connect-src"])
        assertEquals(listOf("'none'"), rules["object-src"])
        assertEquals(listOf("'none'"), rules["form-action"])
        assertEquals(listOf("'none'"), rules["base-uri"])
        assertEquals(listOf("'block'"), rules["webrtc"])
        // No rule names a site, a scheme that reaches the network, or everything.
        listOf("http", "ws", "ftp", "*", "'self'").forEach { way -> assertFalse(rules.values.flatten().any { it.startsWith(way) }, way) }
        assertFalse('"' in policy.contentSecurityPolicy)
    }

    @Test
    fun `no window, permission, place, file or download is granted`() {
        assertFalse(CanvasWebPolicy.ALLOWS_NEW_WINDOWS)
        assertFalse(CanvasWebPolicy.ALLOWS_PERMISSIONS)
        assertFalse(CanvasWebPolicy.ALLOWS_GEOLOCATION)
        assertFalse(CanvasWebPolicy.ALLOWS_FILE_CHOOSING)
        assertFalse(CanvasWebPolicy.ALLOWS_DOWNLOADS)
        assertEquals("about:blank", CanvasWebPolicy.BASE_ADDRESS)
    }

    @Test
    fun `a page is loaded as written behind the rules and the script, and a drawing inside a page of its own`() {
        val guard = "<meta http-equiv=\"Content-Security-Policy\" content=\"${policy.contentSecurityPolicy}\">" +
            "<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\"><script>${policy.startScript}</script>"
        val drawing = assertNotNull(policy.document(CanvasKind.Svg, "<svg id=\"mark\"></svg>"))

        assertEquals("$guard<p>Hi</p>", policy.document(CanvasKind.Html, "<p>Hi</p>"))
        assertTrue(drawing.startsWith("<!doctype html>"))
        assertTrue(drawing.indexOf(guard) in 0 until drawing.indexOf("<svg id=\"mark\"></svg>"))
        assertNull(policy.document(CanvasKind.Markdown, "# Hi"))
        assertNull(policy.document(CanvasKind.Code("kotlin"), "val x = 1"))
    }

    @Test
    fun `a page that says what it is still says so first, with the guard straight after`() {
        val guard = "<meta http-equiv=\"Content-Security-Policy\""
        listOf(
            "<!DOCTYPE html>" to "<html><head><script>go()</script></head></html>",
            "\n  <!doctype html>" to "\n<html></html>",
            "<!-- made by hand -->\n<!doctype html>" to "<title>T</title>",
            "﻿<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">" to "<html></html>",
        ).forEach { (declaration, rest) ->
            val document = assertNotNull(policy.document(CanvasKind.Html, declaration + rest))
            assertTrue(document.startsWith(declaration + guard), declaration)
            assertTrue(document.endsWith("</script>$rest"), declaration)
        }
    }

    @Test
    fun `nothing of a page comes before the guard`() {
        listOf("<script>early()</script><!doctype html>", "<html><!doctype html>", "text <!doctype html>", "<p>Hi</p>").forEach { page ->
            assertTrue(assertNotNull(policy.document(CanvasKind.Html, page)).startsWith("<meta http-equiv=\"Content-Security-Policy\""), page)
        }
    }

    @Test
    fun `the script takes peer connections and beacons away and cannot end its own tag`() {
        listOf("RTCPeerConnection", "webkitRTCPeerConnection", "RTCDataChannel", "sendBeacon").forEach { name -> assertTrue("'$name'" in policy.startScript, name) }
        assertFalse("</" in policy.startScript)
    }

    @Test
    fun `the script seals every frame the page makes or reaches, and is run at the start of every frame's document`() {
        listOf(
            // Reaching a frame's window.
            "contentWindow", "contentDocument",
            // Putting a frame in the page, from script or from markup.
            "appendChild", "insertBefore", "replaceChild", "append", "after", "replaceWith", "insertAdjacentHTML",
            "innerHTML", "outerHTML", "write", "insertNode", "attachShadow", "open",
        ).forEach { name -> assertTrue("'$name'" in policy.startScript, name) }
        // It walks the page's frames by an index the page cannot hide, and watches for the parser's.
        assertTrue("w[i]" in policy.startScript)
        assertTrue("MutationObserver" in policy.startScript)
        // What it relies on is taken before the page runs, so the page cannot swap it out afterwards.
        assertTrue("var call = Reflect.apply, describe = Object.getOwnPropertyDescriptor, define = Object.defineProperty;" in policy.startScript)
        assertEquals(setOf("*"), CanvasWebPolicy.START_SCRIPT_ORIGIN_RULES)
    }
}
