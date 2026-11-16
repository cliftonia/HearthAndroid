package app.hearth.domain

/** What the canvas decides about something its page asks to load. */
enum class CanvasRequestDecisionKind { ALLOW, BLOCK }

/** What the canvas decides about a navigation its page asks for. */
enum class CanvasNavigationDecisionKind { ALLOW, CANCEL }

/**
 * What a page shown in the canvas may do. The page was written by a model, so it is untrusted: it may run its
 * own scripts, and nothing else.
 *
 * - It is loaded from a string with [BASE_ADDRESS] as its address, so it has no origin and no files to reach.
 * - Nothing is fetched over the network: [requestDecision] blocks every request but one that carries its own
 *   content, and the rules in [contentSecurityPolicy], which the page is given before anything of its own, refuse
 *   every connection a script can open, a socket included.
 * - It goes nowhere: [navigationDecision] cancels every navigation but the page's own. A tapped link therefore
 *   opens nothing, here or in the browser.
 * - It is given no window, camera, microphone, place or file: the constants below.
 * - It is given no peer connection, in its own window or any frame's, and no frame it cannot reach: [startScript],
 *   and no shadow root made from its markup, where a frame could be hidden from that script: [document].
 *
 * The web view that enforces this is `CanvasWebPreview` in the app; the decisions are here so that they can be
 * tested.
 */
class CanvasWebPolicy {
    /**
     * Whether something the page asks to load may be loaded. Only what carries its own content (`data:`) or is
     * the empty page itself (`about:`) may; everything else is a way to the network, or to a file, and is
     * answered with nothing.
     */
    fun requestDecision(address: String?): CanvasRequestDecisionKind =
        if (scheme(address) in LOCAL_SCHEMES) CanvasRequestDecisionKind.ALLOW else CanvasRequestDecisionKind.BLOCK

    /**
     * Whether a navigation may go ahead. A page loaded from a string lives at `about:blank`, and so do the
     * frames it writes itself and the places within it that its own links jump to. Those are allowed; anything
     * with any other address is a way out of the page and is cancelled, whether a tap or a script asked for it.
     */
    fun navigationDecision(address: String?): CanvasNavigationDecisionKind =
        if (scheme(address) == "about") CanvasNavigationDecisionKind.ALLOW else CanvasNavigationDecisionKind.CANCEL

    /**
     * The rules the page is held to by the web view's own engine, whatever its scripts do, and the frames it
     * makes with it: scripts, styles, pictures and sounds only from the page itself, no connection of any kind
     * (`fetch`, a request, a beacon, a socket), no form sent anywhere, no other base address, no plug-in, and
     * no peer connection.
     */
    val contentSecurityPolicy: String
        get() = listOf(
            "default-src 'unsafe-inline' 'unsafe-eval' data: blob:",
            "connect-src data: blob:",
            "object-src 'none'",
            "form-action 'none'",
            "base-uri 'none'",
            "webrtc 'block'",
        ).joinToString("; ")

    /**
     * Run before the page's own scripts: takes away the one way to reach the network that is not a request, a
     * peer connection, and has a beacon say truthfully that it was not sent, where the engine would take it, say
     * that it had, and then drop it.
     *
     * Taking them out of the page's own window is not enough. A frame the page makes starts with an empty
     * document of its own, with peer connections of its own, which no script run at the start of a document
     * reaches (measured on the emulator), so the page could take one from there. So the script does the same
     * to every frame the moment it is made or reached, before the page can use it: everything a script makes
     * a frame or reaches its window with (putting elements or markup into the page, `document.write`, a
     * frame's `contentWindow`, and the like) seals every frame in the page as it does so, frames within frames
     * included, and puts the same script into each. A frame that the parser puts in the page is sealed before
     * the next script runs, by a watch on the document's changes, and one whose loading runs the page's code at
     * once, by listening ahead of the page.
     *
     * It fails closed. A frame it cannot reach cannot be sealed, and the only frame in a page that cannot be
     * reached is one with a `sandbox` that keeps it apart, so a sandbox is never given: it is taken off a frame
     * before the frame is put in the page, and setting one is ignored. A frame it still cannot reach is taken out
     * of the page. Markup that would make a shadow root (`setHTMLUnsafe`, `Document.parseHTMLUnsafe` and a
     * `DOMParser` asked to include shadow roots, where the engine has them) is read as plain markup, and
     * `document.write` and `writeln` do nothing, since they make one from markup as the parser does and a name split
     * across writes cannot be renamed reliably, so a frame cannot be hidden in one; the page's own markup has had the same done in [document] ([CanvasShadowRootTransformer]). An
     * open shadow root the sweep comes across that no script asked for is swept and watched like one a script made,
     * and a frame in any shadow root is sealed when the sweep finds it or its window is asked for, since a frame
     * there is not among the page's frames by index. What it uses it takes before the page can change it.
     *
     * The web view also runs it at the start of every frame's document ([START_SCRIPT_ORIGIN_RULES]), where it
     * can.
     */
    val startScript: String
        get() = """
            (function () {
                'use strict';
                // Taken before the page can change them, so that nothing the page does later reaches into what follows.
                var call = Reflect.apply, describe = Object.getOwnPropertyDescriptor, define = Object.defineProperty;
                var toText = String, lower = String.prototype.toLowerCase, lastIndexOf = String.prototype.lastIndexOf;
                var cut = String.prototype.slice, copy = Array.prototype.slice;
                function getterOf(proto, name) { var held = describe(proto, name); return held && held.get; }
                function setterOf(proto, name) { var held = describe(proto, name); return held && held.set; }
                var lengthOf = getterOf(window, 'length') || getterOf(Window.prototype, 'length');
                var countOf = getterOf(NodeList.prototype, 'length'), typeOf = getterOf(Node.prototype, 'nodeType');
                var tagOf = getterOf(Element.prototype, 'localName'), attributeNameOf = getterOf(Attr.prototype, 'localName');
                var markupInto = setterOf(Element.prototype, 'innerHTML'), markupIntoShadow = setterOf(ShadowRoot.prototype, 'innerHTML');
                var hasAttribute = Element.prototype.hasAttribute, removeAttribute = Element.prototype.removeAttribute;
                var removeNode = Element.prototype.remove, makeElement = Document.prototype.createElement;
                var Parser = DOMParser, parse = DOMParser.prototype.parseFromString;
                var iframeWindow = getterOf(HTMLIFrameElement.prototype, 'contentWindow');
                var frameWindow = getterOf(HTMLFrameElement.prototype, 'contentWindow');
                var objectWindow = getterOf(HTMLObjectElement.prototype, 'contentWindow');
                var shadowOf = getterOf(Element.prototype, 'shadowRoot');
                var findAll = {
                    1: Element.prototype.querySelectorAll,
                    9: Document.prototype.querySelectorAll,
                    11: DocumentFragment.prototype.querySelectorAll
                };
                var root = window.top, roots = [];
                var FRAMES = 'iframe, frame, object, [sandbox]';
                var NAMES = ['RTCPeerConnection', 'webkitRTCPeerConnection', 'RTCDataChannel', 'RTCIceCandidate', 'RTCSessionDescription'];
                var EVENTS = ['load', 'error', 'readystatechange', 'DOMContentLoaded', 'focus', 'focusin', 'toggle', 'slotchange'];
                // Each way a script reaches a frame's window, or puts a frame in the page.
                var GETTERS = [
                    ['HTMLIFrameElement', ['contentWindow', 'contentDocument']],
                    ['HTMLFrameElement', ['contentWindow', 'contentDocument']],
                    ['HTMLObjectElement', ['contentWindow', 'contentDocument']]
                ];
                var METHODS = [
                    ['Node', ['appendChild', 'insertBefore', 'replaceChild']],
                    ['Element', ['append', 'prepend', 'before', 'after', 'replaceWith', 'replaceChildren']],
                    ['Element', ['insertAdjacentElement', 'insertAdjacentHTML']],
                    ['CharacterData', ['before', 'after', 'replaceWith']],
                    ['DocumentType', ['before', 'after', 'replaceWith']],
                    ['DocumentFragment', ['append', 'prepend', 'replaceChildren']],
                    ['Document', ['append', 'prepend', 'replaceChildren', 'write', 'writeln', 'execCommand']],
                    ['Range', ['insertNode', 'surroundContents']],
                    ['HTMLIFrameElement', ['getSVGDocument']],
                    ['HTMLObjectElement', ['getSVGDocument']],
                    ['HTMLEmbedElement', ['getSVGDocument']]
                ];
                var SETTERS = [['Element', ['innerHTML', 'outerHTML']], ['ShadowRoot', ['innerHTML']], ['Document', ['body']]];
                // Each way to give an element a sandbox: by the attribute's name, at the given place, or by an attribute.
                var SANDBOX_NAMED = [['Element', ['setAttribute', 'toggleAttribute'], 0], ['Element', ['setAttributeNS'], 1]];
                var SANDBOX_NODES = [['Element', ['setAttributeNode', 'setAttributeNodeNS']], ['NamedNodeMap', ['setNamedItem', 'setNamedItemNS']]];

                function sealed(w) {
                    var held = describe(w, 'RTCPeerConnection');
                    return !!held && held.value === undefined && !held.configurable;
                }
                // Takes the sandbox off a frame and seals its window, wherever the frame is, and takes out of the page a frame that
                // still cannot be reached. A frame in a shadow root is not among the page's frames by index, so this is what seals it.
                function tidy(node) {
                    try {
                        if (call(hasAttribute, node, ['sandbox'])) call(removeAttribute, node, ['sandbox']);
                        var tag = call(tagOf, node, []);
                        var reach = tag === 'iframe' ? iframeWindow : tag === 'frame' ? frameWindow : tag === 'object' ? objectWindow : null;
                        var w = reach && call(reach, node, []);
                        if (w && !sealed(w)) sweep(w);
                    } catch (error) {
                        try { call(removeNode, node, []); } catch (ignored) {}
                    }
                }
                // Tidies a node and every frame or sandboxed element within it, and keeps every open shadow root within it, so that
                // one the parser made from markup, which no script asked for, is swept and watched like one a script made.
                function tidyWithin(node) {
                    try {
                        var type = call(typeOf, node, []);
                        if (type === 1) tidy(node);
                        if (type !== 1 && type !== 9 && type !== 11) return;
                        var found = call(findAll[type], node, [FRAMES]);
                        var count = call(countOf, found, []);
                        for (var k = 0; k < count; k++) tidy(found[k]);
                        if (type === 1) keepShadowOf(node);
                        var all = call(findAll[type], node, ['*']);
                        var total = call(countOf, all, []);
                        for (var e = 0; e < total; e++) keepShadowOf(all[e]);
                    } catch (error) {}
                }
                function keepShadowOf(element) {
                    var shadow = call(shadowOf, element, []);
                    if (shadow) keep(shadow, watchHere);
                }
                // Adds a shadow root to those swept, once, and watches it.
                function keep(shadow, watch) {
                    for (var r = 0; r < roots.length; r++) {
                        if (roots[r] === shadow) return;
                    }
                    define(roots, roots.length, { value: shadow });
                    watch(shadow);
                }
                // Seals every window in the page that is not sealed yet, frames within frames included.
                function sweep(w) {
                    var doc;
                    try {
                        if (!sealed(w)) seal(w);
                        doc = w.document;
                    } catch (error) { return; }
                    tidyWithin(doc);
                    try {
                        var count = call(lengthOf, w, []);
                        for (var i = 0; i < count; i++) sweep(w[i]);
                    } catch (error) {}
                }
                function sweepAll() {
                    sweep(root);
                    if (window !== root) sweep(window);
                    for (var r = 0; r < roots.length; r++) tidyWithin(roots[r]);
                }
                function wrap(w, kind, name, part, make) {
                    var proto = w[kind] && w[kind].prototype;
                    var held = proto && describe(proto, name);
                    if (!held || typeof held[part] !== 'function') return;
                    held[part] = make(held[part]);
                    define(proto, name, held);
                }
                function sweptAfter(original) {
                    return function () { try { return call(original, this, arguments); } finally { sweepAll(); } };
                }
                // Seals the frame whose window is asked for, wherever it is, and every other, before handing it out.
                function sweptBefore(original) {
                    return function () { tidyWithin(this); sweepAll(); return call(original, this, arguments); };
                }
                // Takes the sandbox off whatever is about to be put in the page, then puts it in and seals what it made.
                function tidiedAndSwept(original) {
                    return function () {
                        for (var a = 0; a < arguments.length; a++) tidyWithin(arguments[a]);
                        try { return call(original, this, arguments); } finally { sweepAll(); }
                    };
                }
                function each(list, part, make, w) {
                    for (var k = 0; k < list.length; k++) {
                        for (var n = 0; n < list[k][1].length; n++) wrap(w, list[k][0], list[k][1][n], part, make);
                    }
                }
                function isSandbox(name) {
                    var text = call(lower, name, []);
                    return call(cut, text, [call(lastIndexOf, text, [':']) + 1]) === 'sandbox';
                }
                // Ignores a call that would set a sandbox by name; the name is read once, so it cannot change between.
                function refusingName(at) {
                    return function (original) {
                        return function () {
                            var args = call(copy, arguments, []);
                            if (args.length > at) {
                                args[at] = toText(args[at]);
                                if (isSandbox(args[at])) return undefined;
                            }
                            return call(original, this, args);
                        };
                    };
                }
                // Both engines make a declarative shadow root from markup given to `document.write`, as from the page's own,
                // and a name split across writes slips past any renaming, so writing into a document does nothing here.
                function refuseWrite() {
                    return function () { return undefined; };
                }
                // Watches a node for the frames put into it, with what one window has.
                function watcher(w) {
                    var listen = w.EventTarget.prototype.addEventListener, Observer = w.MutationObserver;
                    var observe = Observer.prototype.observe;
                    var options = { childList: true, subtree: true, attributes: true, attributeFilter: ['sandbox'] };
                    return function (node) {
                        for (var e = 0; e < EVENTS.length; e++) call(listen, node, [EVENTS[e], sweepAll, true]);
                        call(observe, new Observer(sweepAll), [node, options]);
                    };
                }
                function refusingNode(original) {
                    return function (attribute) {
                        try { if (isSandbox(call(attributeNameOf, attribute, []))) return null; } catch (error) {}
                        return call(original, this, arguments);
                    };
                }
                // Takes peer connections out of one window, has its beacons report that they were not sent, gives it no
                // way to sandbox a frame, and has everything in it that makes a frame or reaches one seal the new frame
                // before the page can use it.
                function seal(w) {
                    for (var i = 0; i < NAMES.length; i++) {
                        try { define(w, NAMES[i], { value: undefined, writable: false, configurable: false }); } catch (error) {}
                    }
                    try {
                        var refuse = { value: function () { return false; }, writable: false, configurable: false };
                        define(w.Navigator.prototype, 'sendBeacon', refuse);
                    } catch (error) {}
                    var watch = watcher(w);
                    each(GETTERS, 'get', sweptBefore, w);
                    each(METHODS, 'value', tidiedAndSwept, w);
                    wrap(w, 'Document', 'write', 'value', refuseWrite);
                    wrap(w, 'Document', 'writeln', 'value', refuseWrite);
                    each(SETTERS, 'set', sweptAfter, w);
                    for (var s = 0; s < SANDBOX_NAMED.length; s++) each([SANDBOX_NAMED[s]], 'value', refusingName(SANDBOX_NAMED[s][2]), w);
                    each(SANDBOX_NODES, 'value', refusingNode, w);
                    var spare = null;
                    wrap(w, 'HTMLIFrameElement', 'sandbox', 'set', function () { return function () {}; });
                    // A frame's own list of sandbox flags is never handed out: changes to the one given go nowhere.
                    wrap(w, 'HTMLIFrameElement', 'sandbox', 'get', function (original) {
                        return function () {
                            spare = spare || call(makeElement, w.document, ['iframe']);
                            return call(original, spare, []);
                        };
                    });
                    wrap(w, 'Element', 'setHTMLUnsafe', 'value', function () {
                        return function (markup) { try { call(markupInto, this, [markup]); } finally { sweepAll(); } };
                    });
                    wrap(w, 'ShadowRoot', 'setHTMLUnsafe', 'value', function () {
                        return function (markup) { try { call(markupIntoShadow, this, [markup]); } finally { sweepAll(); } };
                    });
                    try {
                        if (typeof w.Document.parseHTMLUnsafe === 'function') {
                            define(w.Document, 'parseHTMLUnsafe', {
                                value: function (markup) { return call(parse, new Parser(), [toText(markup), 'text/html']); },
                                writable: false,
                                configurable: false
                            });
                        }
                    } catch (error) {}
                    // The engine's older way to the same: a parser asked to include shadow roots is not given the request.
                    wrap(w, 'DOMParser', 'parseFromString', 'value', function (original) {
                        return function (markup, type) { return call(original, this, [markup, type]); };
                    });
                    wrap(w, 'Element', 'attachShadow', 'value', function (original) {
                        return function () {
                            var shadow = call(original, this, arguments);
                            try { keep(shadow, watch); } catch (error) {}
                            return shadow;
                        };
                    });
                    wrap(w, 'Document', 'open', 'value', function (original) {
                        return function () {
                            try { return call(original, this, arguments); } finally {
                                try { watch(this); } catch (error) {}
                                sweepAll();
                            }
                        };
                    });
                    try { watch(w.document); } catch (error) {}
                }
                var watchHere = watcher(window);
                sweep(window);
                sweepAll();
            })();
        """.trimIndent()

    /**
     * The document the web view is given for a piece: a page as it was written, a drawing centred in a page of
     * its own, each with the rules and the script above ahead of anything of its own and its declarative shadow
     * roots made inert ([CanvasShadowRootTransformer]). `null` for a kind that is not shown in a web view.
     */
    fun document(kind: CanvasKind, text: String): String? = when (kind) {
        CanvasKind.Html -> guarded(CanvasShadowRootTransformer.inert(text))
        CanvasKind.Svg -> listOf(
            "<!doctype html>",
            "<html>",
            "<head>",
            guard,
            "<meta charset=\"utf-8\">",
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
            "<style>",
            "html, body { height: 100%; margin: 0; background: #ffffff; }",
            "body { display: flex; align-items: center; justify-content: center; }",
            "svg { max-width: 100%; max-height: 100%; }",
            "</style>",
            "</head>",
            "<body>",
            CanvasShadowRootTransformer.inert(text),
            "</body>",
            "</html>",
        ).joinToString("\n")
        CanvasKind.Markdown, is CanvasKind.Code -> null
    }

    /** The rules and the script, as the first things in a page's head. */
    private val guard: String
        get() = "<meta http-equiv=\"Content-Security-Policy\" content=\"$contentSecurityPolicy\">" +
            "<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\"><script>$startScript</script>"

    /**
     * The page with the guard put first. A page that opens by declaring what it is keeps that declaration
     * first, since anything before it would change how the page is laid out.
     */
    private fun guarded(page: String): String {
        val end = DOCTYPE.find(page.take(DOCTYPE_SEARCH_LENGTH))?.range?.last?.plus(1) ?: 0
        return page.substring(0, end) + guard + page.substring(end)
    }

    /** The scheme of an address, in small letters; `null` when it has none that can be read. */
    private fun scheme(address: String?): String? = address?.let(SCHEME::find)?.groupValues?.get(1)?.lowercase()

    companion object {
        /** The address a page is loaded at: no site, no file, nothing that gives it an origin. */
        const val BASE_ADDRESS = "about:blank"

        /** The documents the start script is also run at the start of, where the web view can: every one, whatever its origin. */
        val START_SCRIPT_ORIGIN_RULES = setOf("*")

        /** A page that asks for a new window, as `window.open` and a link with a target do, gets none. */
        const val ALLOWS_NEW_WINDOWS = false

        /** A page that asks for the camera, the microphone or anything else the phone guards is refused without the user being asked. */
        const val ALLOWS_PERMISSIONS = false

        /** A page that asks where the phone is is refused without the user being asked. */
        const val ALLOWS_GEOLOCATION = false

        /** A page that asks for a file of the user's is given none, and no picker is shown. */
        const val ALLOWS_FILE_CHOOSING = false

        /** A page that offers a download has it ignored. */
        const val ALLOWS_DOWNLOADS = false

        /** How much of a page is searched for the declaration it opens with. */
        private const val DOCTYPE_SEARCH_LENGTH = 4_000
        private val LOCAL_SCHEMES = setOf("data", "about")
        private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
        private val DOCTYPE = Regex("^(?:\\s|\\uFEFF|<!--.*?-->)*<!doctype[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }
}
