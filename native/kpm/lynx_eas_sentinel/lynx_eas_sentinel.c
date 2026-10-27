/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - EAS Schedtune & Priority Sentinel KernelPatch Module (KPM)
 * Target: MediaTek Helio G96 (MT6781) / Linux 4.14+ (ARM64)
 * Purpose: Locks EAS schedtune priority and headroom for gaming render threads
 *          dynamically during Performance/Extreme mode, and automatically
 *          restores stock scheduling in Balance mode to ensure deep sleep and power efficiency.
 * Framework: KernelPatch / APatch
 *
 * Author: ɴᴏɪʀ
 * License: GPL v2
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_eas_sentinel");
KPM_VERSION("2.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic EAS Schedtune Priority Sentinel (Balance Aware)");

#define LOG_TAG "[LynxEASSentinel] "

// Target kernel symbols
static void *target_schedtune_task_boost = NULL;
static void *target_schedtune_prefer_idle = NULL;
static void *target_store_bn_test = NULL;
static void *target_dv2_stop = NULL;

// Runtime dynamic state:
// 1 = Active Sentinel (Performance / Extreme gaming boost protection)
// 0 = Standby / Pass-through (Daily Balance mode - stock power efficiency)
static volatile int g_sentinel_enabled = 0; // Default dormant on load
static volatile int g_target_boost = 35;

static int k_str_contains(const char *haystack, const char *needle)
{
    if (!haystack || !needle) return 0;
    while (*haystack) {
        const char *h = haystack;
        const char *n = needle;
        while (*h && *n && (*h == *n)) {
            h++;
            n++;
        }
        if (!*n) return 1;
        haystack++;
    }
    return 0;
}

// 1. Hook for schedtune_task_boost(struct task_struct *p)
void before_schedtune_task_boost(hook_fargs1_t *args, void *udata)
{
}

void after_schedtune_task_boost(hook_fargs1_t *args, void *udata)
{
    // In Daily Balance mode: zero interference, allow cores to idle and sleep freely
    if (!g_sentinel_enabled) return;

    int orig_boost = (int)args->ret;

    // In gaming mode: ensure active tasks maintain minimum boost headroom (prevent demotion)
    if (orig_boost > 0 && orig_boost < g_target_boost) {
        args->ret = (uint64_t)g_target_boost;
    }
}

// 2. Hook for schedtune_prefer_idle(struct task_struct *p)
void before_schedtune_prefer_idle(hook_fargs1_t *args, void *udata)
{
}

void after_schedtune_prefer_idle(hook_fargs1_t *args, void *udata)
{
    if (!g_sentinel_enabled) return;

    if (args->ret == 0 && g_target_boost >= 40) {
        args->ret = 1;
    }
}

// 3. Dynamic Mode Sync: Hook store_BN_TestMode
void before_store_bn_test(hook_fargs4_t *args, void *udata)
{
    const char *buf = (const char *)args->arg2;
    if (buf) {
        if (buf[0] == '1') {
            g_sentinel_enabled = 1;
            logkd(LOG_TAG "Performance/Extreme engaged: EAS Sentinel ARMED (Boost Floor %d%%)\n", g_target_boost);
        } else if (buf[0] == '0') {
            g_sentinel_enabled = 0;
            logkd(LOG_TAG "Balance mode engaged: EAS Sentinel RESTORED (Stock Scheduling & Deep Sleep Active)\n");
        }
    }
}

// 4. Charger disconnect fallback: dv2_stop_algo
void before_dv2_stop(hook_fargs1_t *args, void *udata)
{
    g_sentinel_enabled = 0;
    logkd(LOG_TAG "Charger disconnected: EAS Sentinel restored to stock balance\n");
}

// Module Control (from userspace via kpm ctl0)
static long lynx_eas_sentinel_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;

    if (k_str_contains(args, "balance") || k_str_contains(args, "0") || k_str_contains(args, "disable")) {
        g_sentinel_enabled = 0;
        logkd(LOG_TAG "Control: EAS Sentinel DISABLED (Balance restored, deep sleep enabled)\n");
    } else if (k_str_contains(args, "performance") || k_str_contains(args, "extreme") || k_str_contains(args, "1")) {
        g_sentinel_enabled = 1;
        if (k_str_contains(args, "extreme")) {
            g_target_boost = 50;
        } else {
            g_target_boost = 35;
        }
        logkd(LOG_TAG "Control: EAS Sentinel ARMED (Gaming Mode Active, Boost Floor: %d%%)\n", g_target_boost);
    }

    return 0;
}

// Module Initialization
static long lynx_eas_sentinel_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx EAS Schedtune Priority Sentinel KPM v2.0...\n");

    if (!kallsyms_lookup_name) {
        logke(LOG_TAG "ERROR: kallsyms_lookup_name not available!\n");
        return -1;
    }

    target_schedtune_task_boost = (void *)kallsyms_lookup_name("schedtune_task_boost");
    target_schedtune_prefer_idle = (void *)kallsyms_lookup_name("schedtune_prefer_idle");
    target_store_bn_test = (void *)kallsyms_lookup_name("store_BN_TestMode");
    target_dv2_stop = (void *)kallsyms_lookup_name("dv2_stop_algo");

    if (target_schedtune_task_boost) {
        hook_wrap1(target_schedtune_task_boost, before_schedtune_task_boost, after_schedtune_task_boost, 0);
    }

    if (target_schedtune_prefer_idle) {
        hook_wrap1(target_schedtune_prefer_idle, before_schedtune_prefer_idle, after_schedtune_prefer_idle, 0);
    }

    if (target_store_bn_test) {
        hook_wrap4(target_store_bn_test, before_store_bn_test, NULL, 0);
    }

    if (target_dv2_stop) {
        hook_wrap1(target_dv2_stop, before_dv2_stop, NULL, 0);
    }

    logkd(LOG_TAG "Lynx EAS Sentinel KPM v2.0 armed! Dynamic Balance restoration active.\n");
    return 0;
}

// Module Exit / Unload
static long lynx_eas_sentinel_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx EAS Schedtune Priority Sentinel KPM...\n");

    if (target_schedtune_task_boost) unhook(target_schedtune_task_boost);
    if (target_schedtune_prefer_idle) unhook(target_schedtune_prefer_idle);
    if (target_store_bn_test) unhook(target_store_bn_test);
    if (target_dv2_stop) unhook(target_dv2_stop);

    logkd(LOG_TAG "EAS Sentinel KPM unloaded. 100% Stock scheduler restored.\n");
    return 0;
}

KPM_INIT(lynx_eas_sentinel_init);
KPM_CTL0(lynx_eas_sentinel_control0);
KPM_EXIT(lynx_eas_sentinel_exit);
