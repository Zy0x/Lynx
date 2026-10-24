package com.noir.lynx.freeform

import com.noir.lynx.freeform.root.RootFrameworkPolicy
import org.junit.Assert.*
import org.junit.Test

class RootFrameworkPolicyTest {
    @Test fun knownTaskInterfacesAndVendorOverloadsAreSelectedByShape() {
        assertEquals(0, RootFrameworkPolicy.taskSignature(listOf(listOf("int"))))
        assertEquals(0, RootFrameworkPolicy.taskSignature(listOf(listOf("int", "boolean", "boolean"))))
        assertEquals(1, RootFrameworkPolicy.taskSignature(listOf(listOf("String"), listOf("int", "boolean", "boolean", "int"))))
        assertNull(RootFrameworkPolicy.taskSignature(listOf(listOf("int", "String", "boolean"))))
    }

    @Test fun displayFlagsDoNotUnlockDisplaysOrEnableRomDesktopDecorations() {
        for (sdk in 29..37) {
            val flags = RootFrameworkPolicy.displayFlags(sdk)
            assertTrue(flags and (1 shl 8) != 0)
            assertEquals(0, flags and (1 shl 12))
            assertEquals(0, flags and (1 shl 9))
            assertEquals(sdk >= 33, flags and (1 shl 11) != 0)
            assertEquals(sdk >= 34, flags and (1 shl 14) != 0)
        }
    }
}
