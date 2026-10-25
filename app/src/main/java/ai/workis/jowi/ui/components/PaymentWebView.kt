package ai.workis.jowi.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ai.workis.jowi.R
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisTheme

/** The panel's orders page the PSP's last redirect lands on (`?odeme=ok|hata`). */
private const val RETURN_PATH = "/workis/panel/siparisler/"

/**
 * The payment institution's hosted page (Moka United, ledger 71). `POST orders/{id}/fund/`
 * may answer `paymentUrl`: the money is NOT held yet — the buyer enters the card on the
 * PSP's page (3-D included), the app never sees a card number. The page ends by sending
 * the buyer to /workis/panel/siparisler/?odeme=ok|hata — an in-app WebView (not a Custom
 * Tab) so that return can be caught: the navigation is cancelled and the overlay closes.
 *
 * `onClose` fires exactly once, when the overlay goes away: the return's verdict
 * (`true` = ok, `false` = hata), `null` when closed by hand (Done or system back).
 * The caller re-reads in every case. The app writes no verdict sentence of its own.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentWebView(url: String, onClose: (Boolean?) -> Unit) {
    val colors = WorkisTheme.colors
    val currentOnClose by rememberUpdatedState(onClose)
    val gate = remember { BooleanArray(1) } // [0] = closed already
    val close: (Boolean?) -> Unit = { verdict ->
        if (!gate[0]) { gate[0] = true; currentOnClose(verdict) }
    }
    Dialog(onDismissRequest = { close(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pay_page_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = { close(null) }) { Text(stringResource(R.string.done), fontSize = 15.sp, color = Kiremit500) }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(colors.canvas.toArgb())
                        // 3-D Secure pages need both
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            private fun caught(view: WebView, uri: Uri?): Boolean {
                                if (uri?.path?.startsWith(RETURN_PATH) != true) return false
                                view.stopLoading()
                                close(uri.getQueryParameter("odeme") == "ok")
                                return true
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                caught(view, request.url)
                            // a POSTed return never reaches shouldOverrideUrlLoading — catch it as it starts
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                if (!caught(view, url?.let(Uri::parse))) super.onPageStarted(view, url, favicon)
                            }
                        }
                        loadUrl(url)
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
            )
        }
    }
}
