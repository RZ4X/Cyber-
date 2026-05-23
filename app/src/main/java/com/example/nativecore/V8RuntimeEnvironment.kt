package com.example.nativecore

import android.os.Handler
import android.os.Looper

class V8RuntimeEnvironment {
    fun triggerAggressiveMemoryCleanup() {
        // Force asynchronous high-frequency runtime garbage collection to simulate low-level heap purging
        Handler(Looper.getMainLooper()).postDelayed({
            System.gc()
            Runtime.getRuntime().gc()
        }, 300)
    }
}
