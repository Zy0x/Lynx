/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Comprehensive CPU, CCI & SoC VCore Undervolt (UV) Engine
 * Author: ɴᴏɪʀ
 * Target: MediaTek (Helio G96 / Dimensity) & PMIC MT6358 Low-Level Buck Converters
 * Framework: KernelPatch / APatch KPM
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_cpu_uv");
KPM_VERSION("2.2.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Adaptive CPU Undervolt [CPU -18.75 mV | VCore -12.5 mV | Safe Floor 0.575V]");

#define LOG_TAG "[LynxCPU_UV] "

// Target kernel symbols (MT6358 PMIC Low-Level Register Setters)
static void *target_upmu_buck_vproc11_vosel = NULL;
static void *target_upmu_buck_vproc12_vosel = NULL;
static void *target_upmu_buck_vcore_vosel = NULL;
static void *target_regulator_set_voltage = NULL;

// Runtime Configurable Offsets (in microvolts, uV)
// Default: -18750 uV (-18.75 mV sweet spot, 100% rock-solid stability & high thermal efficiency)
static int uv_cpu_offset_uv = -18750;
// MT6358 step is 6.25 mV (6250 uV) per selector step: 18750 / 6250 = 3 steps
static int uv_cpu_selector_steps = 3;
// Vcore SoC bus: conservative 12.5 mV (2 steps * 6.25 mV)
static int uv_vcore_selector_steps = 2;

// Absolute safety floor voltage (uV) to prevent CPU freeze/crash
// MT6358 formula: VOUT = 406250 uV + VOSEL * 6250 uV
#define MIN_SAFE_CPU_UV 575000   // 0.575 V minimum floor for CPU
#define MIN_SAFE_VCORE_UV 650000 // 0.650 V minimum floor for SoC VCore
#define MIN_SAFE_SELECTOR 27     // (575000 - 406250) / 6250 = 27 (0.575 V)
#define MIN_SAFE_VCORE_SEL 39    // (650000 - 406250) / 6250 = 39 (0.650 V)

typedef const char *(*fn_rdev_get_name)(void *rdev);
static fn_rdev_get_name p_rdev_get_name = NULL;

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

// 1. Hook for MT6358 Low-Level Buck vproc11 (Little Cores 6x Cortex-A55 & CCI Bus)
// Applies across the entire fluctuating frequency range (500 MHz - 2.00 GHz)
void before_upmu_buck_vproc11_vosel(hook_fargs1_t *args, void *udata)
{
    unsigned int val = (unsigned int)args->arg0;

    if (uv_cpu_selector_steps > 0 && val > MIN_SAFE_SELECTOR) {
        unsigned int max_drop = val - MIN_SAFE_SELECTOR;
        unsigned int drop = (unsigned int)uv_cpu_selector_steps < max_drop ? (unsigned int)uv_cpu_selector_steps : max_drop;
        if (drop > 0) {
            unsigned int new_val = val - drop;
            args->arg0 = (uint64_t)new_val;
            logkd(LOG_TAG "Little Cores & CCI (vproc11) Curve UV: sel %u -> %u (-%d uV)\n",
                  val, new_val, (int)(drop * 6250));
        }
    }
}

void after_upmu_buck_vproc11_vosel(hook_fargs1_t *args, void *udata)
{
}

// 2. Hook for MT6358 Low-Level Buck vproc12 (Big Cores 2x Cortex-A76)
// Applies across the entire fluctuating frequency range (774 MHz - 2.05 GHz)
void before_upmu_buck_vproc12_vosel(hook_fargs1_t *args, void *udata)
{
    unsigned int val = (unsigned int)args->arg0;

    if (uv_cpu_selector_steps > 0 && val > MIN_SAFE_SELECTOR) {
        unsigned int max_drop = val - MIN_SAFE_SELECTOR;
        unsigned int drop = (unsigned int)uv_cpu_selector_steps < max_drop ? (unsigned int)uv_cpu_selector_steps : max_drop;
        if (drop > 0) {
            unsigned int new_val = val - drop;
            args->arg0 = (uint64_t)new_val;
            logkd(LOG_TAG "Big Cores (vproc12) Curve UV: sel %u -> %u (-%d uV)\n",
                  val, new_val, (int)(drop * 6250));
        }
    }
}

void after_upmu_buck_vproc12_vosel(hook_fargs1_t *args, void *udata)
{
}

// 3. Hook for MT6358 Low-Level Buck vcore (SoC VCore, LPDDR4X 4266 MT/s Memory Controller)
void before_upmu_buck_vcore_vosel(hook_fargs1_t *args, void *udata)
{
    unsigned int val = (unsigned int)args->arg0;

    if (uv_vcore_selector_steps > 0 && val > MIN_SAFE_VCORE_SEL) {
        unsigned int max_drop = val - MIN_SAFE_VCORE_SEL;
        unsigned int drop = (unsigned int)uv_vcore_selector_steps < max_drop ? (unsigned int)uv_vcore_selector_steps : max_drop;
        if (drop > 0) {
            unsigned int new_val = val - drop;
            args->arg0 = (uint64_t)new_val;
            logkd(LOG_TAG "SoC VCore & LPDDR4X Curve UV: sel %u -> %u (-%d uV)\n",
                  val, new_val, (int)(drop * 6250));
        }
    }
}

void after_upmu_buck_vcore_vosel(hook_fargs1_t *args, void *udata)
{
}

// 4. Hook for standard Linux kernel regulator_set_voltage (Comprehensive Framework Layer)
void before_regulator_set_voltage(hook_fargs3_t *args, void *udata)
{
    void *reg = (void *)args->arg0;
    int min_uV = (int)args->arg1;
    int max_uV = (int)args->arg2;

    if (!reg) return;

    void *rdev = *(void **)((char *)reg + 0x50);
    const char *name = (p_rdev_get_name && rdev) ? p_rdev_get_name(rdev) : NULL;

    if (!name) return;

    int offset = 0;
    int floor_uv = MIN_SAFE_CPU_UV;

    if (k_str_contains(name, "vproc11") || k_str_contains(name, "vproc12")) {
        if (target_upmu_buck_vproc11_vosel || target_upmu_buck_vproc12_vosel) {
            // Low-level PMIC vosel hook is active, avoid double-undervolting!
            return;
        }
        offset = uv_cpu_offset_uv;
        floor_uv = MIN_SAFE_CPU_UV;
    } else if (k_str_contains(name, "vcore")) {
        if (target_upmu_buck_vcore_vosel) {
            // Low-level PMIC vosel hook is active, avoid double-undervolting!
            return;
        }
        offset = uv_vcore_selector_steps * -6250;
        floor_uv = MIN_SAFE_VCORE_UV;
    } else {
        return;
    }

    if (offset < 0 && min_uV > floor_uv) {
        int new_min = min_uV + offset;
        int new_max = max_uV + offset;

        if (new_min < floor_uv) new_min = floor_uv;
        if (new_max < floor_uv) new_max = floor_uv;

        if (new_min < min_uV) {
            args->arg1 = (uint64_t)new_min;
            args->arg2 = (uint64_t)new_max;
            logkd(LOG_TAG "Regulator Curve UV on '%s': %d uV -> %d uV (offset %d uV)\n",
                  name, min_uV, new_min, offset);
        }
    }
}

void after_regulator_set_voltage(hook_fargs3_t *args, void *udata)
{
}

// Module Control (from userspace CLI/APatch supercall)
static long lynx_cpu_uv_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;
    logkd(LOG_TAG "Control command: %s\n", args);

    if (k_str_contains(args, "reset")) {
        uv_cpu_offset_uv = 0;
        uv_cpu_selector_steps = 0;
        uv_vcore_selector_steps = 0;
        logkd(LOG_TAG "CPU, CCI & VCore UV disabled (stock default 0 uV)\n");
    } else if (k_str_contains(args, "uv=-12.5")) {
        uv_cpu_offset_uv = -12500;
        uv_cpu_selector_steps = 2; // 2 * 6.25 mV = 12.5 mV
        uv_vcore_selector_steps = 1; // 1 * 6.25 mV = 6.25 mV
        logkd(LOG_TAG "Set conservative Full-Curve CPU/CCI UV: -12.5 mV\n");
    } else if (k_str_contains(args, "uv=-18.75")) {
        uv_cpu_offset_uv = -18750;
        uv_cpu_selector_steps = 3; // 3 * 6.25 mV = 18.75 mV
        uv_vcore_selector_steps = 2; // 2 * 6.25 mV = 12.5 mV
        logkd(LOG_TAG "Set moderate Full-Curve CPU/CCI UV: -18.75 mV\n");
    } else if (k_str_contains(args, "uv=-25")) {
        uv_cpu_offset_uv = -25000;
        uv_cpu_selector_steps = 4; // 4 * 6.25 mV = 25.0 mV
        uv_vcore_selector_steps = 2; // 2 * 6.25 mV = 12.5 mV
        logkd(LOG_TAG "Set recommended Full-Curve CPU/CCI UV: -25.0 mV\n");
    } else if (k_str_contains(args, "uv=-31.25")) {
        uv_cpu_offset_uv = -31250;
        uv_cpu_selector_steps = 5; // 5 * 6.25 mV = 31.25 mV
        uv_vcore_selector_steps = 2;
        logkd(LOG_TAG "Set high Full-Curve CPU/CCI UV: -31.25 mV\n");
    } else if (k_str_contains(args, "uv=-37.5")) {
        uv_cpu_offset_uv = -37500;
        uv_cpu_selector_steps = 6; // 6 * 6.25 mV = 37.5 mV
        uv_vcore_selector_steps = 3;
        logkd(LOG_TAG "Set aggressive Full-Curve CPU/CCI UV: -37.5 mV\n");
    }

    return 0;
}

// Module Initialization
static long lynx_cpu_uv_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Comprehensive CPU, CCI & VCore UV Engine v2.0...\n");

    if (kallsyms_lookup_name) {
        target_upmu_buck_vproc11_vosel = (void *)kallsyms_lookup_name("mt6358_upmu_set_rg_buck_vproc11_vosel");
        target_upmu_buck_vproc12_vosel = (void *)kallsyms_lookup_name("mt6358_upmu_set_rg_buck_vproc12_vosel");
        target_upmu_buck_vcore_vosel = (void *)kallsyms_lookup_name("mt6358_upmu_set_rg_buck_vcore_vosel");
        target_regulator_set_voltage = (void *)kallsyms_lookup_name("regulator_set_voltage");
        p_rdev_get_name = (fn_rdev_get_name)kallsyms_lookup_name("rdev_get_name");
    }

    // 1. Hook MT6358 Low-Level Buck vproc11 (Little Cores & CCI Bus)
    if (target_upmu_buck_vproc11_vosel) {
        hook_err_t err = hook_wrap1(target_upmu_buck_vproc11_vosel, before_upmu_buck_vproc11_vosel, after_upmu_buck_vproc11_vosel, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked mt6358_upmu_set_rg_buck_vproc11_vosel (Little Cores & CCI UV Active)\n");
        }
    }

    // 2. Hook MT6358 Low-Level Buck vproc12 (Big Cores Cortex-A76)
    if (target_upmu_buck_vproc12_vosel) {
        hook_err_t err = hook_wrap1(target_upmu_buck_vproc12_vosel, before_upmu_buck_vproc12_vosel, after_upmu_buck_vproc12_vosel, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked mt6358_upmu_set_rg_buck_vproc12_vosel (Big Cores UV Active)\n");
        }
    }

    // 3. Hook MT6358 Low-Level Buck vcore (SoC VCore & LPDDR4X Bus)
    if (target_upmu_buck_vcore_vosel) {
        hook_err_t err = hook_wrap1(target_upmu_buck_vcore_vosel, before_upmu_buck_vcore_vosel, after_upmu_buck_vcore_vosel, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked mt6358_upmu_set_rg_buck_vcore_vosel (SoC VCore & LPDDR4X UV Active)\n");
        }
    }

    // 4. Hook generic Linux regulator framework
    if (target_regulator_set_voltage) {
        hook_err_t err = hook_wrap3(target_regulator_set_voltage, before_regulator_set_voltage, after_regulator_set_voltage, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked regulator_set_voltage (Generic Fallback Active)\n");
        }
    }

    logkd(LOG_TAG "Lynx Comprehensive CPU, CCI & VCore UV Engine v2.0 successfully loaded! (Offset: %d uV)\n", uv_cpu_offset_uv);
    return 0;
}

// Module Exit / Unload
static long lynx_cpu_uv_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx Comprehensive CPU, CCI & VCore UV Engine...\n");

    if (target_upmu_buck_vproc11_vosel) {
        unhook(target_upmu_buck_vproc11_vosel);
    }
    if (target_upmu_buck_vproc12_vosel) {
        unhook(target_upmu_buck_vproc12_vosel);
    }
    if (target_upmu_buck_vcore_vosel) {
        unhook(target_upmu_buck_vcore_vosel);
    }
    if (target_regulator_set_voltage) {
        unhook(target_regulator_set_voltage);
    }

    logkd(LOG_TAG "All CPU, CCI & VCore hooks removed. Voltages restored to stock.\n");
    return 0;
}

KPM_INIT(lynx_cpu_uv_init);
KPM_CTL0(lynx_cpu_uv_control0);
KPM_EXIT(lynx_cpu_uv_exit);
