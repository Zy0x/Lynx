package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Low-level Android system toggles and wireless ADB controls. */
object LynxSystemControlRepository {
    suspend fun readSelinuxMode(): String = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("getenforce 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "Enforcing"
        } catch (e: Exception) { "Enforcing" }
    }

    suspend fun setSelinuxMode(enforcing: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val code = if (enforcing) "1" else "0"
            Shell.cmd("setenforce $code 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun readPrintkSilent(): Boolean = withContext(Dispatchers.IO) {
        try {
            val out = Shell.cmd("cat /proc/sys/kernel/printk 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            out.startsWith("0")
        } catch (e: Exception) { true }
    }

    suspend fun setPrintkSilent(silent: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (silent) "0 0 0 0" else "7 4 1 7"
            val cmd = "echo '$v' > /proc/sys/kernel/printk 2>/dev/null && [ \"$(cat /proc/sys/kernel/printk 2>/dev/null | awk '{print \$1}')\" = \"${if (silent) "0" else "7"}\" ] && echo ok"
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Subsystem Hardware Apply & Active Verification
    // ----------------------------------------------------------------


    suspend fun applyTouchboost(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        LynxCpuSchedulerControlRepository.setUniversalTouchBoost(enabled, LynxRepository.appContext)
    }

    suspend fun applyWifiPingStabilizer(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled) {
                """
                # Universal Android Wi-Fi Low-Latency HAL & Power Management
                cmd wifi force-low-latency-mode enabled 2>/dev/null
                cmd wifi set-wifi-verbose-logging disabled 2>/dev/null
                cmd wifi set-power-save-mode 0 2>/dev/null
                setprop net.tcp.delack 0 2>/dev/null
                settings put global wifi_sleep_policy 2 2>/dev/null
                settings put global wifi_watchdog_poor_network_test_enabled 0 2>/dev/null
                settings put global wifi_networks_available_notification_on 0 2>/dev/null
                settings put global network_avoid_bad_wifi 0 2>/dev/null

                # Linux Kernel Socket Low-Latency & Fast Recycling
                echo 1 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null
                echo 0 > /proc/sys/net/ipv4/tcp_slow_start_after_idle 2>/dev/null

                # Dynamic wireless interface power-save and packet filtering traversal
                for w_if in /sys/class/net/wlan* /sys/class/net/wifi* /sys/class/net/sta*; do
                    [ -d "${'$'}w_if" ] || continue
                    w_name="${'$'}{w_if##*/}"
                    which iw >/dev/null 2>&1 && iw dev "${'$'}w_name" set power_save off 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" set_power 0 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" gEnablePacketFilter 0 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" mp_duty_cycle 100 2>/dev/null || true
                    which wl >/dev/null 2>&1 && wl -i "${'$'}w_name" PM 0 2>/dev/null || true
                done

                # MediaTek Hardware Silicon Hooks
                if [ -f /proc/net/wlan/setCAM ]; then
                    chmod 666 /proc/net/wlan/setCAM 2>/dev/null
                    echo 1 > /proc/net/wlan/setCAM 2>/dev/null
                fi
                if [ -f /proc/net/wlan/autoPerfCfg ]; then
                    echo "ForceEnable:1" > /proc/net/wlan/autoPerfCfg 2>/dev/null
                fi
                if [ -f /proc/net/wlan/pkt_filter ]; then
                    echo 0 > /proc/net/wlan/pkt_filter 2>/dev/null
                fi
                if [ -f /proc/net/wlan/dbgLevel ]; then
                    echo 0 > /proc/net/wlan/dbgLevel 2>/dev/null
                fi

                # Qualcomm Snapdragon WCNSS / QCACLD Low-Latency STA Hooks
                if [ -d /sys/module/wlan/parameters ]; then
                    [ -f /sys/module/wlan/parameters/con_mode ] && echo 0 > /sys/module/wlan/parameters/con_mode 2>/dev/null
                    [ -f /sys/module/wlan/parameters/rx_mode ] && echo 1 > /sys/module/wlan/parameters/rx_mode 2>/dev/null
                    [ -f /sys/module/wlan/parameters/gEnablePacketFilter ] && echo 0 > /sys/module/wlan/parameters/gEnablePacketFilter 2>/dev/null
                    [ -f /sys/module/wlan/parameters/dynamic_ps ] && echo 0 > /sys/module/wlan/parameters/dynamic_ps 2>/dev/null
                fi
                setprop persist.vendor.data.mode concurrent 2>/dev/null
                setprop persist.vendor.data.iwlan.enable true 2>/dev/null

                # Unisoc (Spreadtrum) SPRDWLAN Driver Hooks
                if [ -f /proc/sprdwlan/auto_power ]; then
                    echo 0 > /proc/sprdwlan/auto_power 2>/dev/null
                fi
                if [ -f /proc/sprdwlan/dynamic_ps ]; then
                    echo 0 > /proc/sprdwlan/dynamic_ps 2>/dev/null
                fi
                if [ -f /sys/module/sprdwl_ng/parameters/auto_power ]; then
                    echo 0 > /sys/module/sprdwl_ng/parameters/auto_power 2>/dev/null
                fi
                if [ -f /sys/module/sprdwl_ng/parameters/rx_mode ]; then
                    echo 1 > /sys/module/sprdwl_ng/parameters/rx_mode 2>/dev/null
                fi
                if [ -f /sys/module/sprdwl_ng/parameters/packet_filter ]; then
                    echo 0 > /sys/module/sprdwl_ng/parameters/packet_filter 2>/dev/null
                fi
                setprop persist.vendor.sprd.wlan.lowpower 0 2>/dev/null
                setprop persist.vendor.sprd.wlan.performance 1 2>/dev/null
                setprop persist.sys.sprd.wlan.latency 1 2>/dev/null

                # Broadcom / Samsung Exynos DHD & SCSC Hooks
                if [ -d /sys/module/dhd/parameters ]; then
                    [ -f /sys/module/dhd/parameters/dhd_slpauto ] && echo 0 > /sys/module/dhd/parameters/dhd_slpauto 2>/dev/null
                    [ -f /sys/module/dhd/parameters/dhd_watchdog_ms ] && echo 0 > /sys/module/dhd/parameters/dhd_watchdog_ms 2>/dev/null
                    [ -f /sys/module/dhd/parameters/dhd_pkt_filter_enable ] && echo 0 > /sys/module/dhd/parameters/dhd_pkt_filter_enable 2>/dev/null
                fi
                if [ -f /sys/module/bcmdhd/parameters/op_mode ]; then
                    echo 1 > /sys/module/bcmdhd/parameters/op_mode 2>/dev/null
                fi
                if [ -d /sys/module/scsc_wlan/parameters ]; then
                    [ -f /sys/module/scsc_wlan/parameters/slp_enable ] && echo 0 > /sys/module/scsc_wlan/parameters/slp_enable 2>/dev/null
                    [ -f /sys/module/scsc_wlan/parameters/roam_trigger ] && echo -75 > /sys/module/scsc_wlan/parameters/roam_trigger 2>/dev/null
                fi
                echo ok
                """.trimIndent()
            } else {
                """
                cmd wifi force-low-latency-mode disabled 2>/dev/null
                cmd wifi set-power-save-mode 1 2>/dev/null
                settings put global wifi_sleep_policy 2 2>/dev/null
                echo 0 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null
                for w_if in /sys/class/net/wlan* /sys/class/net/wifi* /sys/class/net/sta*; do
                    [ -d "${'$'}w_if" ] || continue
                    w_name="${'$'}{w_if##*/}"
                    which iw >/dev/null 2>&1 && iw dev "${'$'}w_name" set power_save on 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" set_power 1 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" gEnablePacketFilter 1 2>/dev/null || true
                    which wl >/dev/null 2>&1 && wl -i "${'$'}w_name" PM 1 2>/dev/null || true
                done
                if [ -f /proc/net/wlan/setCAM ]; then
                    echo 0 > /proc/net/wlan/setCAM 2>/dev/null
                fi
                if [ -f /proc/net/wlan/autoPerfCfg ]; then
                    echo "ForceEnable:0" > /proc/net/wlan/autoPerfCfg 2>/dev/null
                fi
                if [ -d /sys/module/wlan/parameters ]; then
                    [ -f /sys/module/wlan/parameters/con_mode ] && echo 1 > /sys/module/wlan/parameters/con_mode 2>/dev/null
                    [ -f /sys/module/wlan/parameters/gEnablePacketFilter ] && echo 1 > /sys/module/wlan/parameters/gEnablePacketFilter 2>/dev/null
                    [ -f /sys/module/wlan/parameters/dynamic_ps ] && echo 1 > /sys/module/wlan/parameters/dynamic_ps 2>/dev/null
                fi
                if [ -f /proc/sprdwlan/auto_power ]; then
                    echo 1 > /proc/sprdwlan/auto_power 2>/dev/null
                fi
                if [ -d /sys/module/dhd/parameters ]; then
                    [ -f /sys/module/dhd/parameters/dhd_slpauto ] && echo 1 > /sys/module/dhd/parameters/dhd_slpauto 2>/dev/null
                fi
                if [ -d /sys/module/scsc_wlan/parameters ]; then
                    [ -f /sys/module/scsc_wlan/parameters/slp_enable ] && echo 1 > /sys/module/scsc_wlan/parameters/slp_enable 2>/dev/null
                fi
                echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun applyAntiJitterScan(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val valStr = if (enabled) "0" else "1"
            val scanInterval = if (enabled) "300000" else "15000"
            val cmd = """
                settings put global wifi_scan_always_enabled $valStr 2>/dev/null
                settings put global wifi_scan_throttle_enabled 0 2>/dev/null
                settings put global wifi_framework_scan_interval_ms $scanInterval 2>/dev/null
                echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun applyFqCodel(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled) {
                """
                # 1. Dynamic CPU bitmask from all online CPU cores (universal for 4, 6, 8, 10, 12 cores)
                mask=0
                for c in /sys/devices/system/cpu/cpu[0-9]*; do
                    [ -f "${'$'}c/online" ] && [ "$(cat "${'$'}c/online" 2>/dev/null)" = "0" ] && continue
                    num="${'$'}{c##*cpu}"
                    mask=${'$'}(( mask | (1 << num) ))
                done
                [ "${'$'}mask" -eq 0 ] && mask=255
                cpu_hex=$(printf "%x" "${'$'}mask")

                # 2. Dynamic multi-queue queue allocation for all physical, wireless & cellular interfaces
                echo 32768 > /proc/sys/net/core/rps_sock_flow_entries 2>/dev/null
                echo 600 > /proc/sys/net/core/netdev_budget 2>/dev/null
                echo 4000 > /proc/sys/net/core/netdev_budget_usecs 2>/dev/null
                echo fq_codel > /proc/sys/net/core/default_qdisc 2>/dev/null || echo fq > /proc/sys/net/core/default_qdisc 2>/dev/null
                for if_path in /sys/class/net/*; do
                    iface="${'$'}{if_path##*/}"
                    case "${'$'}iface" in
                        lo|dummy*|sit*|ip6tnl*|bond*|tun*|tap*|ifb*) continue ;;
                    esac
                    for rx in "${'$'}if_path"/queues/rx-*; do
                        [ -f "${'$'}rx/rps_cpus" ] && echo "${'$'}cpu_hex" > "${'$'}rx/rps_cpus" 2>/dev/null
                        [ -f "${'$'}rx/rps_flow_cnt" ] && echo 4096 > "${'$'}rx/rps_flow_cnt" 2>/dev/null
                    done
                    for tx in "${'$'}if_path"/queues/tx-*; do
                        [ -f "${'$'}tx/xps_cpus" ] && echo "${'$'}cpu_hex" > "${'$'}tx/xps_cpus" 2>/dev/null
                    done
                    tc qdisc replace dev "${'$'}iface" root fq_codel 2>/dev/null || tc qdisc replace dev "${'$'}iface" root fq 2>/dev/null || true
                done
                echo ok
                """.trimIndent()
            } else {
                """
                echo pfifo_fast > /proc/sys/net/core/default_qdisc 2>/dev/null
                echo 0 > /proc/sys/net/core/rps_sock_flow_entries 2>/dev/null
                echo 300 > /proc/sys/net/core/netdev_budget 2>/dev/null
                for if_path in /sys/class/net/*; do
                    iface="${'$'}{if_path##*/}"
                    case "${'$'}iface" in
                        lo|dummy*|sit*|ip6tnl*|bond*|tun*|tap*|ifb*) continue ;;
                    esac
                    for rx in "${'$'}if_path"/queues/rx-*; do
                        [ -f "${'$'}rx/rps_flow_cnt" ] && echo 0 > "${'$'}rx/rps_flow_cnt" 2>/dev/null
                    done
                    tc qdisc del dev "${'$'}iface" root 2>/dev/null || true
                done
                echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun applyTcpBuffers16m(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled) {
                """
                echo 16777216 > /proc/sys/net/core/rmem_max 2>/dev/null
                echo 16777216 > /proc/sys/net/core/wmem_max 2>/dev/null
                echo 262144 > /proc/sys/net/core/rmem_default 2>/dev/null
                echo 262144 > /proc/sys/net/core/wmem_default 2>/dev/null
                echo "4096 87380 16777216" > /proc/sys/net/ipv4/tcp_rmem 2>/dev/null
                echo "4096 65536 16777216" > /proc/sys/net/ipv4/tcp_wmem 2>/dev/null
                for k in default wifi lte 5g hspa hsupa hsdpa umts edge gprs evdo cdma; do
                    setprop net.tcp.buffersize.${'$'}k "524288,1048576,16777216,262144,524288,16777216" 2>/dev/null
                done
                echo ok
                """.trimIndent()
            } else {
                """
                echo 6922240 > /proc/sys/net/core/rmem_max 2>/dev/null
                echo 4525824 > /proc/sys/net/core/wmem_max 2>/dev/null
                echo 262144 > /proc/sys/net/core/rmem_default 2>/dev/null
                echo 262144 > /proc/sys/net/core/wmem_default 2>/dev/null
                echo "1730560 3461120 6922240" > /proc/sys/net/ipv4/tcp_rmem 2>/dev/null
                echo "524288 1048576 4525824" > /proc/sys/net/ipv4/tcp_wmem 2>/dev/null
                for k in default wifi lte 5g hspa hsupa hsdpa umts edge gprs evdo cdma; do
                    setprop net.tcp.buffersize.${'$'}k "" 2>/dev/null
                done
                echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun applyTcpFastOpen(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled) {
                """
                echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null
                echo 0 > /proc/sys/net/ipv4/tcp_autocorking 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/tcp_sack 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/tcp_dsack 2>/dev/null
                echo 15 > /proc/sys/net/ipv4/tcp_fin_timeout 2>/dev/null
                echo 10000 > /proc/sys/net/core/netdev_max_backlog 2>/dev/null
                echo 4096 > /proc/sys/net/core/somaxconn 2>/dev/null
                echo 4096 > /proc/sys/net/ipv4/tcp_max_syn_backlog 2>/dev/null
                echo 3 > /proc/sys/net/ipv4/tcp_syn_retries 2>/dev/null
                echo 3 > /proc/sys/net/ipv4/tcp_synack_retries 2>/dev/null
                echo 3 > /proc/sys/net/ipv4/tcp_early_retrans 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/tcp_recovery 2>/dev/null
                echo 2 > /proc/sys/net/ipv4/tcp_frto 2>/dev/null
                echo 16384 > /proc/sys/net/ipv4/tcp_notsent_lowat 2>/dev/null
                echo 0 > /proc/sys/net/ipv4/tcp_slow_start_after_idle 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/tcp_no_metrics_save 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/tcp_mtu_probing 2>/dev/null
                echo 300 > /proc/sys/net/ipv4/tcp_keepalive_time 2>/dev/null
                echo 15 > /proc/sys/net/ipv4/tcp_keepalive_intvl 2>/dev/null
                echo 5 > /proc/sys/net/ipv4/tcp_keepalive_probes 2>/dev/null
                echo 2 > /proc/sys/net/ipv4/conf/all/arp_announce 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/conf/all/arp_ignore 2>/dev/null
                setprop net.dns.cache_size 1024 2>/dev/null
                setprop net.dns.cache_ttl 600 2>/dev/null
                setprop net.dns.cache_net_ttl 300 2>/dev/null
                echo ok
                """.trimIndent()
            } else {
                """
                echo 1 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/tcp_autocorking 2>/dev/null
                echo 60 > /proc/sys/net/ipv4/tcp_fin_timeout 2>/dev/null
                echo 1000 > /proc/sys/net/core/netdev_max_backlog 2>/dev/null
                echo 128 > /proc/sys/net/core/somaxconn 2>/dev/null
                echo 1024 > /proc/sys/net/ipv4/tcp_max_syn_backlog 2>/dev/null
                echo 6 > /proc/sys/net/ipv4/tcp_syn_retries 2>/dev/null
                echo 5 > /proc/sys/net/ipv4/tcp_synack_retries 2>/dev/null
                echo 4294967295 > /proc/sys/net/ipv4/tcp_notsent_lowat 2>/dev/null
                echo 1 > /proc/sys/net/ipv4/tcp_slow_start_after_idle 2>/dev/null
                echo 0 > /proc/sys/net/ipv4/tcp_no_metrics_save 2>/dev/null
                echo 0 > /proc/sys/net/ipv4/tcp_mtu_probing 2>/dev/null
                echo 7200 > /proc/sys/net/ipv4/tcp_keepalive_time 2>/dev/null
                echo 75 > /proc/sys/net/ipv4/tcp_keepalive_intvl 2>/dev/null
                echo 9 > /proc/sys/net/ipv4/tcp_keepalive_probes 2>/dev/null
                echo 0 > /proc/sys/net/ipv4/conf/all/arp_announce 2>/dev/null
                echo 0 > /proc/sys/net/ipv4/conf/all/arp_ignore 2>/dev/null
                echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun applyMtkHardwareAggregation(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled) {
                """
                # Dynamic wireless interface power-save and packet filtering traversal
                for w_if in /sys/class/net/wlan* /sys/class/net/wifi* /sys/class/net/sta*; do
                    [ -d "${'$'}w_if" ] || continue
                    w_name="${'$'}{w_if##*/}"
                    which iw >/dev/null 2>&1 && iw dev "${'$'}w_name" set power_save off 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" set_power 0 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" gEnablePacketFilter 0 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" mp_duty_cycle 100 2>/dev/null || true
                    which wl >/dev/null 2>&1 && wl -i "${'$'}w_name" PM 0 2>/dev/null || true
                done

                # 1. MediaTek Direct Silicon Hooks
                if [ -f /proc/net/wlan/autoPerfCfg ]; then
                    echo "ForceEnable:1" > /proc/net/wlan/autoPerfCfg 2>/dev/null
                fi
                if [ -f /proc/net/wlan/setCAM ]; then
                    chmod 666 /proc/net/wlan/setCAM 2>/dev/null
                    echo "1" > /proc/net/wlan/setCAM 2>/dev/null
                fi
                if [ -f /proc/net/wlan/pkt_filter ]; then
                    echo 0 > /proc/net/wlan/pkt_filter 2>/dev/null
                fi
                if [ -f /proc/net/wlan/dbgLevel ]; then
                    echo 0 > /proc/net/wlan/dbgLevel 2>/dev/null
                fi

                # 2. Qualcomm Snapdragon WCNSS / QCACLD Direct Hooks
                if [ -d /sys/module/wlan/parameters ]; then
                    [ -f /sys/module/wlan/parameters/con_mode ] && echo 0 > /sys/module/wlan/parameters/con_mode 2>/dev/null
                    [ -f /sys/module/wlan/parameters/rx_mode ] && echo 1 > /sys/module/wlan/parameters/rx_mode 2>/dev/null
                    [ -f /sys/module/wlan/parameters/gEnablePacketFilter ] && echo 0 > /sys/module/wlan/parameters/gEnablePacketFilter 2>/dev/null
                    [ -f /sys/module/wlan/parameters/dynamic_ps ] && echo 0 > /sys/module/wlan/parameters/dynamic_ps 2>/dev/null
                fi
                setprop persist.vendor.data.mode concurrent 2>/dev/null
                setprop persist.vendor.data.iwlan.enable true 2>/dev/null

                # 3. Unisoc SPRDWLAN Direct Hooks
                if [ -f /proc/sprdwlan/auto_power ]; then
                    echo 0 > /proc/sprdwlan/auto_power 2>/dev/null
                fi
                if [ -f /proc/sprdwlan/dynamic_ps ]; then
                    echo 0 > /proc/sprdwlan/dynamic_ps 2>/dev/null
                fi
                if [ -f /sys/module/sprdwl_ng/parameters/auto_power ]; then
                    echo 0 > /sys/module/sprdwl_ng/parameters/auto_power 2>/dev/null
                fi
                if [ -f /sys/module/sprdwl_ng/parameters/rx_mode ]; then
                    echo 1 > /sys/module/sprdwl_ng/parameters/rx_mode 2>/dev/null
                fi
                if [ -f /sys/module/sprdwl_ng/parameters/packet_filter ]; then
                    echo 0 > /sys/module/sprdwl_ng/parameters/packet_filter 2>/dev/null
                fi
                setprop persist.vendor.sprd.wlan.lowpower 0 2>/dev/null
                setprop persist.vendor.sprd.wlan.performance 1 2>/dev/null
                setprop persist.sys.sprd.wlan.latency 1 2>/dev/null

                # 4. Broadcom / Samsung Exynos / Google Tensor SCSC & DHD Hooks
                if [ -d /sys/module/dhd/parameters ]; then
                    [ -f /sys/module/dhd/parameters/dhd_slpauto ] && echo 0 > /sys/module/dhd/parameters/dhd_slpauto 2>/dev/null
                    [ -f /sys/module/dhd/parameters/dhd_watchdog_ms ] && echo 0 > /sys/module/dhd/parameters/dhd_watchdog_ms 2>/dev/null
                    [ -f /sys/module/dhd/parameters/dhd_pkt_filter_enable ] && echo 0 > /sys/module/dhd/parameters/dhd_pkt_filter_enable 2>/dev/null
                fi
                if [ -f /sys/module/bcmdhd/parameters/op_mode ]; then
                    echo 1 > /sys/module/bcmdhd/parameters/op_mode 2>/dev/null
                fi
                if [ -d /sys/module/scsc_wlan/parameters ]; then
                    [ -f /sys/module/scsc_wlan/parameters/slp_enable ] && echo 0 > /sys/module/scsc_wlan/parameters/slp_enable 2>/dev/null
                    [ -f /sys/module/scsc_wlan/parameters/roam_trigger ] && echo -75 > /sys/module/scsc_wlan/parameters/roam_trigger 2>/dev/null
                fi

                # 5. Universal Android Framework Wi-Fi Low Latency
                cmd wifi force-low-latency-mode enabled 2>/dev/null
                cmd wifi set-power-save-mode 0 2>/dev/null
                echo ok
                """.trimIndent()
            } else {
                """
                for w_if in /sys/class/net/wlan* /sys/class/net/wifi* /sys/class/net/sta*; do
                    [ -d "${'$'}w_if" ] || continue
                    w_name="${'$'}{w_if##*/}"
                    which iw >/dev/null 2>&1 && iw dev "${'$'}w_name" set power_save on 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" set_power 1 2>/dev/null || true
                    which iwpriv >/dev/null 2>&1 && iwpriv "${'$'}w_name" gEnablePacketFilter 1 2>/dev/null || true
                    which wl >/dev/null 2>&1 && wl -i "${'$'}w_name" PM 1 2>/dev/null || true
                done
                if [ -f /proc/net/wlan/autoPerfCfg ]; then
                    echo "ForceEnable:0" > /proc/net/wlan/autoPerfCfg 2>/dev/null
                fi
                if [ -f /proc/net/wlan/setCAM ]; then
                    echo "0" > /proc/net/wlan/setCAM 2>/dev/null
                fi
                if [ -d /sys/module/wlan/parameters ]; then
                    [ -f /sys/module/wlan/parameters/con_mode ] && echo 1 > /sys/module/wlan/parameters/con_mode 2>/dev/null
                    [ -f /sys/module/wlan/parameters/gEnablePacketFilter ] && echo 1 > /sys/module/wlan/parameters/gEnablePacketFilter 2>/dev/null
                    [ -f /sys/module/wlan/parameters/dynamic_ps ] && echo 1 > /sys/module/wlan/parameters/dynamic_ps 2>/dev/null
                fi
                if [ -f /proc/sprdwlan/auto_power ]; then
                    echo 1 > /proc/sprdwlan/auto_power 2>/dev/null
                fi
                if [ -d /sys/module/dhd/parameters ]; then
                    [ -f /sys/module/dhd/parameters/dhd_slpauto ] && echo 1 > /sys/module/dhd/parameters/dhd_slpauto 2>/dev/null
                fi
                if [ -d /sys/module/scsc_wlan/parameters ]; then
                    [ -f /sys/module/scsc_wlan/parameters/slp_enable ] && echo 1 > /sys/module/scsc_wlan/parameters/slp_enable 2>/dev/null
                fi
                cmd wifi force-low-latency-mode disabled 2>/dev/null
                cmd wifi set-power-save-mode 1 2>/dev/null
                echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readWifiTelemetry(): WifiTelemetryInfo = withContext(Dispatchers.IO) {
        try {
            val output = Shell.cmd("cmd wifi status 2>/dev/null").exec().out.joinToString("\n")
            if (!output.contains("Wifi is connected to") && !output.contains("WifiInfo:")) {
                return@withContext WifiTelemetryInfo(isConnected = false)
            }

            val ssidMatch = Regex("""Wifi is connected to "([^"]+)"""").find(output)
                ?: Regex("""SSID: "([^"]+)"""").find(output)
            val ssid = ssidMatch?.groupValues?.get(1) ?: "Terhubung"

            val ipMatch = Regex("""IP: /?([0-9.]+)""").find(output)
            val ip = ipMatch?.groupValues?.get(1) ?: ""

            val rssiMatch = Regex("""RSSI: (-?[0-9]+)""").find(output)
            val rssi = rssiMatch?.groupValues?.get(1)?.toIntOrNull() ?: -60

            val linkSpeedMatch = Regex("""Link speed: ([0-9]+)Mbps""").find(output)
            val linkSpeed = linkSpeedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val maxSpeedMatch = Regex("""Max Supported Tx Link speed: ([0-9]+)Mbps""").find(output)
            val maxSpeed = maxSpeedMatch?.groupValues?.get(1)?.toIntOrNull() ?: linkSpeed

            val freqMatch = Regex("""Frequency: ([0-9]+)MHz""").find(output)
            val freq = freqMatch?.groupValues?.get(1)?.toIntOrNull() ?: 5000

            val stdMatch = Regex("""Wi-Fi standard: ([0-9]+)""").find(output)
            val stdNum = stdMatch?.groupValues?.get(1)?.toIntOrNull() ?: 5
            val wifiStandard = when (stdNum) {
                4 -> "Wi-Fi 4 (802.11n)"
                5 -> "Wi-Fi 5 (802.11ac)"
                6 -> "Wi-Fi 6 (802.11ax)"
                7 -> "Wi-Fi 7 (802.11be)"
                else -> "Wi-Fi"
            }

            var pingMs = 0f
            if (ip.isNotBlank()) {
                val gatewayIp = Shell.cmd("ip route list table 0 2>/dev/null | grep -E 'default via [0-9.]+' | sed -n 's/.*default via \\([0-9.]*\\).*/\\1/p' | head -n 1").exec().out.firstOrNull()?.trim()?.ifBlank { null }
                    ?: (ip.substringBeforeLast(".") + ".1")
                val pingOut = Shell.cmd("ping -c 1 -W 1 $gatewayIp 2>/dev/null").exec().out.joinToString("\n")
                val pingMatch = Regex("""time=([0-9.]+) ms""").find(pingOut)
                pingMs = pingMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            }

            WifiTelemetryInfo(
                isConnected = true,
                ssid = ssid,
                ipAddress = ip,
                frequencyMhz = freq,
                linkSpeedMbps = linkSpeed,
                maxLinkSpeedMbps = maxSpeed,
                rssiDbm = rssi,
                wifiStandard = wifiStandard,
                gatewayPingMs = pingMs,
                isLowLatencyActive = output.contains("low-latency") || linkSpeed > 200
            )
        } catch (_: Exception) {
            WifiTelemetryInfo(isConnected = false)
        }
    }

    suspend fun readPrivateDns(): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val mode = Shell.cmd("settings get global private_dns_mode 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "off"
            val spec = Shell.cmd("settings get global private_dns_specifier 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            Pair(mode, spec)
        } catch (_: Exception) {
            Pair("off", "")
        }
    }

    suspend fun applyPrivateDns(mode: String, specifier: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (mode == "hostname" && specifier.isNotBlank()) {
                Shell.cmd("settings put global private_dns_mode hostname; settings put global private_dns_specifier $specifier").exec()
            } else {
                Shell.cmd("settings put global private_dns_mode off; settings put global private_dns_specifier \"\"").exec()
            }
            true
        } catch (_: Exception) { false }
    }

    suspend fun readAvailableTcpCongestion(): List<String> = withContext(Dispatchers.IO) {
        try {
            val out = Shell.cmd("cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            if (out.isNotBlank()) {
                out.split(Regex("\\s+")).filter { it.isNotBlank() }
            } else {
                listOf("cubic", "bic", "reno")
            }
        } catch (_: Exception) {
            listOf("cubic", "bic", "reno")
        }
    }

    suspend fun applyTcpCongestionControl(algo: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("sysctl -w net.ipv4.tcp_congestion_control=$algo 2>/dev/null").exec()
            true
        } catch (_: Exception) { false }
    }

    suspend fun readAudioTelemetry(): AudioTelemetryInfo = withContext(Dispatchers.IO) {
        try {
            val deviceOut = Shell.cmd("dumpsys audio 2>/dev/null | grep -m 1 -E 'Devices: speaker|Devices: wired_headset|Devices: bluetooth|Devices: usb_headset'").exec().out.firstOrNull()?.trim() ?: ""
            val activeDevice = when {
                deviceOut.contains("bluetooth", ignoreCase = true) -> "Bluetooth (A2DP)"
                deviceOut.contains("wired_headset", ignoreCase = true) -> "Headphone (3.5mm)"
                deviceOut.contains("usb", ignoreCase = true) -> "USB Audio DAC"
                deviceOut.contains("speaker", ignoreCase = true) -> "Internal Speaker"
                else -> "Speaker / Default"
            }
            val mmapPolicy = Shell.cmd("getprop aaudio.mmap_policy 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "2"
            val isMmapForced = mmapPolicy == "3"
            val deepBuffer = Shell.cmd("getprop audio.deep_buffer.media 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "true"
            val isDeepBufferBypassed = deepBuffer.equals("false", ignoreCase = true)
            val offloadKb = Shell.cmd("getprop vendor.audio.offload.buffer.size.kb 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "64"
            val isDirectDac = offloadKb == "32"

            AudioTelemetryInfo(
                activeDevice = activeDevice,
                sampleRate = "48000 Hz",
                isMmapForced = isMmapForced,
                isDeepBufferBypassed = isDeepBufferBypassed,
                isDirectDacOffload = isDirectDac
            )
        } catch (_: Exception) {
            AudioTelemetryInfo()
        }
    }

    suspend fun applyAudioMmap(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = if (enabled) "3" else "2"
            val burst = if (enabled) "2000" else "4000"
            val cmd = "setprop aaudio.mmap_policy $p; setprop aaudio.mmap_exclusive_policy $p; setprop aaudio.hw_burst_min_usec $burst; [ \"$(getprop aaudio.mmap_policy)\" = \"$p\" ] && echo ok"
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioDeepBufferBypass(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val valStr = if (enabled) "false" else "true"
            Shell.cmd("setprop audio.deep_buffer.media $valStr; setprop persist.sys.phh.disable_fast_audio 0").exec()
            true
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioDirectDacOffload(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val kb = if (enabled) "32" else "64"
            val mult = if (enabled) "1" else "2"
            Shell.cmd("setprop vendor.audio.offload.buffer.size.kb $kb; setprop af.fast_track_multiplier $mult").exec()
            true
        } catch (_: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  ADB Wireless Keep-Alive & Port Lock Engine
    // ----------------------------------------------------------------

    suspend fun readAdbKeepAliveConfig(): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        try {
            val enabledStr = Shell.cmd("$LYNXD_PATH state get adb_keepalive.enabled").exec().out.firstOrNull()?.trim() ?: "false"
            val portStr = Shell.cmd("$LYNXD_PATH state get adb_keepalive.port 5555").exec().out.firstOrNull()?.trim() ?: "5555"
            val enabled = enabledStr == "true" || enabledStr == "1"
            val port = portStr.toIntOrNull()?.coerceIn(1024, 65535) ?: 5555
            Pair(enabled, port)
        } catch (_: Exception) {
            Pair(false, 5555)
        }
    }

    suspend fun setAdbKeepAlive(enabled: Boolean, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val clampedPort = port.coerceIn(1024, 65535)
            val sub = if (enabled) "enable $clampedPort" else "disable"
            val result = Shell.cmd("$LYNXD_PATH adb $sub").exec()
            result.isSuccess
        } catch (_: Exception) { false }
    }

    suspend fun setAdbPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val clampedPort = port.coerceIn(1024, 65535)
            val result = Shell.cmd("$LYNXD_PATH adb set-port $clampedPort").exec()
            result.isSuccess
        } catch (_: Exception) { false }
    }

    suspend fun getAdbStatus(): String = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("$LYNXD_PATH adb status").exec()
            result.out.joinToString("\n").trim()
        } catch (_: Exception) { "{}" }
    }

    suspend fun applyJoyoseNeutralizer(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled) {
                """
                killall -STOP com.xiaomi.joyose 2>/dev/null
                am force-stop com.xiaomi.joyose 2>/dev/null
                pm disable-user --user 0 com.xiaomi.joyose 2>/dev/null
                pm disable-user --user 0 com.samsung.android.game.gos 2>/dev/null
                echo ok
                """.trimIndent()
            } else {
                """
                pm enable com.xiaomi.joyose 2>/dev/null
                pm enable com.samsung.android.game.gos 2>/dev/null
                killall -CONT com.xiaomi.joyose 2>/dev/null
                echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }


}
