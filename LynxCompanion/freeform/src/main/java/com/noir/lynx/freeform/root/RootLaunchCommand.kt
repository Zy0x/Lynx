package com.noir.lynx.freeform.root

/** Android's Intent CLI accepts -f; there is no --activity-new-task option on Android 10. */
internal object RootLaunchCommand {
    fun create(component: String, userId: Int, displayId: Int): List<String> {
        require(userId >= 0 && displayId > 0)
        return listOf("/system/bin/am", "start", "--user", userId.toString(),
            "--display", displayId.toString(), "--windowingMode", "1",
            "-f", "0x10200000", // NEW_TASK | RESET_TASK_IF_NEEDED, like the system launcher.
            "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", component)
    }
}
