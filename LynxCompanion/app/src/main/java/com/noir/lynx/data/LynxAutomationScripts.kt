package com.noir.lynx.data

import com.noir.lynx.LynxApp

internal object LynxAutomationScripts {
    fun read(name: String): String = LynxApp.instance.assets.open("automation/$name").bufferedReader().use { it.readText() }.trimIndent()
}