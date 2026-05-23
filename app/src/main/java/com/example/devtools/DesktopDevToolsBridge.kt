package com.example.devtools

import android.util.Log
import com.example.nativecore.TitanBlinkEngineEngine
import org.json.JSONObject

class DesktopDevToolsBridge(private val webView: TitanBlinkEngineEngine) {

    var networkLogChannel: ((String) -> Unit)? = null
    var domTreeGraphChannel: ((String) -> Unit)? = null
    var consoleLogChannel: ((String) -> Unit)? = null

    fun processDevToolsPacket(jsonString: String) {
        try {
            val base = JSONObject(jsonString)
            val packetType = base.optString("type")
            val dataPayload = base.optJSONObject("data") ?: return

            when (packetType) {
                "NETWORK_TRACE" -> {
                    val log = "[${dataPayload.optString("subsystem")}] ${dataPayload.optString("method")} -> ${dataPayload.optString("url")} (Status: ${dataPayload.optInt("status")})"
                    networkLogChannel?.invoke(log)
                }
                "DOM_GRAPH" -> domTreeGraphChannel?.invoke(dataPayload.optString("html"))
                "CONSOLE_STREAM" -> {
                    val log = "[${dataPayload.optString("level").uppercase()}] ${dataPayload.optString("output")}"
                    consoleLogChannel?.invoke(log)
                }
            }
        } catch (e: Exception) {
            Log.e("TitanDevToolsBridge", "Fatal Pipeline Deserialization Deflected Safely", e)
        }
    }

    fun modifyBlinkCssTree(css: String) {
        val sanitized = css.replace("'", "\\'")
        val dynamicScript = """
            (function() {
                var cssNodeElement = document.createElement('style');
                cssNodeElement.textContent = '$sanitized';
                document.head.appendChild(cssNodeElement);
            })();
        """.trimIndent()
        webView.evaluateJavascript(dynamicScript, null)
    }

    fun executeV8TerminalScript(javascript: String) {
        webView.evaluateJavascript(javascript, null)
    }
}
