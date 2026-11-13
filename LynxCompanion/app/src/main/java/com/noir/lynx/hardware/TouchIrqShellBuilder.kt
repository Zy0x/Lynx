package com.noir.lynx.hardware

/**
 * Dynamic shell builder for universal CPU cluster topology discovery,
 * input-subsystem SPI/I2C controller resolution, multi-OEM touch IRQ pinning,
 * and matching kernel thread CPU affinity across all Android SoCs.
 */
object TouchIrqShellBuilder {

    /**
     * POSIX/toybox sh helpers to probe asymmetric big/little CPU topology
     * and discover active touchscreen IRQs without hardcoded device paths or core ranges.
     */
    fun buildDiscoveryFunctions(): String = """
        cpus_to_csv() {
            echo "${'$'}1" | awk '{for(i=1;i<=NF;i++) printf "%s%s", ${'$'}i, (i<NF?",":"")}'
        }

        cpus_to_hex() {
            mask=0
            for c in ${'$'}1; do
                mask=${'$'}((mask | (1 << c)))
            done
            printf "%x" "${'$'}mask"
        }

        detect_cpu_topology() {
            ALL_CPUS=""
            for cdir in /sys/devices/system/cpu/cpu[0-9]*; do
                [ -d "${'$'}cdir" ] || continue
                ALL_CPUS="${'$'}ALL_CPUS ${'$'}{cdir##*cpu}"
            done
            ALL_CPUS=${'$'}(echo "${'$'}ALL_CPUS" | tr ' ' '\n' | grep -E '^[0-9]+${'$'}' | sort -n | tr '\n' ' ')
            MIN_FREQ=""
            MAX_FREQ=0
            for pdir in /sys/devices/system/cpu/cpufreq/policy*; do
                [ -f "${'$'}pdir/cpuinfo_max_freq" ] || continue
                f=${'$'}(cat "${'$'}pdir/cpuinfo_max_freq" 2>/dev/null | tr -dc '0-9')
                [ -z "${'$'}f" ] && continue
                [ -z "${'$'}MIN_FREQ" ] && MIN_FREQ="${'$'}f"
                [ "${'$'}f" -lt "${'$'}MIN_FREQ" ] && MIN_FREQ="${'$'}f"
                [ "${'$'}f" -gt "${'$'}MAX_FREQ" ] && MAX_FREQ="${'$'}f"
            done
            BIG_CPUS=""
            if [ -n "${'$'}MIN_FREQ" ] && [ "${'$'}MAX_FREQ" -gt "${'$'}MIN_FREQ" ]; then
                for pdir in /sys/devices/system/cpu/cpufreq/policy*; do
                    [ -f "${'$'}pdir/cpuinfo_max_freq" ] || continue
                    f=${'$'}(cat "${'$'}pdir/cpuinfo_max_freq" 2>/dev/null | tr -dc '0-9')
                    [ -n "${'$'}f" ] && [ "${'$'}f" -gt "${'$'}MIN_FREQ" ] && BIG_CPUS="${'$'}BIG_CPUS ${'$'}(cat "${'$'}pdir/related_cpus" 2>/dev/null)"
                done
            fi
            if [ -z "${'$'}BIG_CPUS" ]; then
                MIN_CAP=""
                MAX_CAP=0
                for cdir in /sys/devices/system/cpu/cpu[0-9]*; do
                    [ -f "${'$'}cdir/cpu_capacity" ] || continue
                    cap=${'$'}(cat "${'$'}cdir/cpu_capacity" 2>/dev/null | tr -dc '0-9')
                    [ -z "${'$'}cap" ] && continue
                    [ -z "${'$'}MIN_CAP" ] && MIN_CAP="${'$'}cap"
                    [ "${'$'}cap" -lt "${'$'}MIN_CAP" ] && MIN_CAP="${'$'}cap"
                    [ "${'$'}cap" -gt "${'$'}MAX_CAP" ] && MAX_CAP="${'$'}cap"
                done
                if [ -n "${'$'}MIN_CAP" ] && [ "${'$'}MAX_CAP" -gt "${'$'}MIN_CAP" ]; then
                    for cdir in /sys/devices/system/cpu/cpu[0-9]*; do
                        [ -f "${'$'}cdir/cpu_capacity" ] || continue
                        cap=${'$'}(cat "${'$'}cdir/cpu_capacity" 2>/dev/null | tr -dc '0-9')
                        [ -n "${'$'}cap" ] && [ "${'$'}cap" -gt "${'$'}MIN_CAP" ] && BIG_CPUS="${'$'}BIG_CPUS ${'$'}{cdir##*cpu}"
                    done
                fi
            fi
            BIG_CPUS=${'$'}(echo "${'$'}BIG_CPUS" | tr ' ' '\n' | grep -E '^[0-9]+${'$'}' | sort -nu | tr '\n' ' ')
            IS_ASYM=0
            if [ -n "${'$'}BIG_CPUS" ] && [ "${'$'}BIG_CPUS" != "${'$'}ALL_CPUS" ]; then
                IS_ASYM=1
            else
                BIG_CPUS="${'$'}ALL_CPUS"
            fi
        }

        find_touch_irqs() {
            BUS_TOKENS=""
            for inp in /sys/class/input/input*; do
                [ -d "${'$'}inp" ] || continue
                iname=${'$'}(cat "${'$'}inp/name" 2>/dev/null | tr '[:upper:]' '[:lower:]')
                case "${'$'}iname" in
                    *fingerprint*|*silead*|*goodix_fp*|*gf_spi*|*fpc*|*uinput*) continue ;;
                esac
                is_direct=0
                props=${'$'}(cat "${'$'}inp/properties" 2>/dev/null | awk '{print ${'$'}NF}' | tr -dc '0-9a-fA-F')
                if [ -n "${'$'}props" ]; then
                    pval=${'$'}((0x${'$'}props & 2)) 2>/dev/null
                    [ "${'$'}pval" = "2" ] && is_direct=1
                fi
                case "${'$'}iname" in
                    *touch*|*tpd*|*fts*|*goodix*|*novatek*|*nvt*|*synaptics*|*himax*|*ilitek*|*sec_ts*|*sec_tp*) is_direct=1 ;;
                esac
                [ "${'$'}is_direct" -eq 1 ] || continue
                dpath=${'$'}(readlink -f "${'$'}inp/device" 2>/dev/null)
                [ -z "${'$'}dpath" ] && continue
                case "${'$'}dpath" in */virtual/*) continue ;; esac
                tok=${'$'}(echo "${'$'}dpath" | tr '/' '\n' | grep -E '\.(spi|i2c)[0-9]*${'$'}|^spi[0-9]+${'$'}|^i2c-[0-9]+${'$'}' | tail -n 1)
                [ -n "${'$'}tok" ] && BUS_TOKENS="${'$'}BUS_TOKENS ${'$'}tok"
            done
            TOUCH_IRQS=""
            while IFS= read -r line; do
                lower=${'$'}(echo "${'$'}line" | tr '[:upper:]' '[:lower:]')
                case "${'$'}lower" in
                    *fingerprint*|*silead*|*goodix_fp*|*gf_spi*|*fpc*|*egis*|*et7*|*qbt*|*focal_fp*|*cdfinger*|*chipone_fp*|*fpsensor*|*sec_fprint*)
                        continue ;;
                esac
                matched=0
                for tok in ${'$'}BUS_TOKENS; do
                    case "${'$'}lower" in *"${'$'}tok"*) matched=1; break ;; esac
                done
                if [ "${'$'}matched" -eq 0 ]; then
                    case "${'$'}lower" in
                        *touch*|*ts_*|*_ts*|*tpd*|*novatek*|*nvt*|*goodix*|*gt9*|*gt1*|*focaltech*|*fts*|*synaptics*|*rmi4*|*s390*|*himax*|*hx83*|*ilitek*|*ili9*|*atmel*|*mxt*|*sec_ts*|*stm_fts*|*sec_tp*|*oplus_tp*|*xiaomi_tp*|*chipone*|*icn*|*galaxycore*|*gcore*|*elan*|*ektf*|*sitronix*|*st16*|*jadard*)
                            matched=1 ;;
                    esac
                fi
                if [ "${'$'}matched" -eq 1 ]; then
                    irq=${'$'}(echo "${'$'}line" | awk -F: '{print ${'$'}1}' | tr -dc '0-9')
                    [ -n "${'$'}irq" ] && TOUCH_IRQS="${'$'}TOUCH_IRQS ${'$'}irq"
                fi
            done < /proc/interrupts
            TOUCH_IRQS=${'$'}(echo "${'$'}TOUCH_IRQS" | tr ' ' '\n' | grep -E '^[0-9]+${'$'}' | sort -nu | tr '\n' ' ')
        }
    """.trimIndent()

    fun buildProbeSnippet(): String = """
        ${buildDiscoveryFunctions()}
        detect_cpu_topology
        find_touch_irqs
        irq_sup=0
        irq_pin=0
        if [ "${'$'}IS_ASYM" -eq 1 ] && [ -n "${'$'}TOUCH_IRQS" ]; then
            for irq in ${'$'}TOUCH_IRQS; do
                if [ -e "/proc/irq/${'$'}irq/smp_affinity_list" ] || [ -e "/proc/irq/${'$'}irq/smp_affinity" ]; then
                    irq_sup=1
                    cur_aff=${'$'}(cat "/proc/irq/${'$'}irq/smp_affinity_list" 2>/dev/null | tr -d ' \r\n')
                    if [ -n "${'$'}cur_aff" ]; then
                        in_big=${'$'}(awk -v aff="${'$'}cur_aff" -v big="${'$'}BIG_CPUS" 'BEGIN {
                            split(big, b, " "); for (i in b) if (b[i] != "") allow[b[i]] = 1;
                            n = split(aff, parts, ","); count = 0; ok = 1;
                            for (i = 1; i <= n; i++) {
                                if (index(parts[i], "-") > 0) {
                                    split(parts[i], r, "-");
                                    for (c = r[1]+0; c <= r[2]+0; c++) { count++; if (!(c in allow)) ok = 0; }
                                } else if (parts[i] != "") {
                                    count++; if (!((parts[i]+0) in allow)) ok = 0;
                                }
                            }
                            print (count > 0 && ok == 1) ? 1 : 0;
                        }')
                        [ "${'$'}in_big" = "1" ] && irq_pin=1
                    fi
                    break
                fi
            done
        fi
        resamp_sup=0
        for lib in /system/lib64/libinput.so /system/lib/libinput.so /system/lib64/libinputflinger.so /system/lib/libinputflinger.so /vendor/lib64/libinput.so; do
            if [ -r "${'$'}lib" ] && grep -Fq "ro.input.resampling" "${'$'}lib" 2>/dev/null; then
                resamp_sup=1
                break
            fi
        done
    """.trimIndent()

    fun buildApplyAffinitySnippet(pinBigCores: Boolean): String {
        val targetRef = if (pinBigCores) "${'$'}BIG_CPUS" else "${'$'}ALL_CPUS"
        return """
        ${buildDiscoveryFunctions()}
        detect_cpu_topology
        find_touch_irqs
        if [ "${'$'}IS_ASYM" -eq 1 ] && [ -n "${'$'}TOUCH_IRQS" ]; then
            TARGET_CPUS="$targetRef"
            TARGET_CSV=${'$'}(cpus_to_csv "${'$'}TARGET_CPUS")
            TARGET_HEX=${'$'}(cpus_to_hex "${'$'}TARGET_CPUS")
            for irq in ${'$'}TOUCH_IRQS; do
                echo "${'$'}TARGET_CSV" > "/proc/irq/${'$'}irq/smp_affinity_list" 2>/dev/null || echo "${'$'}TARGET_HEX" > "/proc/irq/${'$'}irq/smp_affinity" 2>/dev/null
            done
            for cfile in /proc/[0-9]*/comm; do
                [ -r "${'$'}cfile" ] || continue
                cname=${'$'}(cat "${'$'}cfile" 2>/dev/null)
                clower=${'$'}(echo "${'$'}cname" | tr '[:upper:]' '[:lower:]')
                case "${'$'}clower" in
                    *fingerprint*|*silead*|*goodix_fp*|*gf_spi*|*fpc*) continue ;;
                esac
                pin_kthread=0
                for irq in ${'$'}TOUCH_IRQS; do
                    case "${'$'}clower" in irq/${'$'}irq-*) pin_kthread=1; break ;; esac
                done
                if [ "${'$'}pin_kthread" -eq 0 ]; then
                    case "${'$'}clower" in
                        *nvt_work*|*goodix*|*fts*|*synaptics*|*tpd*|*touch*|*himax*|*ilitek*) pin_kthread=1 ;;
                    esac
                fi
                if [ "${'$'}pin_kthread" -eq 1 ]; then
                    kpid=${'$'}{cfile#/proc/}; kpid=${'$'}{kpid%/comm}
                    taskset -p "${'$'}TARGET_HEX" "${'$'}kpid" >/dev/null 2>&1
                fi
            done
        fi
    """.trimIndent()
    }
}
