package com.silentnet

import com.chaquo.python.android.PyApplication
import com.silentnet.app.AppGraph

class SilentNetApplication : PyApplication() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        if (!com.chaquo.python.Python.isStarted()) {
            com.chaquo.python.Python.start(com.chaquo.python.android.AndroidPlatform(this))
        }
        graph = AppGraph(this)
    }
}
