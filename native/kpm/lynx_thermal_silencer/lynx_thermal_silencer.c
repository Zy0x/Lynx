/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Thermal Silencer KernelPatch Module (KPM)
 * Target: MediaTek Helio G96 (MT6781) / Linux 4.14+ (ARM64)
 * Purpose: Mitigates aggressive SoC (CPU & GPU) thermal throttling during heavy gaming
 *          while strictly preserving PMIC 115°C emergency hardware cutoffs & battery safety.
 * Framework: KernelPatch / APatch
 *
 * Author: ɴᴏɪʀ
 * License: GPL v2
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_thermal_silencer");
KPM_VERSION("2.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic SoC CPU & GPU Thermal Silencer (Balance Aware)");

#define LOG_TAG "[LynxThermalSilencer] "

// Target kernel symbols
static void *target_mt_ppm_cpu_thermal_protect = NULL;
static void *target_mt_gpufreq_thermal_protect = NULL;
static void *target_cpuhvfs_update_imax_thermal_state = NULL;
static void *target_store_bn_test = NULL;
static void *target_dv2_stop = NULL;

// Runtime state: default dormant (0) until Performance/Extreme is engaged
static volatile int g_silencer_enabled = 0;

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

// 1. Hook for MediaTek mt_ppm_cpu_thermal_protect(unsigned int limited_power)
void before_mt_ppm_cpu_thermal_protect(hook_fargs1_t *args, void *udata)
{
    if (!args) return;

    // In Daily Balance mode: 100% stock OEM thermal throttling, zero extra heat
    if (!g_silencer_enabled) return;

    unsigned int limited_power = (unsigned int)args->arg0;
    if (limited_power > 0) {
        args->arg0 = 0; // Uncapped CPU budget during gaming
    }
}

void after_mt_ppm_cpu_thermal_protect(hook_fargs1_t *args, void *udata)
{
}

// 2. Hook for MediaTek mt_gpufreq_thermal_protect(unsigned int limited_power)
void before_mt_gpufreq_thermal_protect(hook_fargs1_t *args, void *udata)
{
    if (!args) return;
    if (!g_silencer_enabled) return;

    unsigned int limited_power = (unsigned int)args->arg0;
    if (limited_power > 0) {
        args->arg0 = 0; // Uncapped GPU budget during gaming
    }
}

void after_mt_gpufreq_thermal_protect(hook_fargs1_t *args, void *udata)
{
}

// 3. Hook for cpuhvfs_update_imax_thermal_state
void before_cpuhvfs_update_imax_thermal_state(hook_fargs1_t *args, void *udata)
{
    if (!args) return;
    if (!g_silencer_enabled) return;
    args->arg0 = 0;
}

void after_cpuhvfs_update_imax_thermal_state(hook_fargs1_t *args, void *udata)
{
}

// 4. Dynamic Mode Sync: Hook store_BN_TestMode
void before_store_bn_test(hook_fargs4_t *args, void *udata)
{
    if (!args || !args->arg2 || args->arg3 < 1) return;

    const char *buf = (const char *)args->arg2;
    if (buf) {
        if (buf[0] == '1') {
            g_silencer_enabled = 1;
            logkd(LOG_TAG "Performance/Extreme engaged: Thermal Silencer ARMED (SoC Throttling Mitigated)\n");
        } else if (buf[0] == '0') {
            g_silencer_enabled = 0;
            logkd(LOG_TAG "Balance mode engaged: Thermal Silencer RESTORED (100% OEM Protection Active)\n");
        }
    }
}

// 5. Charger disconnect fallback: dv2_stop_algo
void before_dv2_stop(hook_fargs1_t *args, void *udata)
{
    g_silencer_enabled = 0;
    logkd(LOG_TAG "Charger disconnected: Thermal Silencer restored to OEM safe stock\n");
}

// Module Control (from userspace via kpm ctl0)
static long lynx_thermal_silencer_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;

    if (k_str_contains(args, "balance") || k_str_contains(args, "0") || k_str_contains(args, "disable")) {
        g_silencer_enabled = 0;
        logkd(LOG_TAG "Control: Thermal Silencer DISABLED (100% OEM Thermal Stock Restored)\n");
    } else if (k_str_contains(args, "performance") || k_str_contains(args, "extreme") || k_str_contains(args, "1")) {
        g_silencer_enabled = 1;
        logkd(LOG_TAG "Control: Thermal Silencer ARMED (SoC Throttling Mitigated)\n");
    }

    return 0;
}

// Module Initialization
static long lynx_thermal_silencer_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Thermal Silencer KPM v2.0 (Dynamic Balance Aware)...\n");

    if (!kallsyms_lookup_name) {
        logke(LOG_TAG "ERROR: kallsyms_lookup_name not available!\n");
        return -1;
    }

    target_mt_ppm_cpu_thermal_protect = (void *)kallsyms_lookup_name("mt_ppm_cpu_thermal_protect");
    target_mt_gpufreq_thermal_protect = (void *)kallsyms_lookup_name("mt_gpufreq_thermal_protect");
    target_cpuhvfs_update_imax_thermal_state = (void *)kallsyms_lookup_name("cpuhvfs_update_imax_thermal_state");
    target_store_bn_test = (void *)kallsyms_lookup_name("store_BN_TestMode");
    target_dv2_stop = (void *)kallsyms_lookup_name("dv2_stop_algo");

    if (target_mt_ppm_cpu_thermal_protect) {
        hook_wrap1(target_mt_ppm_cpu_thermal_protect, before_mt_ppm_cpu_thermal_protect, after_mt_ppm_cpu_thermal_protect, 0);
    }

    if (target_mt_gpufreq_thermal_protect) {
        hook_wrap1(target_mt_gpufreq_thermal_protect, before_mt_gpufreq_thermal_protect, after_mt_gpufreq_thermal_protect, 0);
    }

    if (target_cpuhvfs_update_imax_thermal_state) {
        hook_wrap1(target_cpuhvfs_update_imax_thermal_state, before_cpuhvfs_update_imax_thermal_state, after_cpuhvfs_update_imax_thermal_state, 0);
    }

    if (target_store_bn_test) {
        hook_wrap4(target_store_bn_test, before_store_bn_test, NULL, 0);
    }

    if (target_dv2_stop) {
        hook_wrap1(target_dv2_stop, before_dv2_stop, NULL, 0);
    }

    logkd(LOG_TAG "Lynx Thermal Silencer KPM v2.0 armed! Dynamic Balance restoration active.\n");
    return 0;
}

// Module Exit / Unload
static long lynx_thermal_silencer_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx Thermal Silencer KPM...\n");

    if (target_mt_ppm_cpu_thermal_protect) unhook(target_mt_ppm_cpu_thermal_protect);
    if (target_mt_gpufreq_thermal_protect) unhook(target_mt_gpufreq_thermal_protect);
    if (target_cpuhvfs_update_imax_thermal_state) unhook(target_cpuhvfs_update_imax_thermal_state);
    if (target_store_bn_test) unhook(target_store_bn_test);
    if (target_dv2_stop) unhook(target_dv2_stop);

    logkd(LOG_TAG "Thermal Silencer KPM unloaded. 100% OEM thermal throttling restored.\n");
    return 0;
}

KPM_INIT(lynx_thermal_silencer_init);
KPM_CTL0(lynx_thermal_silencer_control0);
KPM_EXIT(lynx_thermal_silencer_exit);
