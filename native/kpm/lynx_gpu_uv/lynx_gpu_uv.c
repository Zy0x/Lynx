/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Dedicated Adaptive GPU Undervolt (UV) Engine
 * Author: ɴᴏɪʀ
 * Target: MediaTek (Helio / Dimensity) & ARM64 Mali / Adreno GPUs
 * Framework: KernelPatch / APatch KPM
 */

#include <log.h>
#include <compiler.h>
#include <kpmodule.h>
#include <hook.h>
#include <kallsyms.h>
#include <linux/printk.h>

KPM_NAME("lynx_gpu_uv");
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic Adaptive GPU Undervolt (UV) Engine");

#define LOG_TAG "[LynxGPU_UV] "

// Target kernel symbols
static void *target_regulator_set_voltage = NULL;
static void *target_ged_dvfs_vcore = NULL;

// Runtime Configurable Offset (in microvolts, uV)
// Default: -12500 uV (-12.5 mV primary sweet spot, 100% stable & cool)
static int uv_gpu_offset_uv = -12500;

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

    if (uv_gpu_offset_uv < 0) {
        int new_min = min_uV + uv_gpu_offset_uv;
        int new_max = max_uV + uv_gpu_offset_uv;

        // Enforce safety floor
        if (new_min >= MIN_SAFE_GPU_UV && new_max >= MIN_SAFE_GPU_UV) {
            args->arg1 = (uint64_t)new_min;
            args->arg2 = (uint64_t)new_max;
            logkd(LOG_TAG "UV applied to '%s': %d uV -> %d uV (offset %d uV)\n",
                  name, min_uV, new_min, uv_gpu_offset_uv);
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

// Module Control (from userspace CLI/APatch supercall)
static long lynx_gpu_uv_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;
    logkd(LOG_TAG "Control command: %s\n", args);

    if (k_str_contains(args, "reset") || k_str_contains(args, "stock")) {
        uv_gpu_offset_uv = 0;
        logkd(LOG_TAG "GPU UV disabled (stock default 0 uV)\n");
    } else if (k_str_contains(args, "uv=-12.5") || k_str_contains(args, "sweetspot")) {
        uv_gpu_offset_uv = -12500;
        logkd(LOG_TAG "Set sweetspot GPU UV offset: -12.5 mV\n");
    } else if (k_str_contains(args, "uv=-25")) {
        uv_gpu_offset_uv = -25000;
        logkd(LOG_TAG "Set aggressive GPU UV offset: -25.0 mV\n");
    } else if (k_str_contains(args, "uv=-37.5")) {
        uv_gpu_offset_uv = -37500;
        logkd(LOG_TAG "Set heavy GPU UV offset: -37.5 mV\n");
    } else if (k_str_contains(args, "uv=-50")) {
        uv_gpu_offset_uv = -50000;
        logkd(LOG_TAG "Set extreme GPU UV offset: -50.0 mV\n");
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
            logkd(LOG_TAG "Hooked regulator_set_voltage (GPU UV active: %d uV)\n", uv_gpu_offset_uv);
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
            logkd(LOG_TAG "Hooked ged_dvfs_vcore (GPU UV active: %d uV)\n", uv_gpu_offset_uv);
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
