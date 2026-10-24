package com.noir.lynx.freeform

import com.noir.lynx.freeform.root.RootTaskPolicy
import org.junit.Assert.*
import org.junit.Test

class RootTaskPolicyTest {
    @Test fun reusesMostRecentMainDisplayTaskForSameUserOnly() {
        val tasks = listOf(
            RootTaskPolicy.Task(8, 10, 0, "browser"),
            RootTaskPolicy.Task(7, 0, 4, "browser"),
            RootTaskPolicy.Task(6, 0, 0, "other"),
            RootTaskPolicy.Task(5, 0, 0, "browser"),
            RootTaskPolicy.Task(3, 0, 0, "browser"))
        assertEquals(5, RootTaskPolicy.reusable(tasks, "browser", 0, setOf(4))?.id)
        assertNull(RootTaskPolicy.reusable(tasks, "absent", 0, setOf(4)))
        assertNull(RootTaskPolicy.reusable(tasks, "browser", 11, emptySet()))
    }
}
