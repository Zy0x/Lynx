package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sound-control driver probing and gain application. */
object LynxSoundControlRepository {
    suspend fun readSoundControl(): SoundControlInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                dir=""
                for d in /sys/kernel/sound_control /sys/devices/virtual/misc/soundcontrol /sys/class/misc/soundcontrol; do
                    if [ -d "${'$'}d" ]; then
                        dir="${'$'}d"
                        break
                    fi
                done
                if [ -n "${'$'}dir" ]; then
                    echo "DIR:${'$'}dir"
                else
                    echo "unsupported"
                fi
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val out = res.out.firstOrNull()?.trim() ?: "unsupported"

            if (out.startsWith("DIR:")) {
                val dir = out.substringAfter("DIR:").trim()
                val hp = Shell.cmd("cat $dir/headphone_gain 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0 0"
                val hpParts = hp.split(Regex("\\s+"))
                val hpL = hpParts.getOrNull(0)?.toIntOrNull() ?: 0
                val hpR = hpParts.getOrNull(1)?.toIntOrNull() ?: hpL
                val spk = Shell.cmd("cat $dir/speaker_gain 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 0
                val mic = Shell.cmd("cat $dir/mic_gain 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 0
                val hpMode = Shell.cmd("cat $dir/highperf_mode 2>/dev/null").exec().out.firstOrNull()?.trim() == "1"

                return@withContext SoundControlInfo(
                    isSupported = true,
                    unsupportedReason = "",
                    headphoneGainL = hpL,
                    headphoneGainR = hpR,
                    speakerGain = spk,
                    micGain = mic,
                    highPerfMode = hpMode
                )
            }

            SoundControlInfo(
                isSupported = false,
                unsupportedReason = "Driver Sound Control (Faux/Franco sound) tidak ditemukan di kernel ini"
            )
        } catch (e: Exception) {
            SoundControlInfo(
                isSupported = false,
                unsupportedReason = "Pemeriksaan driver sound gagal: ${e.message}"
            )
        }
    }

    suspend fun setSoundGain(hpL: Int, hpR: Int, spk: Int, mic: Int, highPerf: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                dir=""
                for d in /sys/kernel/sound_control /sys/devices/virtual/misc/soundcontrol /sys/class/misc/soundcontrol; do
                    if [ -d "${'$'}d" ]; then
                        dir="${'$'}d"
                        break
                    fi
                done
                if [ -n "${'$'}dir" ]; then
                    echo "$hpL $hpR" > "${'$'}dir/headphone_gain" 2>/dev/null
                    echo "$spk" > "${'$'}dir/speaker_gain" 2>/dev/null
                    echo "$mic" > "${'$'}dir/mic_gain" 2>/dev/null
                    [ -f "${'$'}dir/highperf_mode" ] && echo "${if (highPerf) 1 else 0}" > "${'$'}dir/highperf_mode" 2>/dev/null
                    echo 1
                else
                    echo 0
                fi
            """.trimIndent()
            val res = Shell.cmd(script).exec()
            res.out.firstOrNull()?.trim() == "1"
        } catch (e: Exception) {
            false
        }
    }


}
