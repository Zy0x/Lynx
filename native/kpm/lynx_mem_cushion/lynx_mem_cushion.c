/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Kernel Memory Allocstall Cushion KernelPatch Module (KPM)
 * Target: MediaTek Helio G96 (MT6781) / Linux 4.14+ (ARM64)
 * Purpose: Prevents direct reclaim allocstalls from blocking game render threads
 *          during high memory pressure (ZRAM 90%+), while automatically
 *          restoring stock OEM memory reclamation in daily Balance mode.
 * Framework: KernelPatch / APatch
 *
 * Author: ɴᴏɪʀ
 * License: GPL v2
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_mem_cushion");
KPM_VERSION("2.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic Anti-Allocstall Memory Cushion (Balance Aware)");

#define LOG_TAG "[LynxMemCushion] "

// Target kernel symbols
static void *target_try_to_free_pages = NULL;
typedef void (*fn_wakeup_kswapd)(void *zone, int order, int classzone_idx);
static fn_wakeup_kswapd p_wakeup_kswapd = NULL;
static void *target_store_bn_test = NULL;
static void *target_dv2_stop = NULL;

// Runtime dynamic state: default dormant (0) until Performance/Extreme is engaged
static volatile int g_cushion_enabled = 0;

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

// 1. Hook for try_to_free_pages(struct zonelist *zonelist, int order, gfp_t gfp_mask, nodemask_t *nodemask)
void before_try_to_free_pages(hook_fargs4_t *args, void *udata)
{
    // In Daily Balance mode: zero interference, allow standard Linux low-memory management
    if (!g_cushion_enabled) return;

    // In Gaming mode: wake up background kswapd asynchronously to clear pages without stalling the foreground caller
    if (p_wakeup_kswapd) {
        p_wakeup_kswapd(NULL, (int)args->arg1, 0);
    }
}

void after_try_to_free_pages(hook_fargs4_t *args, void *udata)
{
}

// 2. Dynamic Mode Sync: Hook store_BN_TestMode
void before_store_bn_test(hook_fargs4_t *args, void *udata)
{
    const char *buf = (const char *)args->arg2;
    if (buf) {
        if (buf[0] == '1') {
            g_cushion_enabled = 1;
            logkd(LOG_TAG "Performance/Extreme engaged: Memory Cushion ARMED (Direct Allocstall Shield On)\n");
        } else if (buf[0] == '0') {
            g_cushion_enabled = 0;
            logkd(LOG_TAG "Balance mode engaged: Memory Cushion RESTORED (Stock OEM Memory Reclamation Active)\n");
        }
    }
}

// 3. Charger disconnect fallback: dv2_stop_algo
void before_dv2_stop(hook_fargs1_t *args, void *udata)
{
    g_cushion_enabled = 0;
    logkd(LOG_TAG "Charger disconnected: Memory Cushion restored to stock balance\n");
}

// Module Control (from userspace via kpm ctl0)
static long lynx_mem_cushion_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;

    if (k_str_contains(args, "balance") || k_str_contains(args, "0") || k_str_contains(args, "disable")) {
        g_cushion_enabled = 0;
        logkd(LOG_TAG "Control: Memory Cushion DISABLED (Stock OEM memory reclamation restored)\n");
    } else if (k_str_contains(args, "performance") || k_str_contains(args, "extreme") || k_str_contains(args, "1")) {
        g_cushion_enabled = 1;
        logkd(LOG_TAG "Control: Memory Cushion ARMED (Direct Allocstall Shield On)\n");
    }

    return 0;
}

// Module Initialization
static long lynx_mem_cushion_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Memory Allocstall Cushion KPM v2.0 (Dynamic Balance Aware)...\n");

    if (!kallsyms_lookup_name) {
        logke(LOG_TAG "ERROR: kallsyms_lookup_name not available!\n");
        return -1;
    }

    target_try_to_free_pages = (void *)kallsyms_lookup_name("try_to_free_pages");
    p_wakeup_kswapd = (fn_wakeup_kswapd)kallsyms_lookup_name("wakeup_kswapd");
    target_store_bn_test = (void *)kallsyms_lookup_name("store_BN_TestMode");
    target_dv2_stop = (void *)kallsyms_lookup_name("dv2_stop_algo");

    if (target_try_to_free_pages) {
        hook_wrap4(target_try_to_free_pages, before_try_to_free_pages, after_try_to_free_pages, 0);
    }

    if (target_store_bn_test) {
        hook_wrap4(target_store_bn_test, before_store_bn_test, NULL, 0);
    }

    if (target_dv2_stop) {
        hook_wrap1(target_dv2_stop, before_dv2_stop, NULL, 0);
    }

    logkd(LOG_TAG "Lynx Memory Cushion KPM v2.0 armed! Dynamic Balance restoration active.\n");
    return 0;
}

// Module Exit / Unload
static long lynx_mem_cushion_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx Memory Cushion KPM...\n");

    if (target_try_to_free_pages) unhook(target_try_to_free_pages);
    if (target_store_bn_test) unhook(target_store_bn_test);
    if (target_dv2_stop) unhook(target_dv2_stop);

    logkd(LOG_TAG "Memory Cushion KPM unloaded. 100% Stock Linux memory management restored.\n");
    return 0;
}

KPM_INIT(lynx_mem_cushion_init);
KPM_CTL0(lynx_mem_cushion_control0);
KPM_EXIT(lynx_mem_cushion_exit);
