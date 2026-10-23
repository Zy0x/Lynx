package com.noir.lynx.feature.floating

/** Readback evidence only. An accepted broadcast is not proof of a floating task. */
internal object SystemFreeformEvidence {
    private data class Task(val id: Int, val display: Int, val freeform: Boolean)
    private fun tasks(raw: String, pkg: String): Set<Task> {
        var display = -1
        val found = mutableSetOf<Task>()
        val packagePattern = Regex("(?<![A-Za-z0-9_.])${Regex.escape(pkg)}(?![A-Za-z0-9_.])")
        for (line in raw.lineSequence()) {
            Regex("Display #(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { display = it }
            if (!line.contains("Task{") || !packagePattern.containsMatchIn(line)) continue
            val id = Regex("#(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            found.add(Task(id, display, line.contains("mode=freeform")))
        }
        return found
    }

    fun confirmed(before: String, after: String, pkg: String, lmo: Boolean): Boolean {
        val current = tasks(after, pkg)
        if (current.any { it.display >= 0 && it.freeform }) return true
        if (!lmo) return false
        return current.filter { it.display > 0 }.any { it !in tasks(before, pkg) }
    }
}
