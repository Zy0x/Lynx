package com.noir.lynx.feature.hud.data

/** Null means an unmigrated installation; an explicitly empty dock stays empty. */
internal object HudDockPolicy {
    private val defaults = listOf("com.android.chrome", "com.whatsapp", "org.telegram.messenger", "com.discord",
        "com.google.android.youtube", "com.google.android.calculator", "com.transsion.calculator", "com.android.calculator2")

    fun packages(saved: List<String>?, installed: Set<String>): List<String> =
        (saved ?: defaults).distinct().filter { it in installed }
}
