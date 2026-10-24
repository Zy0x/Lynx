package com.noir.lynx.freeform.root

/** Task identity is user-scoped; never borrow a task from another profile or owned window. */
internal object RootTaskPolicy {
    data class Task(val id: Int, val user: Int, val display: Int, val packageName: String?)

    fun reusable(tasks: List<Task>, packageName: String, user: Int, ownedDisplays: Set<Int>): Task? =
        tasks.firstOrNull { it.id >= 0 && it.user == user && it.display == 0 &&
            it.display !in ownedDisplays && it.packageName == packageName }
}
