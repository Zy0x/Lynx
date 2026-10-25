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
static void *target_cubictcp_ssthresh = NULL;
static void *target_bictcp_ssthresh = NULL;
static void *target_reno_ssthresh = NULL;
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

static int k_strncmp(const char *s1, const char *s2, int n)
{
    if (!s1 || !s2 || n <= 0) return -1;
    while (n > 0 && *s1 && (*s1 == *s2)) {
        s1++;
        s2++;
        n--;
    }
    if (n == 0) return 0;
    return *(const unsigned char *)s1 - *(const unsigned char *)s2;
}

static uint32_t k_atou32(const char *s)
{
    uint32_t v = 0;
    if (!s) return 0;
    while (*s >= '0' && *s <= '9') {
        v = (v * 10U) + (uint32_t)(*s - '0');
        s++;
    }
    return v;
}

// 1. Dynamic Proportional After-Hook for tcp_init_cwnd / __tcp_init_cwnd
// Scales the kernel's baseline CWND proportionally (3x baseline, clamped to [16, g_init_cwnd_target])
// while respecting route-specific overrides that already exceed the target.
static void after_tcp_init_cwnd(hook_fargs2_t *args, void *udata)
{
    if (!g_net_turbo_enabled) return;

    uint32_t orig_cwnd = (uint32_t)args->ret;
    uint32_t max_target = g_init_cwnd_target;
    if (orig_cwnd > 0 && orig_cwnd < max_target) {
        uint32_t scaled = orig_cwnd * 3U;
        if (scaled < 16U) scaled = 16U;
        if (scaled > max_target) scaled = max_target;
        args->ret = (uint64_t)scaled;
    }
}

// 2. Universal After-Hook for TCP Congestion ssthresh Recalculation (CUBIC / BIC / Reno)
// Cushions wireless RF packet loss penalty by +12.5% (ret + (ret >> 3)) so transient Wi-Fi/LTE
// air-time interference does not cause severe throughput stalls.
static void after_tcp_ssthresh(hook_fargs1_t *args, void *udata)
{
    if (!g_net_turbo_enabled) return;

    uint32_t ssthresh = (uint32_t)args->ret;
    if (ssthresh > 4 && ssthresh < 0x7fffffffU) {
        uint32_t boosted = ssthresh + (ssthresh >> 3);
        args->ret = (uint64_t)boosted;
    }
}

// 3. Before-Hook for kalPerMonSetForceEnableFlag (when present on MediaTek Gen4m kernels)
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
        } else if (k_strncmp(args, "cwnd=", 5) == 0) {
            uint32_t val = k_atou32(args + 5);
            if (val >= 10U && val <= 128U) {
                g_init_cwnd_target = val;
                logkd(LOG_TAG "Control: Dynamic CWND target updated to %u\n", g_init_cwnd_target);
            }
        }
    }
    return 0;
}

static void *resolve_first_symbol(const char *const *names, int count)
{
    if (!kallsyms_lookup_name) return NULL;
    for (int i = 0; i < count; i++) {
        void *addr = (void *)kallsyms_lookup_name(names[i]);
        if (addr) return addr;
    }
    return NULL;
}

static long lynx_net_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Dynamic Lynx Network & Wi-Fi Turbo KPM v1.1...\n");

    if (kallsyms_lookup_name) {
        const char *const cwnd_syms[] = { "tcp_init_cwnd", "__tcp_init_cwnd" };
        target_tcp_init_cwnd = resolve_first_symbol(cwnd_syms, 2);

        target_cubictcp_ssthresh = (void *)kallsyms_lookup_name("cubictcp_recalc_ssthresh");
        target_bictcp_ssthresh = (void *)kallsyms_lookup_name("bictcp_recalc_ssthresh");
        target_reno_ssthresh = (void *)kallsyms_lookup_name("tcp_reno_ssthresh");
        target_kal_permon_force = (void *)kallsyms_lookup_name("kalPerMonSetForceEnableFlag");
    }

    if (target_tcp_init_cwnd) {
        hook_err_t err = hook_wrap2(target_tcp_init_cwnd, 0, after_tcp_init_cwnd, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked TCP init_cwnd at %px (Dynamic 3x scale up to %u)\n",
                  target_tcp_init_cwnd, g_init_cwnd_target);
        } else {
            target_tcp_init_cwnd = NULL;
        }
    }

    if (target_cubictcp_ssthresh) {
        if (hook_wrap1(target_cubictcp_ssthresh, 0, after_tcp_ssthresh, 0) != HOOK_NO_ERR) {
            target_cubictcp_ssthresh = NULL;
        }
    }
    if (target_bictcp_ssthresh && target_bictcp_ssthresh != target_cubictcp_ssthresh) {
        if (hook_wrap1(target_bictcp_ssthresh, 0, after_tcp_ssthresh, 0) != HOOK_NO_ERR) {
            target_bictcp_ssthresh = NULL;
        }
    }
    if (target_reno_ssthresh) {
        if (hook_wrap1(target_reno_ssthresh, 0, after_tcp_ssthresh, 0) != HOOK_NO_ERR) {
            target_reno_ssthresh = NULL;
        }
    }

    if (target_kal_permon_force) {
        if (hook_wrap1(target_kal_permon_force, before_kal_permon_force, 0, 0) != HOOK_NO_ERR) {
            target_kal_permon_force = NULL;
        }
    }

    g_net_turbo_enabled = 1;
    logkd(LOG_TAG "Lynx Dynamic Network Turbo KPM v1.1 armed.\n");
    return 0;
}

static long lynx_net_exit(void *__user reserved)
{
    g_net_turbo_enabled = 0;
    if (target_kal_permon_force) {
        unhook(target_kal_permon_force);
        target_kal_permon_force = NULL;
    }
    if (target_reno_ssthresh) {
        unhook(target_reno_ssthresh);
        target_reno_ssthresh = NULL;
    }
    if (target_bictcp_ssthresh) {
        unhook(target_bictcp_ssthresh);
        target_bictcp_ssthresh = NULL;
    }
    if (target_cubictcp_ssthresh) {
        unhook(target_cubictcp_ssthresh);
        target_cubictcp_ssthresh = NULL;
    }
    if (target_tcp_init_cwnd) {
        unhook(target_tcp_init_cwnd);
        target_tcp_init_cwnd = NULL;
    }
    logkd(LOG_TAG "Lynx Dynamic Network Turbo KPM unloaded cleanly.\n");
    return 0;
}

KPM_INIT(lynx_net_init);
KPM_CTL0(lynx_net_control0);
KPM_EXIT(lynx_net_exit);
