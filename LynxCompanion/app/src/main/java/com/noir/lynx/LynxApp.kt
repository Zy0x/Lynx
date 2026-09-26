package com.noir.lynx

import android.app.Application
import com.topjohnwu.superuser.Shell

/**
 * Lynx Companion Application class.
 *
 * Initializes LibSU root shell at application startup for universal root access
 * across Magisk, KernelSU, and APatch environments.
 */
class LynxApp : Application() {

    companion object {
        lateinit var instance: LynxApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize TopJohnWu LibSU with stderr redirect and 10s timeout.
        // This must run before any Shell.cmd() calls.
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}
