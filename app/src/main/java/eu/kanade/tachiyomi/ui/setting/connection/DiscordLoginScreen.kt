// AM (DISCORD) -->

package eu.kanade.tachiyomi.ui.setting.connection

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kevinnzou.web.AccompanistWebChromeClient
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import eu.kanade.domain.connection.service.ConnectionPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.connection.ConnectionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Refresh
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.presentation.core.i18n.stringResource

private const val DISCORD_DOMAIN = "https://discord.com"
private const val DISCORD_LOGIN_URL = "$DISCORD_DOMAIN/login"
private const val DISCORD_USER_API_URL = "$DISCORD_DOMAIN/api/v10/users/@me"
private const val JS_TOKEN_NULL = "null"
private const val JS_TOKEN_ERROR = "error"

// From https://github.com/komikku-app/komikku/blob/master/app/src/main/java/eu/kanade/tachiyomi/ui/setting/connections/DiscordLoginScreen.kt
@SuppressLint("SetJavaScriptEnabled")
class DiscordLoginScreen : Screen() {

    @Composable
    override fun Content() {
        val state = rememberWebViewState(url = DISCORD_LOGIN_URL)
        val navigator = LocalNavigator.currentOrThrow
        val webViewNavigator = rememberWebViewNavigator()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        var currentUrl by remember { mutableStateOf(DISCORD_LOGIN_URL) }

        val webViewClient = remember {
            object : AccompanistWebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    url?.let {
                        if (url.contains("/channels/@me") || url.contains("/app")) {
                            view.evaluateJavascript(
                                """
                                (function() {
                                    function fallbackTokenAlert() {
                                        // fallback to alert (kizzy's logic)
                                        try {
                                            var i = document.createElement('iframe');
                                            document.body.appendChild(i);
                                            setTimeout(function() {
                                                try {
                                                    var alt = i.contentWindow.localStorage.token;
                                                    if (alt) {
                                                        alert(alt.slice(1, -1));
                                                    } else {
                                                        alert("$JS_TOKEN_NULL");
                                                    }
                                                } catch (e) {
                                                    alert("$JS_TOKEN_ERROR");
                                                }
                                            }, 1000);
                                        } catch (e) {
                                            alert("$JS_TOKEN_ERROR");
                                        }
                                    }
                                    try {
                                        var token = localStorage.getItem("token");
                                        if (token) {
                                            Android.onRetrieveToken(token.slice(1, -1));
                                        } else {
                                            fallbackTokenAlert();
                                        }
                                    } catch (e) {
                                        fallbackTokenAlert();
                                    }
                                })();
                                """.trimIndent(),
                                null,
                            )
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = false

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let {
                        currentUrl = it
                        if (url.contains(DISCORD_LOGIN_URL)) {
                            view.evaluateJavascript(
                                """localStorage.clear()""",
                                null,
                            )
                        }
                    }
                }

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String?,
                    isReload: Boolean,
                ) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    url?.let {
                        currentUrl = it
                    }
                }
            }
        }

        val webChromeClient = remember {
            object : AccompanistWebChromeClient() {
                override fun onJsAlert(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult,
                ): Boolean {
                    if (message != JS_TOKEN_NULL && message != JS_TOKEN_ERROR) {
                        login(message, context)
                        scope.launch(Dispatchers.Main) {
                            view.loadUrl("about:blank")
                            navigator.pop()
                        }
                    }
                    result.confirm()
                    return true
                }
            }
        }

        Scaffold(
            topBar = {
                Box {
                    Column {
                        AppBar(
                            title = stringResource(
                                MR.strings.login_title,
                                stringResource(AMMR.strings.connection_discord),
                            ),
                            subtitle = currentUrl,
                            navigateUp = { navigator.pop() },
                            actions = {
                                AppBarActions(
                                    persistentListOf(
                                        AppBar.OverflowAction.Item(
                                            icon = MaterialSymbols.Rounded.Refresh,
                                            title = stringResource(MR.strings.action_webview_refresh),
                                            onClick = { webViewNavigator.reload() },
                                        ),
                                        AppBar.OverflowAction.Item(
                                            icon = MaterialSymbols.Rounded.Delete,
                                            title = stringResource(MR.strings.pref_clear_cookies),
                                            onClick = { clearCookies(currentUrl, context) },
                                        ),
                                    ),
                                )
                            },
                        )
                    }

                    when (val loadingState = state.loadingState) {
                        is LoadingState.Initializing -> LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                        )
                        is LoadingState.Loading -> LinearProgressIndicator(
                            progress = { loadingState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                        )
                        else -> {}
                    }
                }
            },
        ) { contentPadding ->
            WebView(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                navigator = webViewNavigator,
                onCreated = { webView ->
                    webView.setDefaultSettings()

                    // Debug mode (chrome://inspect/#devices)
                    if (isDebugBuildType &&
                        0 != webView.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
                    ) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    clearCookies(DISCORD_LOGIN_URL, context)

                    WebStorage.getInstance().deleteOrigin(DISCORD_DOMAIN)

                    webView.addJavascriptInterface(
                        object {
                            @Suppress("unused")
                            @JavascriptInterface
                            fun onRetrieveToken(token: String) {
                                if (token != JS_TOKEN_NULL && token != JS_TOKEN_ERROR) {
                                    login(token, context)
                                    scope.launch(Dispatchers.Main) {
                                        webView.loadUrl("about:blank")
                                        navigator.pop()
                                    }
                                }
                            }
                        },
                        "Android",
                    )
                },
                client = webViewClient,
                chromeClient = webChromeClient,
            )
        }
    }

    private fun login(token: String, context: Context) {
        val connectionManager: ConnectionManager = context.appGraph.connectionManager
        val connectionPreferences: ConnectionPreferences = context.appGraph.connectionPreferences

        connectionPreferences.connectionToken(connectionManager.discord).set(token)
        connectionPreferences.setConnectionCredentials(connectionManager.discord, "Discord", "Logged In")
        context.toast(MR.strings.login_success)
    }

    private fun clearCookies(url: String, context: Context) {
        val networkHelper: NetworkHelper = context.appGraph.networkHelper

        url.toHttpUrlOrNull()?.let {
            val cleared = networkHelper.cookieJar.remove(it)
            logcat { "Cleared $cleared cookies for: $url" }
        }
    }
}
// <-- AM (DISCORD)
