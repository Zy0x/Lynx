package com.noir.lynx.freeform

import com.noir.lynx.freeform.root.RootLaunchCommand
import org.junit.Assert.*
import org.junit.Test

class RootLaunchCommandTest {
    @Test fun launchUsesSupportedIntentFlagsAndOwnDisplay() {
        val command = RootLaunchCommand.create("com.example.chat/.Main", 10, 7)
        fun value(key: String) = command[command.indexOf(key) + 1]
        assertEquals("7", value("--display"))
        assertEquals("10", value("--user"))
        assertEquals("1", value("--windowingMode"))
        val flags = java.lang.Long.decode(value("-f")).toInt()
        assertTrue(flags and 0x10000000 != 0)
        assertTrue(flags and 0x08000000 != 0)
        assertEquals("android.intent.action.MAIN", value("-a"))
        assertFalse(command.contains("--activity-new-task"))
    }
    @Test fun rejectLaunchingIntoMainDisplay() {
        assertTrue(runCatching { RootLaunchCommand.create("com.example/.Main", 0, 0) }.isFailure)
    }
}
