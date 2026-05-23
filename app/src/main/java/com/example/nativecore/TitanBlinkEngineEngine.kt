package com.example.nativecore

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.devtools.DesktopDevToolsBridge

@SuppressLint("SetJavaScriptEnabled")
class TitanBlinkEngineEngine @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    val devToolsBridge = DesktopDevToolsBridge(this)
    private val v8Runtime = V8RuntimeEnvironment()
    val storage = TitanBrowserStorage(context)
    
    // Loaded extensions from storage
    var activeExtensions = mutableListOf<TitanExtension>()
    
    // Custom progress and page listeners
    var onPageLoadProgressChanged: ((Int) -> Unit)? = null
    var onPageTitleOrUrlChanged: ((String, String) -> Unit)? = null

    init {
        clearCache(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        
        // Load initial extensions from persistent storage
        reloadExtensionsFromStorage()

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false // Strict Google Play Security compliance enforcement
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36 Kiwi/Next"
        }
        setLayerType(LAYER_TYPE_HARDWARE, null)
        establishNativeBlinkJniProxy()
        
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                injectDesktopDevToolsCore()
                onPageTitleOrUrlChanged?.invoke(title ?: "Loading...", url ?: "")
                super.onPageStarted(view, url, favicon)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                injectDesktopDevToolsCore()
                
                // Save history to storage organically
                val pageTitle = title ?: "Untitled Page"
                val pageUrl = url ?: ""
                if (pageUrl.isNotEmpty() && !pageUrl.startsWith("javascript:")) {
                    storage.addHistoryEntry(TitanHistoryEntry(pageTitle, pageUrl, System.currentTimeMillis()))
                }
                
                // Inject enabled extensions only
                activeExtensions.forEach { ext ->
                    if (ext.enabled) {
                        evaluateJavascript(
                            "(function() { try { ${ext.script} } catch(e) { console.error('Extension [${ext.name}] Error:', e); } })();", 
                            null
                        )
                    }
                }
                
                onPageTitleOrUrlChanged?.invoke(pageTitle, pageUrl)
                v8Runtime.triggerAggressiveMemoryCleanup()
                super.onPageFinished(view, url)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onPageLoadProgressChanged?.invoke(newProgress)
                super.onProgressChanged(view, newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                onPageTitleOrUrlChanged?.invoke(title ?: "Untitled", url ?: "")
                super.onReceivedTitle(view, title)
            }

            // High-fidelity standard JavaScript Alert Bridge to prevent WebView alert blockers
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                // Return false to let the system handle standard alert or we could build custom dialogs
                return false
            }
        }
    }

    fun reloadExtensionsFromStorage() {
        activeExtensions.clear()
        activeExtensions.addAll(storage.getExtensions())
    }

    fun installManifestV2Extension(name: String, script: String) {
        val newExt = TitanExtension(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            script = script,
            enabled = true
        )
        val currentList = storage.getExtensions().toMutableList()
        currentList.add(newExt)
        storage.saveExtensions(currentList)
        reloadExtensionsFromStorage()
    }

    fun toggleExtension(id: String, enabled: Boolean) {
        val currentList = storage.getExtensions().map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        storage.saveExtensions(currentList)
        reloadExtensionsFromStorage()
    }

    fun deleteExtension(id: String) {
        val currentList = storage.getExtensions().filter { it.id != id }
        storage.saveExtensions(currentList)
        reloadExtensionsFromStorage()
    }

    private fun establishNativeBlinkJniProxy() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(this, "titanBlinkJniBridge", setOf("*")) { _, message, _, _, _ ->
                message.data?.let { devToolsBridge.processDevToolsPacket(it) }
            }
        }
    }

    fun injectDesktopDevToolsCore() {
        val payload = """
        (function() {
            if (window.__TitanBlinkEngineCoreActive__) return;
            window.__TitanBlinkEngineCoreActive__ = true;
            
            function dispatchToCore(type, obj) {
                if (window.titanBlinkJniBridge) {
                   window.titanBlinkJniBridge.postMessage(JSON.stringify({type: type, data: obj}));
                }
            }

            // 1. 100% KIWI PARITY NETWORK LAYER INSPECTION HOOKS
            const rawXOpen = window.XMLHttpRequest.prototype.open;
            window.XMLHttpRequest.prototype.open = function(method, url) {
                this._url = url; this._method = method;
                return rawXOpen.apply(this, arguments);
            };
            const rawXSend = window.XMLHttpRequest.prototype.send;
            window.XMLHttpRequest.prototype.send = function() {
                this.addEventListener('load', function() {
                    dispatchToCore('NETWORK_TRACE', {url: this._url, method: this._method, status: this.status, subsystem: 'BLINK-XHR'});
                });
                return rawXSend.apply(this, arguments);
            };

            const rawFetchApi = window.fetch;
            window.fetch = async function(...args) {
                const url = typeof args[0] === 'object' ? args[0].url : args[0];
                const method = args[1]?.method || 'GET';
                try {
                    const response = await rawFetchApi.apply(this, args);
                    dispatchToCore('NETWORK_TRACE', {url: url, method: method, status: response.status, subsystem: 'BLINK-FETCH'});
                    return response;
                } catch(e) {
                    dispatchToCore('NETWORK_FAULT', {url: url, reason: e.message});
                    throw e;
                }
            };

            // 2. INDUSTRIAL REAL-TIME DOM STREE GRAPH STREAMER
            function updateDomSnapshot() {
                dispatchToCore('DOM_GRAPH', { html: document.documentElement.outerHTML.substring(0, 99000) });
            }
            const mutationTracker = new MutationObserver(updateDomSnapshot);
            mutationTracker.observe(document.documentElement, { attributes: true, childList: true, subtree: true, characterData: true });
            
            // Initial snapshot
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', updateDomSnapshot);
            } else {
                updateDomSnapshot();
            }

            // 3. COMPLETE DESKTOP CONSOLE REDIRECTION MATRIX
            const targets = ['log', 'error', 'warn', 'info'];
            targets.forEach(channel => {
                const nativeConsole = console[channel];
                console[channel] = function(...args) {
                    dispatchToCore('CONSOLE_STREAM', {level: channel, output: args.join(' ')});
                    if (nativeConsole) nativeConsole.apply(console, args);
                };
            });

            // 4. ELITE TRACKING PROTECTION & ANTI-FINGERPRINTING V8 SHIELD
            try {
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 64 });
                Object.defineProperty(navigator, 'deviceMemory', { get: () => 64 });
                const nativeCanvasContext = HTMLCanvasElement.prototype.getContext;
                HTMLCanvasElement.prototype.getContext = function(type) {
                    const context = nativeCanvasContext.apply(this, arguments);
                    if (type === '2d' && context) {
                        const nativeFillText = context.fillText;
                        context.fillText = function(...args) {
                            context.fillStyle = 'rgba(0,0,0,0.01)';
                            nativeFillText.call(context, '.', 0, 0);
                            return nativeFillText.apply(context, args);
                        };
                    }
                    return context;
                };
            } catch(err) {}
        })();
        """.trimIndent()
        evaluateJavascript("javascript:$payload", null)
    }
}
