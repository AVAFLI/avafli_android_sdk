package com.avafli.avaflisdk.ui.v2

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.avafli.avaflisdk.R
import com.avafli.avaflisdk.AvafliConstants
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// In-experience legal webview (2.9.4): Official Rules and the Privacy Policy
// open INSIDE the drawer instead of bouncing the user out to a browser
// (ACTION_VIEW). The privacy page is loaded with ?app=1 — the parallel in-app
// build of winrmedia.com/sdk/privacy that renders a "Delete my data" section —
// and hands control back to the SDK via a delete-bridge navigation —
// `avafli://delete`, with the legacy `winr://delete` still honored (the hosted
// privacy page predates the Avafli rebrand and may emit either) — which the
// screen intercepts and routes into the EXISTING destructive opt-out
// confirmation + authenticated erasure flow. No manifest changes, no exported
// components, no scheme registration: the schemes exist only as navigations
// the in-app WebView client watches for.

/** A legal document to present in the in-experience webview. */
internal data class AvafliLegalPage(val title: String, val url: String)

/**
 * Route to the in-experience legal webview, provided by the V2 root. Null
 * outside the V2 experience — [rememberAvafliLegalOpener] then falls back to
 * the old browser hand-off so a stray composition never dead-ends a link.
 */
internal val LocalAvafliLegalOpener =
    staticCompositionLocalOf<((AvafliLegalPage) -> Unit)?> { null }

/**
 * Pure URL policy for the legal webview — split from the composable so the
 * ?app=1 construction and the delete-bridge decision are unit-testable.
 */
internal object AvafliLegalWebPolicy {

    /**
     * The privacy URL for IN-APP presentation: `?app=1` appended via okhttp's
     * HttpUrl builder, so a base URL that already carries a query string
     * extends correctly. A URL already carrying an `app` param is returned
     * untouched; an unparseable URL passes through unchanged.
     */
    fun privacyUrlForApp(base: String = AvafliConstants.PRIVACY_URL): String {
        val url = base.toHttpUrlOrNull() ?: return base
        if (url.queryParameter("app") != null) return base
        return url.newBuilder().addQueryParameter("app", "1").build().toString()
    }

    /**
     * shouldOverrideUrlLoading decision: is [url] the privacy page's
     * delete-bridge navigation (the in-app build's "Delete my data" action
     * handing control back to the SDK)? Both `avafli://delete` and the legacy
     * `winr://delete` are honored — the hosted page predates the Avafli
     * rebrand and may emit either. Scheme and host are matched
     * case-insensitively; trailing slashes and query strings are tolerated.
     * Everything else — including any other `avafli://` or `winr://` path —
     * loads normally.
     */
    fun isDeleteBridge(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val normalized = url.trim().lowercase()
        val scheme = listOf("avafli:", "winr:").firstOrNull(normalized::startsWith)
            ?: return false
        val rest = normalized
            .removePrefix(scheme)
            .trimStart('/')
            .substringBefore('?')
            .trimEnd('/')
        return rest == "delete"
    }
}

/**
 * Resolves how a legal link opens: inside the experience (the V2 root provides
 * [LocalAvafliLegalOpener]) or, outside it, the legacy ACTION_VIEW browser
 * hand-off. A null URL is a no-op, matching the old openers.
 */
@Composable
internal fun rememberAvafliLegalOpener(): (title: String, url: String?) -> Unit {
    val opener = LocalAvafliLegalOpener.current
    val context = LocalContext.current
    return remember(opener, context) {
        { title, url ->
            if (url != null) {
                if (opener != null) {
                    opener(AvafliLegalPage(title = title, url = url))
                } else {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    } catch (_: Exception) {
                        // No browser available — silently ignore.
                    }
                }
            }
        }
    }
}

/**
 * The legal webview screen: slim header (dynamic title + X) over a WebView on
 * the gunmetal drawer chrome, with a loading indicator and a simple error +
 * retry state. JavaScript is ON (the page needs it) but the surface stays
 * minimal: NO addJavascriptInterface objects — the only way the page reaches
 * the SDK is the intercepted `winr://delete` navigation ([onDeleteBridge]).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AvafliV2LegalWebViewScreen(
    accent: Color,
    page: AvafliLegalPage,
    onDeleteBridge: () -> Unit,
    onClose: () -> Unit,
) {
    var isLoading by remember(page.url) { mutableStateOf(true) }
    var loadFailed by remember(page.url) { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // The WebViewClient is created once in the factory — route its callbacks
    // through rememberUpdatedState so they never go stale on recomposition.
    val currentOnDeleteBridge by rememberUpdatedState(onDeleteBridge)

    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().background(AvafliV2Color.gunmetal)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                page.title,
                style = AvafliV2Font.inter(17.sp, FontWeight.Bold, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AvafliV2CircleButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.avafli_close_x),
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            // Keyed on the URL: a different document rebuilds the WebView (and
            // the client closures over this composition's state).
            key(page.url) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    if (AvafliLegalWebPolicy.isDeleteBridge(request?.url?.toString())) {
                                        currentOnDeleteBridge()
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    // Sub-resource failures don't blank the page —
                                    // only a main-frame failure earns the error state.
                                    if (request?.isForMainFrame == true) {
                                        loadFailed = true
                                        isLoading = false
                                    }
                                }
                            }
                            loadUrl(page.url)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isLoading && !loadFailed) {
                // Opaque cover, not just a spinner overlay: the WebView's own
                // white flash never shows through the dark chrome.
                Box(
                    Modifier.fillMaxSize().background(AvafliV2Color.gunmetal),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            if (loadFailed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AvafliV2Color.gunmetal)
                        .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        AvafliV2Strings.LEGAL_LOAD_FAILED,
                        style = AvafliV2Font.inter(
                            14.sp,
                            color = AvafliV2Color.textTertiary,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    AvafliV2PillButton(
                        accent = accent,
                        title = AvafliV2Strings.RETRY,
                        modifier = Modifier.padding(top = 20.dp).width(220.dp),
                    ) {
                        loadFailed = false
                        isLoading = true
                        webView?.reload()
                    }
                }
            }
        }
    }
}
