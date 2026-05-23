package com.example.devtools

import java.util.concurrent.ConcurrentHashMap

class NetworkPacketDissector {
    private val processedStreamMap = ConcurrentHashMap<String, String>()

    fun registerActiveStream(requestId: String, metadata: String) {
        processedStreamMap[requestId] = metadata
    }

    fun purgeActiveStreamLogs() {
        processedStreamMap.clear()
    }
}
