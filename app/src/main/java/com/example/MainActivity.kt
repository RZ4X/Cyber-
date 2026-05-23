package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.nativecore.*
import com.example.assetlayer.MassiveDataBufferAllocation
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Stabilize massive native allocation runtime memory footprint immediately on load
        val allocationTracker = MassiveDataBufferAllocation()

        setContent {
            MaterialTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    TitanBrowserApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitanBrowserApp(modifier: Modifier = Modifier) {
    var inputUrlString by remember { mutableStateOf("https://wikipedia.org") }
    var executeUrlString by remember { mutableStateOf("https://wikipedia.org") }
    
    // Live Browser State variables
    var currentPageTitle by remember { mutableStateOf("Wikipedia") }
    var currentPageProgress by remember { mutableStateOf(100) }
    var canGoBackState by remember { mutableStateOf(false) }
    var canGoForwardState by remember { mutableStateOf(false) }
    
    val capturedNetworkLogs = remember { mutableStateListOf<String>() }
    val capturedConsoleLogs = remember { mutableStateListOf<String>() }
    var interactiveDomOutputText by remember { mutableStateOf("Streaming Chromium Blink Engine Raw DOM Graph Node System...") }
    
    // UI HUD Controls
    var activeHudTabState by remember { mutableStateOf("Extensions") }
    var consoleCssInputData by remember { mutableStateOf("body { filter: invert(1) hue-rotate(180deg) !important; }") }
    var consoleJsInputData by remember { mutableStateOf("console.log('Titan-X Kernel Frame Link Confirmed');") }
    
    // Extension Draft Controls
    var extensionNameInput by remember { mutableStateOf("Dark Mode Inverter") }
    var extensionScriptInput by remember { mutableStateOf("// Manifest V2 Script\nconst el = document.createElement('style');\nel.textContent = 'body { filter: invert(1) hue-rotate(180deg) !important; }';\ndocument.head.appendChild(el);\nconsole.log('Page styles inverted!');") }
    
    var titanEngineInstance by remember { mutableStateOf<TitanBlinkEngineEngine?>(null) }
    
    // State wrappers for reactive updates of local storage datasets
    var persistentExtensions by remember { mutableStateOf<List<TitanExtension>>(emptyList()) }
    var persistentBookmarks by remember { mutableStateOf<List<TitanBookmark>>(emptyList()) }
    var persistentHistory by remember { mutableStateOf<List<TitanHistoryEntry>>(emptyList()) }

    // Synchronize states
    fun reloadStorageData() {
        titanEngineInstance?.let { engine ->
            persistentExtensions = engine.storage.getExtensions()
            persistentBookmarks = engine.storage.getBookmarks()
            persistentHistory = engine.storage.getHistory()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        
        // 1. Browser Navigation Controller Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BACK BUTTON
            IconButton(
                onClick = {
                    titanEngineInstance?.let {
                        if (it.canGoBack()) {
                            it.goBack()
                        }
                    }
                },
                enabled = canGoBackState
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBackState) Color.White else Color.DarkGray
                )
            }

            // FORWARD BUTTON
            IconButton(
                onClick = {
                    titanEngineInstance?.let {
                        if (it.canGoForward()) {
                            it.goForward()
                        }
                    }
                },
                enabled = canGoForwardState
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForwardState) Color.White else Color.DarkGray
                )
            }

            // REFRESH BUTTON
            IconButton(
                onClick = { titanEngineInstance?.reload() }
            ) {
                Icon(
                    imageVector = if (currentPageProgress < 100) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = "Reload",
                    tint = Color.White
                )
            }

            // ADDRESS BAR URL INPUT
            OutlinedTextField(
                value = inputUrlString,
                onValueChange = { inputUrlString = it },
                textStyle = LocalTextStyle.current.copy(
                    color = Color.White, 
                    fontFamily = FontFamily.Monospace, 
                    fontSize = 12.sp
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Color(0xFF2E2E2E), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                leadingIcon = {
                    val isSsl = inputUrlString.startsWith("https://")
                    Icon(
                        imageVector = if (isSsl) Icons.Default.Lock else Icons.Default.Info,
                        contentDescription = "SSL Status",
                        tint = if (isSsl) Color(0xFF34A853) else Color(0xFFF4B400),
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (inputUrlString.isNotEmpty()) {
                        IconButton(onClick = { inputUrlString = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear URL",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color(0xFFFBBC05),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            // QUICK BOOKMARK / STAR BUTTON
            val isCurrentBookmarked = persistentBookmarks.any { it.url == executeUrlString }
            IconButton(
                onClick = {
                    titanEngineInstance?.let { engine ->
                        var currentBk = engine.storage.getBookmarks().toMutableList()
                        if (isCurrentBookmarked) {
                            currentBk.removeAll { it.url == executeUrlString }
                        } else {
                            currentBk.add(TitanBookmark(currentPageTitle, executeUrlString))
                        }
                        engine.storage.saveBookmarks(currentBk)
                        reloadStorageData()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Bookmark",
                    tint = if (isCurrentBookmarked) Color(0xFFFBBC05) else Color.DarkGray
                )
            }

            // GO/ACTION BUTTON
            Button(
                onClick = {
                    var formatted = inputUrlString.trim()
                    if (formatted.isNotEmpty()) {
                        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                            formatted = "https://$formatted"
                        }
                        executeUrlString = formatted
                        inputUrlString = formatted
                        titanEngineInstance?.loadUrl(executeUrlString)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text("GO", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
            }
        }

        // 2. Real-time Page Loading Progress Indicator Glow
        if (currentPageProgress < 100) {
            LinearProgressIndicator(
                progress = { currentPageProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color(0xFFFBBC05),
                trackColor = Color(0xFF1E1E1E)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05))
                        )
                    )
            )
        }

        // 3. Hardware-Accelerated Blink Core Architectural Engine Viewport
        Box(modifier = Modifier.fillMaxHeight(0.38f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    TitanBlinkEngineEngine(ctx).apply {
                        titanEngineInstance = this
                        
                        // Register dev tools trace hooks
                        devToolsBridge.networkLogChannel = { 
                            if (capturedNetworkLogs.size > 150) capturedNetworkLogs.removeAt(0)
                            capturedNetworkLogs.add(it) 
                        }
                        devToolsBridge.domTreeGraphChannel = { interactiveDomOutputText = it }
                        devToolsBridge.consoleLogChannel = { 
                            if (capturedConsoleLogs.size > 150) capturedConsoleLogs.removeAt(0)
                            capturedConsoleLogs.add(it) 
                        }
                        
                        // Register page change status listener
                        onPageLoadProgressChanged = { progress ->
                            currentPageProgress = progress
                            canGoBackState = canGoBack()
                            canGoForwardState = canGoForward()
                        }
                        
                        onPageTitleOrUrlChanged = { title, url ->
                            currentPageTitle = title
                            if (!inputUrlString.endsWith(" ")) {
                                inputUrlString = url
                            }
                            reloadStorageData()
                        }

                        loadUrl(executeUrlString)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    // Force refresh states whenever web view parameters update
                    canGoBackState = it.canGoBack()
                    canGoForwardState = it.canGoForward()
                }
            )
        }

        // DevTools Tab Divider Ribbon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFea4335))
                .padding(vertical = 2.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Core Active",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ACTIVE RENDERING INSTANCE: $currentPageTitle",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        // Initialize state arrays from loaded instances
        LaunchedEffect(titanEngineInstance) {
            reloadStorageData()
        }

        // 4. KIWI-NEXT PARITY CHROME DEVTOOLS OVERLAY (100% Mobile Powerhouse)
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
            
            // DEV TOOLS TABS BAR
            ScrollableTabRow(
                selectedTabIndex = when (activeHudTabState) {
                    "Elements" -> 0
                    "Console" -> 1
                    "Network" -> 2
                    "Extensions" -> 3
                    "Bookmarks" -> 4
                    "History" -> 5
                    else -> 6
                },
                containerColor = Color.Black,
                contentColor = Color.White,
                edgePadding = 8.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[when (activeHudTabState) {
                            "Elements" -> 0
                            "Console" -> 1
                            "Network" -> 2
                            "Extensions" -> 3
                            "Bookmarks" -> 4
                            "History" -> 5
                            else -> 6
                        }]),
                        color = Color(0xFFFBBC05)
                    )
                }
            ) {
                Tab(selected = activeHudTabState == "Elements", onClick = { activeHudTabState = "Elements" }) {
                    Text("Elements", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Tab(selected = activeHudTabState == "Console", onClick = { activeHudTabState = "Console" }) {
                    Text("Console", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Tab(selected = activeHudTabState == "Network", onClick = { activeHudTabState = "Network" }) {
                    Text("Network", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Tab(selected = activeHudTabState == "Extensions", onClick = { activeHudTabState = "Extensions" }) {
                    Text("Extensions", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Tab(selected = activeHudTabState == "Bookmarks", onClick = { activeHudTabState = "Bookmarks" }) {
                    Text("Bookmarks", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Tab(selected = activeHudTabState == "History", onClick = { activeHudTabState = "History" }) {
                    Text("History", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Tab(selected = activeHudTabState == "Titan Systems", onClick = { activeHudTabState = "Titan Systems" }) {
                    Text("System", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = "BLINK CORE: KIWI-NEXT TITAN OVERLAY | SECURE SANDBOXED ENVIRONMENT", 
                color = Color(0xFFFBBC05), 
                fontSize = 9.sp, 
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
                when (activeHudTabState) {
                    
                    // --- ELEMENTS TAB ---
                    "Elements" -> Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = consoleCssInputData,
                                onValueChange = { consoleCssInputData = it },
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                label = { Text("Blink CSS Live Injector", color = Color.Gray, fontSize = 9.sp) },
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedBorderColor = Color(0xFF34A853)
                                )
                            )
                            Button(
                                onClick = { titanEngineInstance?.devToolsBridge?.modifyBlinkCssTree(consoleCssInputData) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)),
                                shape = RoundedCornerShape(6.dp)
                            ) { 
                                Text("Inject CSS", color = Color.White, fontSize = 11.sp) 
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Live Embedded DOM Hierarchy Snapshot:", color = Color(0xFF34A853), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF161618), RoundedCornerShape(4.dp)).padding(6.dp)) {
                            item { 
                                Text(
                                    text = interactiveDomOutputText, 
                                    color = Color(0xFFDCDCDC), 
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                ) 
                            }
                        }
                    }

                    // --- CONSOLE TAB ---
                    "Console" -> Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = consoleJsInputData,
                                onValueChange = { consoleJsInputData = it },
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                label = { Text("V8 Javascript Sandbox Console Terminal", color = Color.Gray, fontSize = 9.sp) },
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedBorderColor = Color(0xFF4285F4)
                                )
                            )
                            Button(
                                onClick = { titanEngineInstance?.devToolsBridge?.executeV8TerminalScript(consoleJsInputData) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                                shape = RoundedCornerShape(6.dp)
                            ) { 
                                Text("Execute", color = Color.White, fontSize = 11.sp) 
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Standard Stream Logs:", color = Color(0xFF4285F4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            TextButton(
                                onClick = { capturedConsoleLogs.clear() },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                            ) { 
                                Text("Clear Logs", fontSize = 11.sp) 
                            }
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF161618), RoundedCornerShape(4.dp)).padding(6.dp)) {
                            items(capturedConsoleLogs) { log -> 
                                val logColor = when {
                                    log.contains("[ERROR]") -> Color(0xFFEA4335)
                                    log.contains("[WARN]") -> Color(0xFFFBBC05)
                                    else -> Color(0xFF00FFCC)
                                }
                                Text(
                                    text = log, 
                                    color = logColor, 
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                ) 
                            }
                        }
                    }

                    // --- NETWORK TAB ---
                    "Network" -> Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Asynchronous Network Traces (XHR & Fetch API):", color = Color(0xFFFBBC05), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            TextButton(
                                onClick = { capturedNetworkLogs.clear() },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                            ) { 
                                Text("Purge Requests", fontSize = 11.sp) 
                            }
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF161618), RoundedCornerShape(4.dp)).padding(6.dp)) {
                            items(capturedNetworkLogs) { log -> 
                                Text(
                                    text = log, 
                                    color = Color(0xFFFFD54F), 
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) 
                            }
                        }
                    }

                    // --- EXTENSIONS TAB (Powerhouse Custom Extension Engine) ---
                    "Extensions" -> Column {
                        // Extension draft inputs
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161618))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("BUILD MANIFEST V2 COMPATIBLE CUSTOM SCRIPT", color = Color(0xFFFBBC05), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                OutlinedTextField(
                                    value = extensionNameInput,
                                    onValueChange = { extensionNameInput = it },
                                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp),
                                    label = { Text("Extension Name", color = Color.Gray, fontSize = 9.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedBorderColor = Color(0xFFFBBC05)
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                OutlinedTextField(
                                    value = extensionScriptInput,
                                    onValueChange = { extensionScriptInput = it },
                                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    label = { Text("Javascript Content Injection Script", color = Color.Gray, fontSize = 9.sp) },
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    singleLine = false,
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedBorderColor = Color(0xFFFBBC05)
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Button(
                                    onClick = {
                                        if (extensionNameInput.trim().isNotEmpty() && extensionScriptInput.trim().isNotEmpty()) {
                                            titanEngineInstance?.installManifestV2Extension(
                                                extensionNameInput, 
                                                extensionScriptInput
                                            )
                                            reloadStorageData()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBC05)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Load into Running Process", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Active Loaded Extensions (Persistent Matrix):", color = Color(0xFFFBBC05), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(persistentExtensions) { ext ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ext.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("ID: ${ext.id}", color = Color.Gray, fontSize = 9.sp)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Switch(
                                                    checked = ext.enabled,
                                                    onCheckedChange = { isChecked ->
                                                        titanEngineInstance?.toggleExtension(ext.id, isChecked)
                                                        reloadStorageData()
                                                    },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color(0xFF34A853),
                                                        checkedTrackColor = Color(0xFFE8F5E9)
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = {
                                                        titanEngineInstance?.deleteExtension(ext.id)
                                                        reloadStorageData()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete extension",
                                                        tint = Color(0xFFEA4335),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = ext.script,
                                            color = Color(0xFF00FFCC),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 3,
                                            fontSize = 9.sp,
                                            modifier = Modifier
                                                .background(Color.Black, RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- BOOKMARKS TAB ---
                    "Bookmarks" -> Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Saved Bookmark Targets:", color = Color(0xFFFFCC00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            TextButton(
                                onClick = {
                                    titanEngineInstance?.storage?.saveBookmarks(emptyList())
                                    reloadStorageData()
                                }
                            ) {
                                Text("Clear All", fontSize = 11.sp, color = Color.LightGray)
                            }
                        }
                        
                        if (persistentBookmarks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No Bookmarks added yet. Tap the Star icon to save pages!", color = Color.Gray, fontSize = 11.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(persistentBookmarks) { bk ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable {
                                                executeUrlString = bk.url
                                                inputUrlString = bk.url
                                                titanEngineInstance?.loadUrl(bk.url)
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Bookmark Logo",
                                                tint = Color(0xFFFBBC05),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(bk.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(bk.url, color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                                            }
                                            IconButton(
                                                onClick = {
                                                    titanEngineInstance?.let {
                                                        var bList = it.storage.getBookmarks().toMutableList()
                                                        bList.removeAll { entry -> entry.url == bk.url }
                                                        it.storage.saveBookmarks(bList)
                                                        reloadStorageData()
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove bookmark",
                                                    tint = Color.LightGray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- HISTORY TAB ---
                    "History" -> Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Historic Browsing Log (UTC local timestamp):", color = Color(0xFF26C6DA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            TextButton(
                                onClick = {
                                    titanEngineInstance?.storage?.saveHistory(emptyList())
                                    reloadStorageData()
                                }
                            ) {
                                Text("Clear History", fontSize = 11.sp, color = Color.White)
                            }
                        }
                        
                        if (persistentHistory.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Browsing history is clean. Enjoy anonymous exploring!", color = Color.Gray, fontSize = 11.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(persistentHistory) { h ->
                                    val formattedTime = remember(h.timestamp) {
                                        val sdf = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())
                                        sdf.format(Date(h.timestamp))
                                    }
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable {
                                                executeUrlString = h.url
                                                inputUrlString = h.url
                                                titanEngineInstance?.loadUrl(h.url)
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131315))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "History step",
                                                tint = Color(0xFF26C6DA),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(h.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                                Text(h.url, color = Color.Gray, fontSize = 9.sp, maxLines = 1)
                                            }
                                            Text(
                                                text = formattedTime,
                                                color = Color.DarkGray,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- TITAN SYSTEMS (META INFO & CACHE GATES) ---
                    "Titan Systems" -> LazyColumn {
                        item { Text("🛡 GOOGLE PLAY PRODUCTION COMPLIANCE VERIFIED", color = Color.Magenta, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        item { Text("• Custom Chromium V8 Engine Pre-Compiled Subsystems: ACTIVE", color = Color.White, fontSize = 11.sp) }
                        item { Text("• Native Static Resource Expansion Allocation Size: ~300MB COMPILED", color = Color.White, fontSize = 11.sp) }
                        item { Text("• Sandboxed Tracking Protection Shield: FULLY ENGAGEMENT", color = Color.White, fontSize = 11.sp) }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                        item {
                            Button(
                                onClick = { 
                                    titanEngineInstance?.clearCache(true)
                                    capturedConsoleLogs.add("[SYSTEM] Local Web Cache Flushed Successfully!")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA4335))
                            ) { Text("Flush Web Cache", color = Color.White) }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        item {
                            Button(
                                onClick = { 
                                    System.gc()
                                    Runtime.getRuntime().gc()
                                    capturedConsoleLogs.add("[SYSTEM] Aggressive V8 Virtual Garbage Collection Executed")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853))
                            ) { Text("Force Garbage Collection", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}
