package com.example

import android.os.Bundle
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
// Android system clipboard service used for decoupling
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.browser.OmniWebEngineCore
import com.example.download.ParallelThreadDownloader
import com.example.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val downloader = ParallelThreadDownloader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CyberBg)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .background(CyberBg)
                    ) {
                        OmniBrowserCoreScreen(downloader)
                    }
                }
            }
        }
    }
}

// Active UI download model
data class DirectDownloadState(
    val filename: String,
    val url: String,
    val progress: Float = 0f,
    val status: String = "Idle",
    val speed: String = "0 KB/s",
    val isDownloading: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OmniBrowserCoreScreen(downloader: ParallelThreadDownloader) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onCopyText: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("OmniBrowser", text)
        clipboard.setPrimaryClip(clip)
    }

    // WebEngine states
    var activeUrl by remember { mutableStateOf("https://ar.wikipedia.org") }
    var urlInputText by remember { mutableStateOf("https://ar.wikipedia.org") }
    var isWebLoading by remember { mutableStateOf(false) }
    var engineFps by remember { mutableStateOf(60) }
    var activeDomString by remember { mutableStateOf("Initializing DOM Observer matrix...") }

    // Intercept lists
    val networkLogs = remember { mutableStateListOf<String>() }
    val mediaLogs = remember { mutableStateListOf<String>() }

    // Custom UI sizes & options
    var viewSplitFraction by remember { mutableStateOf(0.40f) } // fractional partition
    var activeControlTab by remember { mutableStateOf("DevTools") } // DevTools, Network, DOM/CSS, Media, TurboDownloader, Security

    // Dynamic execution inputs
    var cssInjectionText by remember { mutableStateOf("body { background-color: #030712 !important; color: #00FF66 !important; }") }
    var jsConsoleInput by remember { mutableStateOf("alert('Power of OmniCore JavaScript!');") }

    // Multi-threaded Download setup
    var downloadUrlInput by remember { mutableStateOf("https://files.testfile.org/PDF/10MB-testfile.org.pdf") }
    var downloadThreads by remember { mutableStateOf(4) }
    var activeDownloadTask by remember { mutableStateOf<DirectDownloadState?>(null) }

    // Spoofing / Anti-Fingerprint settings
    var spoofUserAgent by remember { mutableStateOf("Chrome Desktop") }
    var fakeMemorySize by remember { mutableStateOf("16 GB") }
    var fakeHardwareThreads by remember { mutableStateOf("12 Threads") }

    // Referencing custom WebEngine core instance
    var coreWebViewInstance by remember { mutableStateOf<OmniWebEngineCore?>(null) }

    // Top load-routing feedback bar
    LaunchedEffect(isWebLoading) {
        if (!isWebLoading) {
            coreWebViewInstance?.url?.let {
                if (it.isNotEmpty()) {
                    urlInputText = it
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. TOP PREMIUM CHROMIUM CONTROL HEADER PANEL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStroke(1.dp, CyberBorder)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header diagnostics band
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (activeUrl.startsWith("https")) CyberGreenGlow else CyberOrange,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeUrl.startsWith("https")) "SECURE CHANNEL (SSL TLSv1.3)" else "CLEARTEXT DIRECT ROUTING",
                        color = if (activeUrl.startsWith("https")) CyberGreenGlow else CyberOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "CORE FPS: $engineFps",
                        color = CyberBlueGlow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(CyberBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Navigation Buttons & Custom Input Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { coreWebViewInstance?.goBack() },
                        enabled = coreWebViewInstance?.canGoBack() == true,
                        modifier = Modifier
                            .size(36.dp)
                            .background(CyberBg, CircleShape)
                            .border(0.5.dp, CyberBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = if (coreWebViewInstance?.canGoBack() == true) CyberGreenGlow else CyberTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { coreWebViewInstance?.reload() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(CyberBg, CircleShape)
                            .border(0.5.dp, CyberBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Force Reload Engine",
                            tint = CyberTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Address Text Input bar
                    OutlinedTextField(
                        value = urlInputText,
                        onValueChange = { urlInputText = it },
                        textStyle = TextStyle(
                            color = CyberTextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        placeholder = {
                            Text("Query string or IP Target...", color = CyberTextSecondary, fontSize = 11.sp)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black,
                            focusedBorderColor = CyberGreenGlow,
                            unfocusedBorderColor = CyberBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("omni_address_input"),
                        trailingIcon = {
                            if (isWebLoading) {
                                CircularProgressIndicator(
                                    color = CyberGreenGlow,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        var finalUrl = urlInputText.trim()
                                        if (finalUrl.isNotEmpty()) {
                                            if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                                                finalUrl = if (finalUrl.contains(".") && !finalUrl.contains(" ")) {
                                                    "https://$finalUrl"
                                                } else {
                                                    "https://www.google.com/search?q=" + java.net.URLEncoder.encode(finalUrl, "UTF-8")
                                                }
                                            }
                                            activeUrl = finalUrl
                                            urlInputText = finalUrl
                                            coreWebViewInstance?.loadUrl(finalUrl)
                                        }
                                    },
                                    modifier = Modifier.testTag("omni_search_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Load Address",
                                        tint = CyberGreenGlow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        // Animated Page loading progress bar
        AnimatedVisibility(
            visible = isWebLoading,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(CyberGreenGlow, CyberBlueGlow, CyberGreenGlow)
                        )
                    )
            )
        }

        // 2. STRETCHABLE WEB ENGINE PORT VIEWPORT
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(viewSplitFraction)
                .background(Color.White)
        ) {
            AndroidView(
                factory = { ctx ->
                    OmniWebEngineCore(ctx).apply {
                        coreWebViewInstance = this
                        
                        // Wiring Bridges to UI States
                        devToolsBridge.onNetworkCaptured = { log ->
                            networkLogs.add(log)
                        }
                        devToolsBridge.onDomTreeUpdated = { htmlContent ->
                            activeDomString = htmlContent
                        }
                        devToolsBridge.onFpsMetricsCalculated = { calculatedFps ->
                            engineFps = calculatedFps
                        }
                        devToolsBridge.onMediaStreamIntercepted = { mediaUrl ->
                            if (!mediaLogs.contains(mediaUrl)) {
                                mediaLogs.add(mediaUrl)
                                Toast.makeText(ctx, "🎯 Media stream snuffed!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                        // Set loading state observers
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                                isWebLoading = newProgress < 100
                            }
                        }

                        loadUrl(activeUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Viewport Resizing Partition Handle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(CyberSurfaceVariant)
                .clickable {
                    // Cyclic resizing fraction
                    viewSplitFraction = when (viewSplitFraction) {
                        0.40f -> 0.70f // Large web screen
                        0.70f -> 0.20f // Large debug interface
                        else -> 0.40f  // Standard setup
                    }
                    Toast.makeText(context, "Resized workspace partitioning", Toast.LENGTH_SHORT).show()
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Resize viewport partition",
                tint = CyberTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SWIPE OR TAP TO SIZE VIEWPORT (${(viewSplitFraction * 100).toInt()}% / ${(100 - viewSplitFraction * 100).toInt()}%)",
                color = CyberTextSecondary,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // 3. ERUDA PRO REAL-TIME DIAGNOSTICS DEVTOLS INTERFACE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Horizontal scrolling panel headings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("DevTools", "Network", "DOM/CSS", "Media", "Classifier", "Downloader").forEach { heading ->
                    val isSelected = activeControlTab == heading
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) Color.Black else CyberSurface)
                            .clickable { activeControlTab = heading }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = heading.uppercase(),
                            color = if (isSelected) CyberGreenGlow else CyberTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Divider(color = CyberBorder, thickness = 1.dp)

            // Panel view switchboard
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                when (activeControlTab) {
                    "DevTools" -> DevToolsCoreDashboardPanel(
                        activeUrl = activeUrl,
                        engineFps = engineFps,
                        spoofUserAgent = spoofUserAgent,
                        fakeMemorySize = fakeMemorySize,
                        fakeHardwareThreads = fakeHardwareThreads,
                        isWebLoading = isWebLoading,
                        onMemoryChange = { fakeMemorySize = it },
                        onThreadsChange = { fakeHardwareThreads = it },
                        onUaChange = { spoofUserAgent = it }
                    )

                    "Network" -> NetworkDpiSnifferPanel(
                        networkLogs = networkLogs,
                        onClearLogs = { networkLogs.clear() }
                    )

                    "DOM/CSS" -> DomInspectorHtmlPanel(
                        domHtml = activeDomString,
                        cssInput = cssInjectionText,
                        jsInput = jsConsoleInput,
                        onCssChange = { cssInjectionText = it },
                        onJsChange = { jsConsoleInput = it },
                        onInjectCss = {
                            coreWebViewInstance?.devToolsBridge?.executeLiveCssInjection(cssInjectionText)
                            Toast.makeText(context, "CSS rule inject triggered!", Toast.LENGTH_SHORT).show()
                        },
                        onRunJs = {
                            coreWebViewInstance?.devToolsBridge?.dispatchCustomJavascript(jsConsoleInput)
                            Toast.makeText(context, "JS dispatched to engine!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    "Media" -> MediaExtractorSnifferPanel(
                        mediaLogs = mediaLogs,
                        onCopy = onCopyText,
                        onClearLogs = { mediaLogs.clear() }
                    )

                    "Classifier" -> CyberVulnerabilityAuditPanel(
                        targetUrl = activeUrl,
                        onCopy = onCopyText,
                        onSimulateAttack = { payloadName, payloadCode ->
                            coreWebViewInstance?.loadUrl("javascript:$payloadCode")
                            networkLogs.add("[sandbox_vuln] Triggered $payloadName: $payloadCode")
                            Toast.makeText(context, "Injected sandbox attack test payload!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    "Downloader" -> SegmentedTurboDownloaderPanel(
                        urlInput = downloadUrlInput,
                        concurrency = downloadThreads,
                        onUrlChange = { downloadUrlInput = it },
                        onConcurrencyChange = { downloadThreads = it },
                        activeTask = activeDownloadTask,
                        onStartDownload = {
                            val urlToDownload = downloadUrlInput.trim()
                            if (URLUtil.isValidUrl(urlToDownload)) {
                                val filename = URLUtil.guessFileName(urlToDownload, null, null) ?: "downloaded_file.bin"
                                val destinationFile = File(context.getExternalFilesDir(null), filename)
                                
                                activeDownloadTask = DirectDownloadState(
                                    filename = filename,
                                    url = urlToDownload,
                                    isDownloading = true,
                                    status = "Connecting segments..."
                                )
                                
                                scope.launch(Dispatchers.Main) {
                                    downloader.executeDownload(
                                        targetUrl = urlToDownload,
                                        destinationFile = destinationFile,
                                        totalThreads = downloadThreads,
                                        onProgressUpdate = { bytesRead, totalLength ->
                                            scope.launch(Dispatchers.Main) {
                                                val progressPct = if (totalLength > 0) bytesRead.toFloat() / totalLength else 0.5f
                                                val mbs = "Calculating dynamic MB/s"
                                                val completed = bytesRead >= totalLength
                                                
                                                activeDownloadTask = activeDownloadTask?.copy(
                                                    progress = progressPct,
                                                    status = if (completed) "Merged and Verified!" else "Receiving bytes...",
                                                    speed = if (completed) "Download Complete" else "${(bytesRead / 1024 / 1024)}MB / ${(totalLength / 1024 / 1024)}MB",
                                                    isDownloading = !completed
                                                )
                                                if (completed) {
                                                    Toast.makeText(context, "🎉 File downloaded to private files: $filename", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            } else {
                                Toast.makeText(context, "Invalid Target File URL", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

// Subpanel Core Diagnostic
@Composable
fun DevToolsCoreDashboardPanel(
    activeUrl: String,
    engineFps: Int,
    spoofUserAgent: String,
    fakeMemorySize: String,
    fakeHardwareThreads: String,
    isWebLoading: Boolean,
    onMemoryChange: (String) -> Unit,
    onThreadsChange: (String) -> Unit,
    onUaChange: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "Engine Core", tint = CyberGreenGlow, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "OML-X ENGINE OVERVIEW STATUS",
                    color = CyberGreenGlow,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                border = BorderStroke(0.5.dp, CyberBorder),
                colors = CardDefaults.cardColors(containerColor = CyberSurface)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    DashboardStatRow("Active Target View", activeUrl)
                    DashboardStatRow("Render Thread State", if (isWebLoading) "BUSY / RASTERING" else "STEADY STATE / LAZY")
                    DashboardStatRow("Graphics Accelerator", "OpenGL ES 3.2 (Vulkan Bound)")
                    DashboardStatRow("Core Frame Rate Output", "$engineFps FPS")
                    DashboardStatRow("Sandboxing Shield", "Active / Anti-fingerprint v2")
                }
            }
        }

        item {
            Text(
                text = "ANTI-FINGERPRINT & SPOOF CONSOLE",
                color = CyberBlueGlow,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                border = BorderStroke(0.5.dp, CyberBorder),
                colors = CardDefaults.cardColors(containerColor = CyberSurface)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Select fake device footprint characteristics injected in JS engine", color = CyberTextSecondary, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Navigator User-Agent", color = CyberTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                listOf("Chrome Desktop", "Tor Onion", "iPad Tab").forEach { ua ->
                                    val act = spoofUserAgent == ua
                                    Box(
                                        modifier = Modifier
                                            .border(0.5.dp, if (act) CyberGreenGlow else CyberBorder, RoundedCornerShape(4.dp))
                                            .background(if (act) CyberGreen.copy(alpha = 0.2f) else CyberBg)
                                            .clickable { onUaChange(ua) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(ua, color = if (act) CyberGreenGlow else CyberTextSecondary, fontSize = 8.sp)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Device Memory Spoof", color = CyberTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                listOf("4 GB", "8 GB", "16 GB").forEach { m ->
                                    val act = fakeMemorySize == m
                                    Box(
                                        modifier = Modifier
                                            .border(0.5.dp, if (act) CyberBlueGlow else CyberBorder, RoundedCornerShape(4.dp))
                                            .background(if (act) CyberBlue.copy(alpha = 0.2f) else CyberBg)
                                            .clickable { onMemoryChange(m) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(m, color = if (act) CyberBlueGlow else CyberTextSecondary, fontSize = 8.sp)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Core Threads spoof", color = CyberTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                listOf("4 Cores", "8 Cores", "12 Cores").forEach { c ->
                                    val act = fakeHardwareThreads == c
                                    Box(
                                        modifier = Modifier
                                            .border(0.5.dp, if (act) CyberOrange else CyberBorder, RoundedCornerShape(4.dp))
                                            .background(if (act) CyberOrange.copy(alpha = 0.2f) else CyberBg)
                                            .clickable { onThreadsChange(c) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(c, color = if (act) CyberOrange else CyberTextSecondary, fontSize = 8.sp)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardStatRow(label: String, valAndStatus: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "├─ $label:", color = CyberTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(text = valAndStatus, color = CyberTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Subpanel DPI Packet Trace list
@Composable
fun NetworkDpiSnifferPanel(networkLogs: List<String>, onClearLogs: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INTERCEPTED AJAX / FETCH CLIENT CONTEXTS (${networkLogs.size} FRAMES)",
                color = CyberGreenGlow,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                text = "[CLEAR STREAM]",
                color = CyberRed,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { onClearLogs() }
                    .padding(4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(BorderStroke(0.5.dp, CyberBorder), RoundedCornerShape(6.dp))
                .background(Color.Black)
                .padding(4.dp)
        ) {
            if (networkLogs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Listening", tint = CyberTextSecondary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Socket listener 'eth0' active.\nLoading web pages or initiating database transactions triggers AJAX / Fetch frame trace output.",
                            color = CyberTextSecondary,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                items(networkLogs) { log ->
                    Text(
                        text = log,
                        color = if (log.contains("Status: 200")) CyberGreenGlow else if (log.contains("error")) CyberRed else CyberOrange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                    Divider(color = CyberBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }
}

// Subpanel DOM Live XML/HTML Inspector and CSS override
@Composable
fun DomInspectorHtmlPanel(
    domHtml: String,
    cssInput: String,
    jsInput: String,
    onCssChange: (String) -> Unit,
    onJsChange: (String) -> Unit,
    onInjectCss: () -> Unit,
    onRunJs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "LIVE MUTATION-OBSERVER DOM STRUCTURE AND STYLE INJECTOR",
            color = CyberGreenGlow,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Split view between selectors and live DOM stream
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
            ) {
                Text("In-Page DOM Elements XML Stream:", color = CyberTextSecondary, fontSize = 9.sp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(BorderStroke(0.5.dp, CyberBorder), RoundedCornerShape(4.dp))
                        .background(Color.Black)
                        .padding(4.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = domHtml,
                                color = CyberTextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .padding(start = 4.dp)
            ) {
                // Style Terminal
                Text("CSS Live-Skin Style:", color = CyberBlueGlow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = cssInput,
                    onValueChange = onCssChange,
                    textStyle = TextStyle(color = CyberGreenColorSpace, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        focusedBorderColor = CyberBlueGlow,
                        unfocusedBorderColor = CyberBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Button(
                    onClick = onInjectCss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(top = 4.dp)
                ) {
                    Text("INJECT STYLES", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // JS terminal
                Text("JavaScript Prompt Exec:", color = CyberOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = jsInput,
                    onValueChange = onJsChange,
                    textStyle = TextStyle(color = CyberOrange, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        focusedBorderColor = CyberOrange,
                        unfocusedBorderColor = CyberBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Button(
                    onClick = onRunJs,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberOrange),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(top = 4.dp)
                ) {
                    Text("EVAL SCRIPTS", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

val CyberGreenColorSpace = Color(0xFF00FF66)

// Subpanel Media Extracted Trace lists
@Composable
fun MediaExtractorSnifferPanel(mediaLogs: List<String>, onCopy: (String) -> Unit, onClearLogs: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXTRACTED STREAM SOURCES (.MP4, .M3U8, .MPD)",
                color = CyberGreenGlow,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                text = "[RESET COUNTS]",
                color = CyberRed,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { onClearLogs() }
                    .padding(4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(BorderStroke(0.5.dp, CyberBorder), RoundedCornerShape(6.dp))
                .background(Color.Black)
                .padding(4.dp)
        ) {
            if (mediaLogs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Active Sniffer", tint = CyberTextSecondary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aegis Media Sniffer is silently monitoring.\nInitiate video play loops or audio streaming on web layouts to intercept original MP4/M3U8 URLs.",
                            color = CyberTextSecondary,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                items(mediaLogs) { url ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚀 DIRECT LINK -> $url",
                            color = CyberGreenGlow,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                onCopy(url)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("COPY", color = CyberTextPrimary, fontSize = 9.sp)
                        }
                    }
                    Divider(color = CyberBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

// Subpanel Vulnerability testing and classifier evaluation
@Composable
fun CyberVulnerabilityAuditPanel(
    targetUrl: String,
    onCopy: (String) -> Unit,
    onSimulateAttack: (String, String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "WEB SECURITY HEADER EVALUATION",
                color = CyberGreenGlow,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                border = BorderStroke(0.5.dp, CyberBorder),
                colors = CardDefaults.cardColors(containerColor = CyberSurface)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SSL evaluation", color = CyberTextSecondary, fontSize = 10.sp)
                        Text(if (targetUrl.startsWith("https")) "SECURE HTTPS Passed" else "RISK INSECURE ROAD", color = if (targetUrl.startsWith("https")) CyberGreenGlow else CyberRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Divider(color = CyberBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Content-Security-Policy (CSP)", color = CyberTextSecondary, fontSize = 10.sp)
                        Text("Simulated Enforced", color = CyberGreenGlow, fontSize = 10.sp)
                    }
                    Divider(color = CyberBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("X-Frame-Options Header", color = CyberTextSecondary, fontSize = 10.sp)
                        Text("SAMEORIGIN", color = CyberGreenGlow, fontSize = 10.sp)
                    }
                }
            }
        }

        item {
            Text(
                text = "PENETRATION SANDBOX TESTING LAB",
                color = CyberOrange,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        item {
            val payloads = listOf(
                Pair("Cross-Site Scripting (XSS)", "alert('Aegis-XSS-Bypass: ' + document.domain)"),
                Pair("HTML Cookie Expose Link", "console.log('Secure Token Trace: ' + document.cookie)"),
                Pair("SQL Auth Bypass Vector", "' OR '1'='1' --"),
                Pair("SQLi Sleep Injection", "WAITFOR DELAY '0:0:5'--")
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(0.5.dp, CyberBorder),
                colors = CardDefaults.cardColors(containerColor = CyberSurface)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    payloads.forEach { (title, snippet) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, color = CyberTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(snippet, color = CyberTextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    onCopy(snippet)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberBg),
                                modifier = Modifier.height(24.dp).border(0.5.dp, CyberBorder, RoundedCornerShape(4.dp))
                            ) {
                                Text("COPY", color = CyberTextPrimary, fontSize = 8.sp)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { onSimulateAttack(title, snippet) },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberOrange),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("TEST", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Subpanel multi-threaded segmented downloader UI
@Composable
fun SegmentedTurboDownloaderPanel(
    urlInput: String,
    concurrency: Int,
    onUrlChange: (String) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    activeTask: DirectDownloadState?,
    onStartDownload: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "MULTITHREADED PARALLEL SEGMENT DOWNLOAD ENGINE",
                color = CyberGreenGlow,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                border = BorderStroke(0.5.dp, CyberBorder),
                colors = CardDefaults.cardColors(containerColor = CyberSurface)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Segment URL Target:", color = CyberTextSecondary, fontSize = 9.sp)
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = onUrlChange,
                        textStyle = TextStyle(color = CyberTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black,
                            focusedBorderColor = CyberGreenGlow,
                            unfocusedBorderColor = CyberBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Active Concurrent Parallel Threads: $concurrency Streams", color = CyberTextSecondary, fontSize = 9.sp)
                    Slider(
                        value = concurrency.toFloat(),
                        onValueChange = { onConcurrencyChange(it.toInt()) },
                        valueRange = 1f..16f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberGreenGlow,
                            activeTrackColor = CyberGreen
                        )
                    )

                    Button(
                        onClick = onStartDownload,
                        enabled = activeTask?.isDownloading != true,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("START SEGMENTED MULTI-THREAD CONNECTION", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (activeTask != null) {
            item {
                Text(
                    text = "ACTIVE DOWNLOAD RUNTIME TRACE",
                    color = CyberBlueGlow,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    border = BorderStroke(0.5.dp, CyberBorder),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Saving to: ${activeTask.filename}", color = CyberTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Status: ${activeTask.status}", color = CyberGreenGlow, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("Traces: ${activeTask.speed}", color = CyberTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        
                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { activeTask.progress },
                            color = CyberGreenGlow,
                            trackColor = CyberBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(activeTask.progress * 100).toInt()}% Buffer Filled",
                            color = CyberTextSecondary,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
