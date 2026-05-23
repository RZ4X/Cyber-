package com.example.nativecore

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class TitanExtension(
    val id: String,
    val name: String,
    val script: String,
    val enabled: Boolean = true
)

data class TitanBookmark(
    val title: String,
    val url: String
)

data class TitanHistoryEntry(
    val title: String,
    val url: String,
    val timestamp: Long
)

class TitanBrowserStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("titan_browser_prefs", Context.MODE_PRIVATE)

    fun getExtensions(): List<TitanExtension> {
        val jsonStr = prefs.getString("extensions_list", null) ?: return getDefaultExtensions()
        val list = mutableListOf<TitanExtension>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TitanExtension(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        script = obj.getString("script"),
                        enabled = obj.optBoolean("enabled", true)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveExtensions(extensions: List<TitanExtension>) {
        val array = JSONArray()
        for (ext in extensions) {
            val obj = JSONObject().apply {
                put("id", ext.id)
                put("name", ext.name)
                put("script", ext.script)
                put("enabled", ext.enabled)
            }
            array.put(obj)
        }
        prefs.edit().putString("extensions_list", array.toString()).apply()
    }

    fun getBookmarks(): List<TitanBookmark> {
        val jsonStr = prefs.getString("bookmarks_list", null) ?: return getDefaultBookmarks()
        val list = mutableListOf<TitanBookmark>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TitanBookmark(
                        title = obj.getString("title"),
                        url = obj.getString("url")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveBookmarks(bookmarks: List<TitanBookmark>) {
        val array = JSONArray()
        for (b in bookmarks) {
            val obj = JSONObject().apply {
                put("title", b.title)
                put("url", b.url)
            }
            array.put(obj)
        }
        prefs.edit().putString("bookmarks_list", array.toString()).apply()
    }

    fun getHistory(): List<TitanHistoryEntry> {
        val jsonStr = prefs.getString("history_list", null) ?: return emptyList()
        val list = mutableListOf<TitanHistoryEntry>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TitanHistoryEntry(
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addHistoryEntry(entry: TitanHistoryEntry) {
        val current = getHistory().toMutableList()
        // Remove duplicate of same URL to keep it clean
        current.removeAll { it.url == entry.url }
        current.add(0, entry)
        if (current.size > 200) { // Bound history items
            current.removeAt(current.size - 1)
        }
        saveHistory(current)
    }

    fun saveHistory(history: List<TitanHistoryEntry>) {
        val array = JSONArray()
        for (h in history) {
            val obj = JSONObject().apply {
                put("title", h.title)
                put("url", h.url)
                put("timestamp", h.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("history_list", array.toString()).apply()
    }

    private fun getDefaultExtensions(): List<TitanExtension> {
        return listOf(
            TitanExtension(
                id = "dark_mode_v2",
                name = "Force Universal Dark Mode",
                script = """
                    const style = document.createElement('style');
                    style.textContent = '* { background-color: #121212 !important; color: #e0e0e0 !important; border-color: #2c2c2c !important; } a { color: #8ab4f8 !important; }';
                    document.head.appendChild(style);
                    console.log('Premium Dark Mode Extension Engine Applied!');
                """.trimIndent(),
                enabled = false
            ),
            TitanExtension(
                id = "adblock_lite",
                name = "Quantum AdBlock Shield Lite",
                script = """
                    const badSelectors = [
                        'div[class*="ad-"]', 'div[class*="ads-"]', 'div[id*="ad-"]', 
                        'iframe[src*="doubleclick"]', 'img[src*="adsystem"]',
                        '.sponsor-box', '.adsbygoogle'
                    ];
                    function purgeAds() {
                        badSelectors.forEach(sel => {
                            document.querySelectorAll(sel).forEach(el => el.remove());
                        });
                    }
                    const adPurgeObserver = new MutationObserver(purgeAds);
                    adPurgeObserver.observe(document.documentElement, { childList: true, subtree: true });
                    purgeAds();
                    console.log('Quantum AdBlock Shield fully armed!');
                """.trimIndent(),
                enabled = true
            ),
            TitanExtension(
                id = "dom_glow",
                name = "UI Crimson Retro Border Glow",
                script = """
                    document.body.style.border = '4px double #ea4335';
                    console.log('Crimson styling injector successfully mapped!');
                """.trimIndent(),
                enabled = false
            )
        )
    }

    private fun getDefaultBookmarks(): List<TitanBookmark> {
        return listOf(
            TitanBookmark("Wikipedia", "https://wikipedia.org"),
            TitanBookmark("Google Search", "https://google.com"),
            TitanBookmark("GitHub", "https://github.com"),
            TitanBookmark("Kiwi Browser Next", "https://github.com/kiwibrowser/src.next")
        )
    }
}
