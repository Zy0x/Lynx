/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Network & Wi-Fi Low-Latency Turbo KernelPatch Module (KPM)
 * Author: ɴᴏɪʀ
 * Target: MediaTek / Universal ARM64 (Kernel 4.14+)
 * Framework: KernelPatch / APatch
 *
 * Features:
 * 1. TCP Initial Congestion Window Boost (tcp_init_cwnd: 10 -> 32 segments)
 *    Bypasses 3 slow-start RTTs on every new HTTPS/API/Game connection.
 * 2. Wireless Packet-Loss Cushion (bictcp_recalc_ssthresh)
 *    Prevents single-frame RF jitter on Wi-Fi/LTE from slashing cwnd by 30%.
 * 3. MediaTek Gen4m Wi-Fi Performance Monitor Lock (kalPerMonSetForceEnableFlag)
 *    Keeps hardware HIF/NAPI high-throughput state active without sleep drops.
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_net_turbo");
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Kernel TCP Initial Window Booster, Wireless Loss Cushion & MTK WLAN Turbo");

#define LOG_TAG "[LynxNetKPM] "

static volatile int g_net_turbo_enabled = 1;
static volatile uint32_t g_init_cwnd_target = 32;

static void *target_tcp_init_cwnd = NULL;
static void *target_bictcp_ssthresh = NULL;
static void *target_kal_permon_force = NULL;

static int k_strcmp(const char *s1, const char *s2)
{
    if (!s1 || !s2) return -1;
    while (*s1 && (*s1 == *s2)) {
        s1++;
        s2++;
    }
    return *(const unsigned char *)s1 - *(const unsigned char *)s2;
}

// 1. After-Hook for tcp_init_cwnd(__u32 __tcp_init_cwnd(struct tcp_sock *tp, const struct dst_entry *dst))
// Standard Linux 4.14 returns TCP_INIT_CWND (10). Boosting to 32 eliminates 3 RTTs of slow-start delay.
static void after_tcp_init_cwnd(hook_fargs2_t *args, void *udata)
{
    if (!g_net_turbo_enabled) return;

    uint32_t orig_cwnd = (uint32_t)args->ret;
    uint32_t target = g_init_cwnd_target;
    if (orig_cwnd > 0 && orig_cwnd < target) {
        args->ret = (uint64_t)target;
    }
}

// 2. After-Hook for bictcp_recalc_ssthresh(u32 bictcp_recalc_ssthresh(struct sock *sk))
// Cushions wireless RF packet loss penalty by +12.5% (ret + (ret >> 3)) so Wi-Fi interference
// does not cause severe throughput stalls.
static void after_bictcp_ssthresh(hook_fargs1_t *args, void *udata)
{
    if (!g_net_turbo_enabled) return;

    uint32_t ssthresh = (uint32_t)args->ret;
    if (ssthresh > 4 && ssthresh < 0x7fffffffU) {
        uint32_t boosted = ssthresh + (ssthresh >> 3);
        args->ret = (uint64_t)boosted;
    }
}

// 3. Before-Hook for kalPerMonSetForceEnableFlag(void kalPerMonSetForceEnableFlag(uint32_t u4Flag))
// Ensures MediaTek wlan_drv_gen4m Performance Monitor stays forced ON (1) when turbo is active.
static void before_kal_permon_force(hook_fargs1_t *args, void *udata)
{
    if (!g_net_turbo_enabled) return;
    if (args->arg0 == 0) {
        args->arg0 = 1;
    }
}

static long lynx_net_control0(const char *args, char *__user out_msg, int outlen)
{
    if (args) {
        if (k_strcmp(args, "turbo_on") == 0 || k_strcmp(args, "1") == 0) {
            g_net_turbo_enabled = 1;
            logkd(LOG_TAG "Control: Network & Wi-Fi Turbo ENABLED (init_cwnd=%u)\n", g_init_cwnd_target);
        } else if (k_strcmp(args, "turbo_off") == 0 || k_strcmp(args, "0") == 0) {
            g_net_turbo_enabled = 0;
            logkd(LOG_TAG "Control: Network & Wi-Fi Turbo DISABLED (OEM default)\n");
        }
    }
    return 0;
}

static long lynx_net_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Network & Wi-Fi Turbo KPM v1.0...\n");

    if (kallsyms_lookup_name) {
        target_tcp_init_cwnd = (void *)kallsyms_lookup_name("tcp_init_cwnd");
        target_bictcp_ssthresh = (void *)kallsyms_lookup_name("bictcp_recalc_ssthresh");
        target_kal_permon_force = (void *)kallsyms_lookup_name("kalPerMonSetForceEnableFlag");
    }

    if (target_tcp_init_cwnd) {
        hook_err_t err = hook_wrap2(target_tcp_init_cwnd, 0, after_tcp_init_cwnd, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked tcp_init_cwnd at %px (Initial CWND -> %u)\n",
                  target_tcp_init_cwnd, g_init_cwnd_target);
        } else {
            logke(LOG_TAG "Failed to hook tcp_init_cwnd: %d\n", err);
        }
    }

    if (target_bictcp_ssthresh) {
        hook_err_t err = hook_wrap1(target_bictcp_ssthresh, 0, after_bictcp_ssthresh, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked bictcp_recalc_ssthresh at %px (Wireless Loss Cushion active)\n",
                  target_bictcp_ssthresh);
        } else {
            logke(LOG_TAG "Failed to hook bictcp_recalc_ssthresh: %d\n", err);
        }
    }

    if (target_kal_permon_force) {
        hook_err_t err = hook_wrap1(target_kal_permon_force, before_kal_permon_force, 0, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked kalPerMonSetForceEnableFlag at %px (MTK Gen4m PerfLock active)\n",
                  target_kal_permon_force);
        }
    }

    g_net_turbo_enabled = 1;
    logkd(LOG_TAG "Lynx Network Turbo KPM v1.0 armed and active.\n");
    return 0;
}

static long lynx_net_exit(void *__user reserved)
{
    g_net_turbo_enabled = 0;
    if (target_kal_permon_force) {
        unhook(target_kal_permon_force);
        target_kal_permon_force = NULL;
    }
    if (target_bictcp_ssthresh) {
        unhook(target_bictcp_ssthresh);
        target_bictcp_ssthresh = NULL;
    }
    if (target_tcp_init_cwnd) {
        unhook(target_tcp_init_cwnd);
        target_tcp_init_cwnd = NULL;
    }
    logkd(LOG_TAG "Lynx Network Turbo KPM unloaded cleanly.\n");
    return 0;
}

KPM_INIT(lynx_net_init);
KPM_CTL0(lynx_net_control0);
KPM_EXIT(lynx_net_exit);
