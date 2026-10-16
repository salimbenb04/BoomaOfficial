package com.booma.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Signs in to YouTube Music inside the app and keeps the session cookies.
 * Google sometimes refuses sign-in inside embedded web views; if that happens, the fix is a Custom Tab.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onSignedIn: (cookie: String) -> Unit) {
    val done = remember { BooleanArray(1) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (done[0] || url == null || !url.startsWith("https://music.youtube.com")) return
                        val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com")
                        // SAPISID is the cookie the web client uses to prove the account is signed in
                        if (cookie != null && cookie.contains("SAPISID")) {
                            done[0] = true
                            onSignedIn(cookie)
                        }
                    }
                }
                loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/")
            }
        },
    )
}

/**
 * Signing out: forget the Google session inside the sign-in page too. Without this, the page still has
 * Google's cookies and signs you straight back in.
 */
fun clearWebSession() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
}
