/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - GPU DVFS 950 MHz Peak Floor Lock KernelPatch Module (KPM)
 * Target: MediaTek Helio G96 (MT6781) / ARM Mali-G57 MC2 (Kernel 4.14+)
 * Framework: KernelPatch / APatch
 *
 * Author: ɴᴏɪʀ
 * License: GPL v2
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_gpu_lock");
KPM_VERSION("2.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic GPU DVFS 950 MHz Peak Floor Lock (Balance Aware)");

#define LOG_TAG "[LynxGPULock] "

// Target kernel symbols
static void *target_mt_gpufreq_target = NULL;
static void *target_ged_dvfs_boost_gpu_freq = NULL;
static void *target_store_bn_test = NULL;
static void *target_dv2_stop = NULL;

// Runtime state: default dormant (0) until Performance/Extreme is engaged
#define DEFAULT_LOCK_KHZ 950000
static volatile int g_gpu_lock_enabled = 0;
static volatile unsigned int g_lock_freq_khz = DEFAULT_LOCK_KHZ;

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

// 1. Hook for MediaTek mt_gpufreq_target(unsigned int target_freq, unsigned int relation)
void before_mt_gpufreq_target(hook_fargs2_t *args, void *udata)
{
    // In Daily Balance mode: zero interference, GPU downclocks to 300 MHz freely
    if (!g_gpu_lock_enabled) return;

    unsigned int target = (unsigned int)args->arg0;

    // In Gaming mode: clamp floor up to 950 MHz peak hardware OPP
    if (target < g_lock_freq_khz) {
        args->arg0 = (uint64_t)g_lock_freq_khz;
    }
}

void after_mt_gpufreq_target(hook_fargs2_t *args, void *udata)
{
}

// 2. Hook for MediaTek GED DVFS GPU Boost
void before_ged_dvfs_boost_gpu_freq(hook_fargs1_t *args, void *udata)
{
    if (!g_gpu_lock_enabled) return;

    unsigned int freq = (unsigned int)args->arg0;
    if (freq < g_lock_freq_khz) {
        args->arg0 = (uint64_t)g_lock_freq_khz;
    }
}

void after_ged_dvfs_boost_gpu_freq(hook_fargs1_t *args, void *udata)
{
}

// 3. Dynamic Mode Sync: Hook store_BN_TestMode (called by lynxd/system when mode changes)
void before_store_bn_test(hook_fargs4_t *args, void *udata)
{
    const char *buf = (const char *)args->arg2;
    if (buf) {
        if (buf[0] == '1') {
            g_gpu_lock_enabled = 1;
            logkd(LOG_TAG "Performance/Extreme engaged: GPU Floor Locked to 950 MHz\n");
        } else if (buf[0] == '0') {
            g_gpu_lock_enabled = 0;
            logkd(LOG_TAG "Balance mode engaged: GPU Floor Lock RESTORED to Auto (300 MHz idle)\n");
        }
    }
}

// 4. Charger disconnect fallback: dv2_stop_algo
void before_dv2_stop(hook_fargs1_t *args, void *udata)
{
    g_gpu_lock_enabled = 0;
    logkd(LOG_TAG "Charger disconnected: GPU Floor Lock restored to auto\n");
}

// Module Control (from userspace via kpm ctl0)
static long lynx_gpu_lock_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;

    if (k_str_contains(args, "balance") || k_str_contains(args, "0") || k_str_contains(args, "disable")) {
        g_gpu_lock_enabled = 0;
        logkd(LOG_TAG "Control: GPU Floor Lock DISABLED (Balance restored)\n");
    } else if (k_str_contains(args, "performance") || k_str_contains(args, "extreme") || k_str_contains(args, "1")) {
        g_gpu_lock_enabled = 1;
        if (k_str_contains(args, "850")) {
            g_lock_freq_khz = 850000;
        } else {
            g_lock_freq_khz = DEFAULT_LOCK_KHZ;
        }
        logkd(LOG_TAG "Control: GPU Floor Lock ARMED to %u kHz\n", g_lock_freq_khz);
    }

    return 0;
}

// Module Initialization
static long lynx_gpu_lock_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx GPU DVFS Dynamic Floor Lock KPM v2.0...\n");

    if (!kallsyms_lookup_name) {
        logke(LOG_TAG "ERROR: kallsyms_lookup_name not available!\n");
        return -1;
    }

    target_mt_gpufreq_target = (void *)kallsyms_lookup_name("mt_gpufreq_target");
    target_ged_dvfs_boost_gpu_freq = (void *)kallsyms_lookup_name("ged_dvfs_boost_gpu_freq");
    target_store_bn_test = (void *)kallsyms_lookup_name("store_BN_TestMode");
    target_dv2_stop = (void *)kallsyms_lookup_name("dv2_stop_algo");

    if (target_mt_gpufreq_target) {
        hook_wrap2(target_mt_gpufreq_target, before_mt_gpufreq_target, after_mt_gpufreq_target, 0);
    }

    if (target_ged_dvfs_boost_gpu_freq) {
        hook_wrap1(target_ged_dvfs_boost_gpu_freq, before_ged_dvfs_boost_gpu_freq, after_ged_dvfs_boost_gpu_freq, 0);
    }

    if (target_store_bn_test) {
        hook_wrap4(target_store_bn_test, before_store_bn_test, NULL, 0);
    }

    if (target_dv2_stop) {
        hook_wrap1(target_dv2_stop, before_dv2_stop, NULL, 0);
    }

    logkd(LOG_TAG "Lynx GPU Lock KPM v2.0 armed! Dynamic Balance restoration active.\n");
    return 0;
}

// Module Exit / Unload
static long lynx_gpu_lock_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx GPU DVFS Floor Lock KPM...\n");

    if (target_mt_gpufreq_target) unhook(target_mt_gpufreq_target);
    if (target_ged_dvfs_boost_gpu_freq) unhook(target_ged_dvfs_boost_gpu_freq);
    if (target_store_bn_test) unhook(target_store_bn_test);
    if (target_dv2_stop) unhook(target_dv2_stop);

    logkd(LOG_TAG "GPU DVFS Lock KPM unloaded cleanly. Auto DVFS restored.\n");
    return 0;
}

KPM_INIT(lynx_gpu_lock_init);
KPM_CTL0(lynx_gpu_lock_control0);
KPM_EXIT(lynx_gpu_lock_exit);
