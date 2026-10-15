/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - TPCB Thermal Unthrottle KernelPatch Module (KPM)
 * Author: ɴᴏɪʀ
 * Target: MediaTek / Transsion Helio G96 (Kernel 4.14+)
 * Framework: KernelPatch / APatch
 */

#include <log.h>
#include <compiler.h>
#include <kpmodule.h>
#include <hook.h>
#include <linux/printk.h>

KPM_NAME("lynx_thermal_unthrottle");
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic TPCB Thermal Override for Direct Charge");

#define LOG_TAG "[LynxKPM] "

// Symbols to hook dynamically
static void *target_dv2_check_thermal = NULL;

// Maximum allowed physical PCB temperature before emergency safety fallback (degrees Celsius)
#define HARD_MAX_SAFE_PCB_TEMP 52

// Before Hook for __dv2_check_thermal_level
void before_dv2_check(hook_fargs3_t *args, void *udata)
{
    // args->arg0: name/pointer
    // args->arg1: current temperature
    // args->arg2: level pointer or mode
}

// After Hook for __dv2_check_thermal_level
void after_dv2_check(hook_fargs3_t *args, void *udata)
{
    int orig_level = (int)args->ret;

    // If kernel decided to throttle charging current (Level > 0)
    if (orig_level > 0) {
        // Temperature safety check:
        // args->arg1 contains the current temperature in degrees C
        int temp = (int)args->arg1;
        if (temp > 0 && temp < HARD_MAX_SAFE_PCB_TEMP) {
            logkd(LOG_TAG "Overriding thermal level %d -> 0 (temp=%d C < %d C safe limit)\n",
                  orig_level, temp, HARD_MAX_SAFE_PCB_TEMP);
            args->ret = 0; // Force Level 0 (Full unthrottled fast charging)
        } else if (temp >= HARD_MAX_SAFE_PCB_TEMP) {
            logkd(LOG_TAG "Emergency PCB safety threshold reached (temp=%d C >= %d C), allowing hardware throttle (level %d)\n",
                  temp, HARD_MAX_SAFE_PCB_TEMP, orig_level);
        }
    }
}

// Module Initialization
static long lynx_thermal_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Thermal Unthrottle KPM...\n");

    // Dynamic symbol resolution using KernelPatch internal kallsyms
    target_dv2_check_thermal = (void *)kallsyms_lookup_name_by_suffix("__dv2_check_thermal_level");
    if (!target_dv2_check_thermal) {
        target_dv2_check_thermal = (void *)kallsyms_lookup_name_by_suffix("dv2_check_thermal_level");
    }

    if (target_dv2_check_thermal) {
        logkd(LOG_TAG "Found __dv2_check_thermal_level at %px\n", target_dv2_check_thermal);
        hook_err_t err = hook_wrap3(target_dv2_check_thermal, before_dv2_check, after_dv2_check, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Successfully installed inline hook on __dv2_check_thermal_level!\n");
        } else {
            logke(LOG_TAG "Failed to hook __dv2_check_thermal_level, error: %d\n", err);
        }
    } else {
        logke(LOG_TAG "Symbol __dv2_check_thermal_level not found in kallsyms\n");
    }

    return 0;
}

// Module Control (from userspace)
static long lynx_thermal_control0(const char *args, char *__user out_msg, int outlen)
{
    logkd(LOG_TAG "Control message received: %s\n", args ? args : "NULL");
    return 0;
}

// Module Exit / Unload
static long lynx_thermal_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx Thermal Unthrottle KPM...\n");
    if (target_dv2_check_thermal) {
        unhook(target_dv2_check_thermal);
        logkd(LOG_TAG "Unhooked __dv2_check_thermal_level successfully.\n");
    }
    return 0;
}

KPM_INIT(lynx_thermal_init);
KPM_CTL0(lynx_thermal_control0);
KPM_EXIT(lynx_thermal_exit);
