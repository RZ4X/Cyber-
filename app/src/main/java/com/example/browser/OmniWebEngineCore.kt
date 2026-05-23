package com.example.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.devtools.OmniDevToolsBridge

@SuppressLint("SetJavaScriptEnabled")
class OmniWebEngineCore @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    val devToolsBridge = OmniDevToolsBridge(this)

    init {
        clearCache(true)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        }
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setupNativeMessageBridge()
        
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                injectDevToolsCorePayload()
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectDevToolsCorePayload()
                super.onPageFinished(view, url)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request?.url?.toString()?.let { url ->
                    if (url.contains(".m3u8") || url.contains(".mpd") || url.contains(".mp4") || url.contains("video/") || url.contains("audio/")) {
                        devToolsBridge.triggerMediaSniffer(url)
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun setupNativeMessageBridge() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(this, "nativeOmniBridge", setOf("*")) { _, message, _, _, _ ->
                message.data?.let { devToolsBridge.processIncomingJsData(it) }
            }
        }
    }

    fun injectDevToolsCorePayload() {
        val payload = """
        (function() {
            if (window.__OmniSystemHooked__) return;
            window.__OmniSystemHooked__ = true;
            
            function emitToNative(type, body) {
                if (window.nativeOmniBridge) {
                    window.nativeOmniBridge.postMessage(JSON.stringify({type: type, payload: body}));
                }
            }

            // 1. TRUE API & DEEP NETWORK INTERCEPTION HOOKS
            const rawXHROpen = window.XMLHttpRequest.prototype.open;
            window.XMLHttpRequest.prototype.open = function(method, url) {
                this._url = url;
                this._method = method;
                return rawXHROpen.apply(this, arguments);
            };
            const rawXHRSend = window.XMLHttpRequest.prototype.send;
            window.XMLHttpRequest.prototype.send = function() {
                this.addEventListener('load', function() {
                    emitToNative('NETWORK_MONITOR', {url: this._url, method: this._method, status: this.status, provider: 'XHR'});
                });
                return rawXHRSend.apply(this, arguments);
            };

            const rawFetch = window.fetch;
            window.fetch = async function(...args) {
                const url = typeof args[0] === 'object' ? args[0].url : args[0];
                const method = args[1]?.method || 'GET';
                try {
                    const response = await rawFetch.apply(this, args);
                    emitToNative('NETWORK_MONITOR', {url: url, method: method, status: response.status, provider: 'FETCH'});
                    return response;
                } catch(err) {
                    emitToNative('NETWORK_ERROR', {url: url, error: err.message});
                    throw err;
                }
            };

            // 2. LIVE REAL-TIME DOM TREE MATRIX OBSERVER
            const domObserver = new MutationObserver(() => {
                emitToNative('DOM_MUTATION', { html: document.documentElement.outerHTML.substring(0, 50000) });
            });
            domObserver.observe(document.documentElement, { attributes: true, childList: true, subtree: true });

            // Initial DOM upload
            setTimeout(() => {
                emitToNative('DOM_MUTATION', { html: document.documentElement.outerHTML.substring(0, 50000) });
            }, 1000);

            // 3. HIGH-FREQUENCY FPS AND RUNTIME METER
            let frameCounter = 0, benchmarkTimestamp = performance.now();
            function runFpsEngine() {
                frameCounter++;
                const currentTimestamp = performance.now();
                if (currentTimestamp >= benchmarkTimestamp + 1000) {
                    emitToNative('PERFORMANCE_FPS', { fps: Math.round((frameCounter * 1000) / (currentTimestamp - benchmarkTimestamp)) });
                    frameCounter = 0;
                    benchmarkTimestamp = currentTimestamp;
                }
                requestAnimationFrame(runFpsEngine);
            }
            requestAnimationFrame(runFpsEngine);
            
            // 4. INJECTED DYNAMIC CSS STYLE ENGINE
            window.__OmniStyleMutator__ = {
                applyLiveCSS: function(styleRule) {
                    const styleElement = document.createElement('style');
                    styleElement.textContent = styleRule;
                    document.head.appendChild(styleElement);
                }
            };

            // 5. ANTI-FINGERPRINTING OBFUSCATION MATRIX
            try {
                const canvasContextHook = HTMLCanvasElement.prototype.getContext;
                HTMLCanvasElement.prototype.getContext = function(type) {
                    const ctx = canvasContextHook.apply(this, arguments);
                    if (type === '2d' && ctx) {
                        const rawFillText = ctx.fillText;
                        ctx.fillText = function() {
                            ctx.fillStyle = 'rgba(0,0,0,0.01)';
                            rawFillText.call(ctx, '.', 0, 0);
                            return rawFillText.apply(ctx, arguments);
                        };
                    }
                    return ctx;
                };
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });
                Object.defineProperty(navigator, 'deviceMemory', { get: () => 16 });
            } catch (e) {}
        })();
        """.trimIndent()
        evaluateJavascript("javascript:$payload", null)
    }
}
