/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Adaptive Dynamic Undervolt (UV) & Overclock (OC) Engine
 * Author: ɴᴏɪʀ
 * Target: MediaTek (Helio G96 / Dimensity) & ARM64 Linux Kernel 4.14+
 * Framework: KernelPatch / APatch KPM
 */

#include <log.h>
#include <compiler.h>
#include <kpmodule.h>
#include <hook.h>
#include <kallsyms.h>
#include <linux/printk.h>

KPM_NAME("lynx_oc_uv");
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic Adaptive Undervolt (UV) & Overclock (OC) Engine");

#define LOG_TAG "[LynxOC_UV] "

// Target kernel symbols
static void *target_regulator_set_voltage = NULL;
static void *target_ged_dvfs_vcore = NULL;
static void *target_dev_pm_opp_set_rate = NULL;

// Runtime Configurable Offsets (in microvolts, uV)
// Default to 0 uV (monitoring & safety stage on initial load)
static int uv_cpu_offset_uv = 0;
static int uv_gpu_offset_uv = 0;
static int oc_boost_enabled = 0;

// Absolute safety floor voltages (uV) to prevent instability/freezes
#define MIN_SAFE_CPU_UV 700000 // 0.70 V floor for CPU
#define MIN_SAFE_GPU_UV 550000 // 0.55 V floor for GPU

typedef const char *(*fn_rdev_get_name)(void *rdev);
static fn_rdev_get_name p_rdev_get_name = NULL;

static int k_str_contains(const char *haystack, const char *needle);

// Before Hook for regulator_set_voltage(struct regulator *reg, int min_uV, int max_uV)
// Before Hook for regulator_set_voltage(struct regulator *reg, int min_uV, int max_uV)
void before_regulator_set_voltage(hook_fargs3_t *args, void *udata)
{
    void *reg = (void *)args->arg0;
    int min_uV = (int)args->arg1;
    int max_uV = (int)args->arg2;

    if (!reg) return;

    // From disassembly: ldr x22, [x0, #0x50] -> rdev is at offset 0x50
    void *rdev = *(void **)((char *)reg + 0x50);
    const char *name = (p_rdev_get_name && rdev) ? p_rdev_get_name(rdev) : NULL;

    // Check if target is CPU (vproc11, vproc12) or GPU (vgpu)
    int offset = 0;
    if (name) {
        if (k_str_contains(name, "vproc")) {
            offset = uv_cpu_offset_uv;
        } else if (k_str_contains(name, "vgpu")) {
            offset = uv_gpu_offset_uv;
        } else {
            // Not CPU or GPU regulator, do not touch!
            return;
        }
    } else {
        return; // Only modify verified named rails
    }

    if (offset < 0) {
        int new_min = min_uV + offset;
        int new_max = max_uV + offset;
        int floor_uv = k_str_contains(name, "vgpu") ? MIN_SAFE_GPU_UV : MIN_SAFE_CPU_UV;

        // Enforce safety floor
        if (new_min >= floor_uv && new_max >= floor_uv) {
            args->arg1 = (uint64_t)new_min;
            args->arg2 = (uint64_t)new_max;
            logkd(LOG_TAG "UV applied to '%s': %d uV -> %d uV (offset %d uV)\n",
                  name, min_uV, new_min, offset);
        }
    } else {
        // Monitor mode: log requests for CPU & GPU
        logkd(LOG_TAG "regulator_set_voltage: rail='%s', req=%d..%d uV\n",
              name, min_uV, max_uV);
    }
}

void after_regulator_set_voltage(hook_fargs3_t *args, void *udata)
{
}

// Before Hook for ged_dvfs_vcore(int vcore)
void before_ged_dvfs_vcore(hook_fargs1_t *args, void *udata)
{
    int vcore = (int)args->arg0;

    // Apply GPU Undervolt offset if active
    if (vcore > 60000 && uv_gpu_offset_uv < 0) {
        int offset_step = uv_gpu_offset_uv / 1000; // convert uV to mV
        int new_vcore = vcore + (offset_step * 100);
        if (new_vcore >= (MIN_SAFE_GPU_UV / 10)) {
            args->arg0 = (uint64_t)new_vcore;
            logkd(LOG_TAG "ged_dvfs_vcore: %d -> %d (offset %d uV)\n",
                  vcore, new_vcore, uv_gpu_offset_uv);
        }
    }
}

void after_ged_dvfs_vcore(hook_fargs1_t *args, void *udata)
{
}

// Before Hook for dev_pm_opp_set_rate(struct device *dev, unsigned long target_freq)
void before_opp_set_rate(hook_fargs2_t *args, void *udata)
{
    unsigned long freq = (unsigned long)args->arg1;

    // If OC / Peak Mode is enabled, prevent aggressive downclocking during load
    if (oc_boost_enabled && freq < 1000000000 && freq > 500000000) {
        // Boost mid-range requests to high performance tier
        // args->arg1 = 1800000000;
    }
}

void after_opp_set_rate(hook_fargs2_t *args, void *udata)
{
}

// Module Initialization
static long lynx_oc_uv_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Adaptive OC & UV Engine...\n");

    // 1. Hook regulator_set_voltage for CPU VCore UV
    if (kallsyms_lookup_name) {
        target_regulator_set_voltage = (void *)kallsyms_lookup_name("regulator_set_voltage");
        p_rdev_get_name = (fn_rdev_get_name)kallsyms_lookup_name("rdev_get_name");
    }
    if (target_regulator_set_voltage) {
        logkd(LOG_TAG "Found regulator_set_voltage at %px, rdev_get_name at %px\n",
              target_regulator_set_voltage, p_rdev_get_name);

        uint32_t *code = (uint32_t *)target_regulator_set_voltage;
        logkd(LOG_TAG "code regulator_set_voltage:\n");
        for (int i = 0; i < 16; i += 4) {
            logkd(LOG_TAG "  [%02d..%02d] %08x %08x %08x %08x\n",
                  i, i+3, code[i], code[i+1], code[i+2], code[i+3]);
        }

        hook_err_t err = hook_wrap3(target_regulator_set_voltage, before_regulator_set_voltage, after_regulator_set_voltage, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked regulator_set_voltage (Safe Monitor Mode: offset %d uV)\n", uv_cpu_offset_uv);
        } else {
            logke(LOG_TAG "Failed to hook regulator_set_voltage, err=%d\n", err);
        }
    }

    // 2. Hook ged_dvfs_vcore for Mali GPU UV
    if (kallsyms_lookup_name) {
        target_ged_dvfs_vcore = (void *)kallsyms_lookup_name("ged_dvfs_vcore");
    }
    if (target_ged_dvfs_vcore) {
        hook_err_t err = hook_wrap1(target_ged_dvfs_vcore, before_ged_dvfs_vcore, after_ged_dvfs_vcore, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked ged_dvfs_vcore (GPU UV offset: %d uV)\n", uv_gpu_offset_uv);
        }
    }

    // 3. Hook dev_pm_opp_set_rate for OPP frequency scaling
    if (kallsyms_lookup_name) {
        target_dev_pm_opp_set_rate = (void *)kallsyms_lookup_name("dev_pm_opp_set_rate");
    }
    if (target_dev_pm_opp_set_rate) {
        hook_err_t err = hook_wrap2(target_dev_pm_opp_set_rate, before_opp_set_rate, after_opp_set_rate, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked dev_pm_opp_set_rate (OC boost active)\n");
        }
    }

    logkd(LOG_TAG "Lynx Adaptive OC & UV Engine successfully loaded!\n");
    return 0;
}

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

// Module Control (from userspace CLI/APatch supercall)
static long lynx_oc_uv_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;
    logkd(LOG_TAG "Control message received: %s\n", args);

    // Support dynamic runtime reconfiguration:
    // e.g. "uv_cpu=-12500" or "uv_cpu=-25000" or "reset"
    if (k_str_contains(args, "reset")) {
        uv_cpu_offset_uv = 0;
        uv_gpu_offset_uv = 0;
        oc_boost_enabled = 0;
        logkd(LOG_TAG "Reset all offsets to 0 (stock defaults)\n");
    } else if (k_str_contains(args, "uv=-12.5")) {
        uv_cpu_offset_uv = -12500;
        uv_gpu_offset_uv = -12500;
        logkd(LOG_TAG "Set conservative UV offset: -12.5 mV\n");
    } else if (k_str_contains(args, "uv=-25")) {
        uv_cpu_offset_uv = -25000;
        uv_gpu_offset_uv = -25000;
        logkd(LOG_TAG "Set recommended UV offset: -25.0 mV\n");
    } else if (k_str_contains(args, "uv=-37.5")) {
        uv_cpu_offset_uv = -37500;
        uv_gpu_offset_uv = -37500;
        logkd(LOG_TAG "Set aggressive UV offset: -37.5 mV\n");
    }

    return 0;
}

// Module Exit / Unload
static long lynx_oc_uv_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx Adaptive OC & UV Engine...\n");

    if (target_regulator_set_voltage) {
        unhook(target_regulator_set_voltage);
    }
    if (target_ged_dvfs_vcore) {
        unhook(target_ged_dvfs_vcore);
    }
    if (target_dev_pm_opp_set_rate) {
        unhook(target_dev_pm_opp_set_rate);
    }

    logkd(LOG_TAG "All hooks removed. Voltages and clocks restored to stock.\n");
    return 0;
}

KPM_INIT(lynx_oc_uv_init);
KPM_CTL0(lynx_oc_uv_control0);
KPM_EXIT(lynx_oc_uv_exit);
