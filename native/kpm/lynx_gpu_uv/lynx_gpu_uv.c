/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Dedicated Adaptive GPU Undervolt (UV) Engine
 * Author: ɴᴏɪʀ
 * Target: MediaTek (Helio / Dimensity) & ARM64 Mali / Adreno GPUs
 * Framework: KernelPatch / APatch KPM
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_gpu_uv");
KPM_VERSION("1.2.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Adaptive GPU Undervolt [Tiered: Peak -12.5 mV | Mid/Low -25.0 mV | Floor 0.55V]");

#define LOG_TAG "[LynxGPU_UV] "

// Target kernel symbols
static void *target_regulator_set_voltage = NULL;
static void *target_ged_dvfs_vcore = NULL;

// Operating Modes:
// 0 = Tiered Curve (Recommended): Peak (>=840mV) @ -12.5 mV, Mid/Low (<840mV) @ -25.0 mV
// 1 = Flat Offset across all OPP states
// 2 = Disabled (Stock OEM voltages)
#define UV_MODE_TIERED   0
#define UV_MODE_FLAT     1
#define UV_MODE_DISABLED 2

static int g_uv_mode = UV_MODE_TIERED;

// Tiered Configuration:
static int uv_peak_offset_uv = -12500; // -12.5 mV for >= 840 mV (930-950 MHz)
static int uv_mid_offset_uv  = -25000; // -25.0 mV for < 840 mV (300-920 MHz)

// Flat Configuration (when g_uv_mode == UV_MODE_FLAT):
static int uv_flat_offset_uv = -12500;

// Rail Targeting Control:
// 0 = ONLY vgpu (Core Logic undervolt, vsram kept at 100% stock for rock-solid stability)
// 1 = Dual-rail (both vgpu and vsram_gpu undervolted)
static int uv_target_vsram = 0;

// Absolute safety floor voltage (uV) to prevent GPU freeze/crash
#define MIN_SAFE_GPU_UV 550000 // 0.55 V minimum floor

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

static inline int resolve_offset_for_uv(int base_uV)
{
    if (g_uv_mode == UV_MODE_DISABLED) return 0;
    if (g_uv_mode == UV_MODE_FLAT) return uv_flat_offset_uv;

    // UV_MODE_TIERED:
    // Peak OPPs (930 - 950 MHz, base >= 840000 uV): -12.5 mV for 100% rock-solid combat stability
    // Mid & Low OPPs (300 - 920 MHz, base < 840000 uV): -25.0 mV for deep thermal & power savings
    if (base_uV >= 840000) {
        return uv_peak_offset_uv;
    } else {
        return uv_mid_offset_uv;
    }
}

// Before Hook for regulator_set_voltage(struct regulator *reg, int min_uV, int max_uV)
void before_regulator_set_voltage(hook_fargs3_t *args, void *udata)
{
    void *reg = (void *)args->arg0;
    int min_uV = (int)args->arg1;
    int max_uV = (int)args->arg2;

    if (!reg) return;

    // rdev is at offset 0x50 in struct regulator (ARM64 standard)
    void *rdev = *(void **)((char *)reg + 0x50);
    const char *name = (p_rdev_get_name && rdev) ? p_rdev_get_name(rdev) : NULL;

    // Target ONLY GPU power rails:
    // vgpu is always targeted. vsram_gpu is targeted ONLY if uv_target_vsram is explicitly enabled!
    if (!name) return;

    int is_vgpu = k_str_contains(name, "vgpu") && !k_str_contains(name, "vsram");
    int is_vsram = k_str_contains(name, "vsram");

    if (!is_vgpu && !(is_vsram && uv_target_vsram)) {
        return;
    }

    int offset = resolve_offset_for_uv(min_uV);
    if (offset < 0) {
        int new_min = min_uV + offset;
        int new_max = max_uV + offset;

        // Enforce safety floor
        if (new_min >= MIN_SAFE_GPU_UV && new_max >= MIN_SAFE_GPU_UV) {
            args->arg1 = (uint64_t)new_min;
            args->arg2 = (uint64_t)new_max;
            logkd(LOG_TAG "UV applied to '%s': %d uV -> %d uV (offset %d uV, %s)\n",
                  name, min_uV, new_min, offset,
                  (min_uV >= 840000) ? "Peak Tier" : "Mid/Low Tier");
        }
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
    if (vcore > 60000) {
        int base_uV = vcore * 10;
        int offset_uv = resolve_offset_for_uv(base_uV);
        if (offset_uv < 0) {
            int offset_step = offset_uv / 1000; // convert uV to mV
            int new_vcore = vcore + (offset_step * 100);
            if (new_vcore >= (MIN_SAFE_GPU_UV / 10)) {
                args->arg0 = (uint64_t)new_vcore;
                logkd(LOG_TAG "ged_dvfs_vcore: %d -> %d (offset %d uV)\n",
                      vcore, new_vcore, offset_uv);
            }
        }
    }
}

void after_ged_dvfs_vcore(hook_fargs1_t *args, void *udata)
{
}

// Module Control (from userspace CLI/APatch supercall)
static long lynx_gpu_uv_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;
    logkd(LOG_TAG "Control command: %s\n", args);

    if (k_str_contains(args, "reset") || k_str_contains(args, "stock")) {
        g_uv_mode = UV_MODE_DISABLED;
        logkd(LOG_TAG "GPU UV disabled (stock default 0 uV)\n");
    } else if (k_str_contains(args, "tiered") || k_str_contains(args, "auto")) {
        g_uv_mode = UV_MODE_TIERED;
        uv_peak_offset_uv = -12500;
        uv_mid_offset_uv = -25000;
        logkd(LOG_TAG "GPU UV mode: Tiered Curve (Peak -12.5 mV | Mid/Low -25.0 mV)\n");
    } else if (k_str_contains(args, "flat12.5") || k_str_contains(args, "flat_12.5") || k_str_contains(args, "uv=-12.5")) {
        g_uv_mode = UV_MODE_FLAT;
        uv_flat_offset_uv = -12500;
        logkd(LOG_TAG "GPU UV mode: Flat -12.5 mV across entire table\n");
    } else if (k_str_contains(args, "flat25") || k_str_contains(args, "flat_25") || k_str_contains(args, "uv=-25")) {
        g_uv_mode = UV_MODE_FLAT;
        uv_flat_offset_uv = -25000;
        logkd(LOG_TAG "GPU UV mode: Flat -25.0 mV across entire table\n");
    }

    if (k_str_contains(args, "vsram=off") || k_str_contains(args, "core_only")) {
        uv_target_vsram = 0;
        logkd(LOG_TAG "Targeting ONLY vgpu (vsram kept at stock)\n");
    } else if (k_str_contains(args, "vsram=on") || k_str_contains(args, "dual_rail")) {
        uv_target_vsram = 1;
        logkd(LOG_TAG "Targeting dual rails (vgpu + vsram)\n");
    }

    return 0;
}

// Module Initialization
static long lynx_gpu_uv_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Dedicated GPU UV Engine...\n");

    // 1. Hook regulator_set_voltage
    if (kallsyms_lookup_name) {
        target_regulator_set_voltage = (void *)kallsyms_lookup_name("regulator_set_voltage");
        p_rdev_get_name = (fn_rdev_get_name)kallsyms_lookup_name("rdev_get_name");
    }
    if (target_regulator_set_voltage) {
        hook_err_t err = hook_wrap3(target_regulator_set_voltage, before_regulator_set_voltage, after_regulator_set_voltage, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked regulator_set_voltage (Tiered UV: Peak %d uV, Mid %d uV)\n",
                  uv_peak_offset_uv, uv_mid_offset_uv);
        } else {
            logke(LOG_TAG "Failed to hook regulator_set_voltage, err=%d\n", err);
        }
    }

    // 2. Hook ged_dvfs_vcore
    if (kallsyms_lookup_name) {
        target_ged_dvfs_vcore = (void *)kallsyms_lookup_name("ged_dvfs_vcore");
    }
    if (target_ged_dvfs_vcore) {
        hook_err_t err = hook_wrap1(target_ged_dvfs_vcore, before_ged_dvfs_vcore, after_ged_dvfs_vcore, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked ged_dvfs_vcore (Tiered UV: Peak %d uV, Mid %d uV)\n",
                  uv_peak_offset_uv, uv_mid_offset_uv);
        }
    }

    logkd(LOG_TAG "Lynx Dedicated GPU UV Engine successfully loaded!\n");
    return 0;
}

// Module Exit / Unload
static long lynx_gpu_uv_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx Dedicated GPU UV Engine...\n");

    if (target_regulator_set_voltage) {
        unhook(target_regulator_set_voltage);
    }
    if (target_ged_dvfs_vcore) {
        unhook(target_ged_dvfs_vcore);
    }

    logkd(LOG_TAG "GPU hooks removed. GPU voltages restored to stock.\n");
    return 0;
}

KPM_INIT(lynx_gpu_uv_init);
KPM_CTL0(lynx_gpu_uv_control0);
KPM_EXIT(lynx_gpu_uv_exit);
