package com.noir.lynx.feature.hud.addins

import android.content.Context
import com.noir.lynx.feature.hud.data.HudCapability
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal fun gameQosRule(binary: String, operation: String, uid: Int, token: String): String {
    require(binary in listOf("iptables", "ip6tables") && operation in listOf("-A", "-C", "-D"))
    require(uid >= 10000 && token.matches(Regex("[0-9a-f]{32}")))
    return "$binary -w 2 -t mangle $operation OUTPUT -m owner --uid-owner $uid -m comment --comment lynx-hud-$token -j DSCP --set-dscp 34"
}

data class HudQosState(val capability: HudCapability = HudCapability.CHECKING, val enabled: Boolean = false, val busy: Boolean = false)

/** Per-app AF41 marking, not bandwidth bonding. Only uniquely owned rules are removed. */
class HudGameQos(private val context: Context) {
    private val journal = context.getSharedPreferences("hud_game_qos", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(HudQosState())
    val state = mutable.asStateFlow()
    private var families = emptyList<String>()

    @Synchronized fun probe() {
        if (!runCatching { Shell.getShell().isRoot }.getOrDefault(false)) {
            mutable.value = HudQosState(HudCapability.NEEDS_ROOT); return
        }
        families = listOf("iptables", "ip6tables").filter { binary ->
            val help = Shell.cmd("$binary -j DSCP -h 2>/dev/null").exec()
            help.isSuccess && help.out.any { "--set-dscp" in it }
        }
        mutable.value = mutable.value.copy(capability = if (families.isEmpty()) HudCapability.UNSUPPORTED else HudCapability.READY)
    }

    @Synchronized fun set(enabled: Boolean, pkg: String) {
        mutable.value = mutable.value.copy(busy = true)
        try {
            if (!restore()) return
            if (!enabled || !validMagicPackage(pkg) || families.isEmpty()) return
            val actual = Shell.cmd("dumpsys activity activities | sed -nE '/Display #0 /,/Display #[1-9][0-9]* /{/topResumedActivity|mResumedActivity/{s/.* u[0-9][0-9]* //;s,/.*,,;p;q;}}'").exec().out.firstOrNull()?.trim()
            if (actual != pkg) return
            val uid = runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull() ?: return
            if (uid < 10000) return
            val token = UUID.randomUUID().toString().replace("-", "")
            if (!journal.edit().putInt("uid", uid).putString("token", token).putStringSet("families", families.toSet()).commit()) return
            val applied = families.all { binary ->
                Shell.cmd(gameQosRule(binary, "-A", uid, token)).exec().isSuccess &&
                    Shell.cmd(gameQosRule(binary, "-C", uid, token)).exec().isSuccess
            }
            if (!applied) { restore(); return }
            mutable.value = mutable.value.copy(enabled = true)
        } finally { mutable.value = mutable.value.copy(busy = false) }
    }

    @Synchronized fun restore(): Boolean {
        val token = journal.getString("token", null) ?: return true
        val uid = journal.getInt("uid", -1)
        if (uid < 10000 || !token.matches(Regex("[0-9a-f]{32}"))) return false
        val families = journal.getStringSet("families", emptySet()).orEmpty()
        if (families.any { it !in listOf("iptables", "ip6tables") }) return false
        val restored = families.map { binary ->
            val check = Shell.cmd(gameQosRule(binary, "-C", uid, token)).exec()
            when (check.code) {
                1 -> true
                0 -> Shell.cmd(gameQosRule(binary, "-D", uid, token)).exec().isSuccess &&
                    Shell.cmd(gameQosRule(binary, "-C", uid, token)).exec().code == 1
                else -> false
            }
        }.all { it }
        if (restored) { journal.edit().clear().commit(); mutable.value = mutable.value.copy(enabled = false) }
        return restored
    }
}
