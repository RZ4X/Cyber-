package com.example.devtools

import android.util.Log
import com.example.browser.OmniWebEngineCore
import org.json.JSONObject

class OmniDevToolsBridge(private val webView: OmniWebEngineCore) {

    var onNetworkCaptured: ((String) -> Unit)? = null
    var onDomTreeUpdated: ((String) -> Unit)? = null
    var onFpsMetricsCalculated: ((Int) -> Unit)? = null
    var onMediaStreamIntercepted: ((String) -> Unit)? = null

    fun processIncomingJsData(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val eventType = json.optString("type")
            val payload = json.optJSONObject("payload") ?: return

            when (eventType) {
                "NETWORK_MONITOR" -> {
                    val log = "[${payload.optString("provider")}] ${payload.optString("method")} -> ${payload.optString("url")} (Status: ${payload.optInt("status")})"
                    onNetworkCaptured?.invoke(log)
                }
                "NETWORK_ERROR" -> {
                    val log = "[network_error] URL: ${payload.optString("url")} | Message: ${payload.optString("error")}"
                    onNetworkCaptured?.invoke(log)
                }
                "DOM_MUTATION" -> onDomTreeUpdated?.invoke(payload.optString("html"))
                "PERFORMANCE_FPS" -> onFpsMetricsCalculated?.invoke(payload.optInt("fps"))
            }
        } catch (e: Exception) {
            Log.e("OmniBridgeEngine", "Payload Crash Exception", e)
        }
    }

    fun triggerMediaSniffer(mediaUrl: String) {
        onMediaStreamIntercepted?.invoke(mediaUrl)
    }

    fun dispatchCustomJavascript(scriptCode: String) {
        webView.evaluateJavascript(scriptCode, null)
    }

    fun executeLiveCssInjection(cssStyles: String) {
        // Sanitize single quotes to prevent breaking JS string literal boundaries
        val escapedStyles = cssStyles.replace("'", "\\'")
        webView.evaluateJavascript("window.__OmniStyleMutator__.applyLiveCSS('$escapedStyles')", null)
    }
}
