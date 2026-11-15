package app.hearth.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.hearth.domain.CanvasNavigationDecisionKind
import app.hearth.domain.CanvasRequestDecisionKind
import app.hearth.domain.CanvasWebPolicy
import java.io.ByteArrayInputStream

/**
 * A page or a drawing written by a model, shown in a web view that can reach nothing outside itself.
 *
 * What it may do is decided by [CanvasWebPolicy]; [CanvasWebGuard] carries the decisions out. The web view is made
 * when this first shows and destroyed when it goes, as when the canvas closes.
 *
 * @param document The document to show, as the policy made it.
 * @param isShown Whether the page is to be seen. While it is not, as while the code is looked at, it is kept as
 * it was, so that a page is not started again by a glance at its code.
 * @param onUnavailable Called when the page cannot be shown, because the phone has no web view to show it in or
 * the one it had has failed.
 */
@Composable
internal fun CanvasWebPreview(document: String, isShown: Boolean, onUnavailable: () -> Unit, modifier: Modifier = Modifier) {
    val unavailable = rememberUpdatedState(onUnavailable)
    val guard = remember { CanvasWebGuard { unavailable.value() } }
    AndroidView(
        factory = guard::make,
        modifier = modifier,
        update = { guard.show(document, isShown) },
        onRelease = { guard.destroy() },
    )
}

/** Holds the web view of a [CanvasWebPreview] and answers everything it asks with the policy's decisions. */
internal class CanvasWebGuard(private val onUnavailable: () -> Unit) {
    private val policy = CanvasWebPolicy()
    private var webView: WebView? = null

    /** The document most lately loaded. */
    private var shownDocument: String? = null

    /** The web view, shut off from everything; or an empty view if the phone has none to give. */
    fun make(context: Context): View = try {
        lockedDown(WebView(context)).also { webView = it }
    } catch (failure: RuntimeException) {
        // A phone whose web view is missing, switched off or being updated cannot make one.
        Log.e(TAG, "Making the canvas's web view failed: ${failure.javaClass.simpleName}")
        onUnavailable()
        View(context)
    }

    /** Loads the document unless it is the one already showing, and shows or hides the page. */
    fun show(document: String, isShown: Boolean) {
        val view = webView ?: return
        // A view that is not visible takes no touches and is not read out.
        view.visibility = if (isShown) View.VISIBLE else View.INVISIBLE
        if (document == shownDocument) return
        shownDocument = document
        // From a string, at an address that is no site and no file: the page has no origin and nothing to reach.
        view.loadDataWithBaseURL(CanvasWebPolicy.BASE_ADDRESS, document, "text/html", "utf-8", null)
    }

    /** Stops the page and lets go of everything the web view held. */
    fun destroy() {
        val view = webView ?: return
        webView = null
        shownDocument = null
        view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view)
        view.webChromeClient = null
        view.clearHistory()
        view.clearFormData()
        view.clearCache(true)
        view.removeAllViews()
        view.destroy()
    }

    /**
     * Sets a new web view up so that a page in it can run its own scripts and do nothing else.
     *
     * Scripts are turned on because a page that a model writes is made of them: a stopwatch that cannot count is
     * no preview. That is safe here only because of everything else this does: the page comes from a string and
     * has no origin, every load from the network is refused three times over (by the setting, by the answer to
     * each request and by the rules the page itself is held to), every navigation is cancelled, nothing of the
     * app is exposed to the page's scripts (`addJavascriptInterface` is never called), and files, the phone's
     * content, windows, permissions, downloads, cookies and storage are all withheld.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun lockedDown(view: WebView): WebView {
        view.settings.apply {
            javaScriptEnabled = true
            // Nothing from the network, whatever asks for it.
            blockNetworkLoads = true
            blockNetworkImage = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            // Nothing from the phone: no files, nothing lent by another app.
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            setGeolocationEnabled(CanvasWebPolicy.ALLOWS_GEOLOCATION)
            // No windows: a request for one comes to `onCreateWindow`, which makes none.
            javaScriptCanOpenWindowsAutomatically = CanvasWebPolicy.ALLOWS_NEW_WINDOWS
            setSupportMultipleWindows(true)
            // Nothing kept: no storage, no database, nothing typed into a form.
            domStorageEnabled = false
            @Suppress("DEPRECATION")
            databaseEnabled = false
            @Suppress("DEPRECATION")
            saveFormData = false
            @Suppress("DEPRECATION")
            savePassword = false
            mediaPlaybackRequiresUserGesture = true
            safeBrowsingEnabled = true
        }
        // The app has no other web view, so cookies are refused for all of them and for this one by name.
        CookieManager.getInstance().apply {
            setAcceptCookie(false)
            setAcceptThirdPartyCookies(view, false)
        }
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        // Told to fill its place, or a web view takes its page to have no height and a page laid out by height shows nothing.
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.webViewClient = Client()
        view.webChromeClient = Chrome()
        // A download is offered to this listener and to nothing else, and it does nothing with it.
        view.setDownloadListener { _, _, _, _, _ -> }
        // The start script is in the page itself; where the web view can, it is also run at the start of every
        // frame's document, before anything of the frame's own. Neither runs in the empty document a frame starts
        // with; the script seals that one itself.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, policy.startScript, CanvasWebPolicy.START_SCRIPT_ORIGIN_RULES)
        }
        return view
    }

    /** Decides what the page may load and where it may go. */
    private inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            when (policy.requestDecision(request.url?.toString())) {
                CanvasRequestDecisionKind.ALLOW -> null
                CanvasRequestDecisionKind.BLOCK -> WebResourceResponse("text/plain", "utf-8", BLOCKED_STATUS, BLOCKED_REASON, emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = isCancelled(request.url?.toString())

        @Deprecated("Asked of older web views, which are answered the same way.")
        override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean = isCancelled(url)

        /** A web view whose page has crashed or been killed is done with; the app carries on without it. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.e(TAG, "The canvas's page stopped: ${if (detail.didCrash()) "it crashed" else "it was ended by the phone"}")
            onUnavailable()
            return true
        }

        private fun isCancelled(address: String?): Boolean = when (policy.navigationDecision(address)) {
            CanvasNavigationDecisionKind.ALLOW -> false
            CanvasNavigationDecisionKind.CANCEL -> {
                // The address is part of a reply, so it is not logged.
                Log.i(TAG, "The canvas cancelled a navigation away from its page")
                true
            }
        }
    }

    /** Refuses everything the page asks of the phone. */
    private class Chrome : WebChromeClient() {
        /** `window.open` and links with a target get no window. */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = CanvasWebPolicy.ALLOWS_NEW_WINDOWS

        /** The camera, the microphone and anything else of the kind: refused without the user being asked. */
        override fun onPermissionRequest(request: PermissionRequest) {
            if (!CanvasWebPolicy.ALLOWS_PERMISSIONS) request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, CanvasWebPolicy.ALLOWS_GEOLOCATION, false)
        }

        /** A page that asks for a file is told that none was chosen, and no picker is shown. */
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>?, parameters: FileChooserParams?): Boolean {
            callback?.onReceiveValue(null)
            return !CanvasWebPolicy.ALLOWS_FILE_CHOOSING
        }
    }

    private companion object {
        const val TAG = "CanvasWebPreview"

        /** What a refused request is answered with: nothing, marked as forbidden. */
        const val BLOCKED_STATUS = 403
        const val BLOCKED_REASON = "Blocked"
    }
}
