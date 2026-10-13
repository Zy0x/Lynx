package com.noir.lynx.data

internal object LynxTunableOptionParser {
    fun parseOptionsFromHelp(help: String): List<TunableOption> {
        val result = mutableListOf<TunableOption>()
        try {
            val pattern = Regex("(\\d+)\\s*[:\\-=]\\s*([a-zA-Z0-9_\\- /]+)")
            val matches = pattern.findAll(help)
            for (m in matches) {
                val v = m.groupValues[1].trim()
                val l = m.groupValues[2].trim().trimEnd(',', ';')
                if (v.isNotBlank() && l.isNotBlank()) {
                    result.add(TunableOption(value = v, label = "$v - $l"))
                }
            }
        } catch (_: Exception) {}
        return result
    }

}
