package com.noir.lynx.freeform.root

/** Android's Intent CLI accepts -f; there is no --activity-new-task option on Android 10. */
internal object RootLaunchCommand {
    fun create(component: String, userId: Int, displayId: Int): List<String> {
        require(userId >= 0 && displayId > 0)
        return listOf("/system/bin/am", "start", "--user", userId.toString(),
            "--display", displayId.toString(), "--windowingMode", "1",
            "-f", "0x18000000", // FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK
            "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", component)
    }
}
