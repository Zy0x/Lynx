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
#include <kallsyms.h>
#include <linux/printk.h>

KPM_NAME("lynx_thermal_unthrottle");
KPM_VERSION("2.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic TPCB & Multi-Domain Thermal Override for Direct Charge");

#define LOG_TAG "[LynxKPM] "

// Master state flag: 1 = Active (Hyper Charging engaged), 0 = Inactive / OEM Stock
static volatile int g_hyper_enabled = 1;

// Hook targets
static void *target_dv2_check_thermal = NULL;
static void *target_dv2_stop = NULL;
static void *target_store_bn_test = NULL;

// Custom string comparison (zero external dependency)
static int k_strcmp(const char *s1, const char *s2)
{
    if (!s1 || !s2) return -1;
    while (*s1 && (*s1 == *s2)) {
        s1++;
        s2++;
    }
    return *(const unsigned char *)s1 - *(const unsigned char *)s2;
}

// Domain-aware safety ceilings
static int get_max_safe_temp(const char *name)
{
    if (!name) return 55;
    if (k_strcmp(name, "tbat") == 0) {
        // Battery cell: STRICT safety limit (prevent lithium plating / runaway)
        return 45;
    } else if (k_strcmp(name, "tpcb") == 0) {
        // Motherboard PCB: elevated to 58°C (safe component threshold is 60°C)
        return 58;
    } else if (k_strcmp(name, "tdvchg_master") == 0 || k_strcmp(name, "tdvchg") == 0) {
        // RT9759 Direct Charge Pump IC: elevated to 68°C (silicon OTP is 85°C)
        return 68;
    } else if (k_strcmp(name, "tta") == 0) {
        // Travel Adapter / cable tip: elevated to 65°C (Transsion OEM derating is 70°C)
        return 65;
    } else if (k_strcmp(name, "tusb") == 0) {
        // USB-C Connector: safe limit 52°C (prevent connector damage)
        return 52;
    }
    return 55;
}

// Before Hook for __dv2_check_thermal_level
// Signature: int __dv2_check_thermal_level(struct dv2_algo_info *info, struct dv2_thermal_sensor *sensor)
void before_dv2_check(hook_fargs3_t *args, void *udata)
{
    uint64_t sensor = args->arg1;
    args->local.data[0] = 0;
    args->local.data[1] = sensor;
    args->local.data[2] = 0; // max_safe_temp

    // Only engage if Hyper Charging mode is actively requested
    if (!g_hyper_enabled) {
        return;
    }

    if (sensor > 0xffffff8000000000ULL && ((sensor & 7) == 0)) {
        int orig_temp = *(volatile int *)(sensor + 8);
        const char *name = *(const char **)sensor;
        int max_safe = get_max_safe_temp(name);

        args->local.data[0] = (uint64_t)orig_temp;
        args->local.data[2] = (uint64_t)max_safe;

        if (orig_temp > 30 && orig_temp < max_safe) {
            logkd(LOG_TAG "Sensor '%s' temp=%d C -> clamped to 28 C [Hyper Active, MaxSafe=%d C]\n",
                  name ? name : "unknown", orig_temp, max_safe);
            *(volatile int *)(sensor + 8) = 28;
        } else if (orig_temp >= max_safe) {
            logkd(LOG_TAG "CRITICAL HARDWARE TRIP: Sensor '%s' temp=%d C >= %d C limit! Allowing OEM thermal cut.\n",
                  name ? name : "unknown", orig_temp, max_safe);
        }
    }
}

// After Hook for __dv2_check_thermal_level
void after_dv2_check(hook_fargs3_t *args, void *udata)
{
    int orig_temp = (int)args->local.data[0];
    uint64_t sensor = args->local.data[1];
    int max_safe = (int)args->local.data[2];

    // Restore real physical temperature in sensor struct
    if (sensor > 0xffffff8000000000ULL && orig_temp > 0) {
        *(volatile int *)(sensor + 8) = orig_temp;
    }

    // If Hyper mode was engaged and sensor was below emergency ceiling, ensure ret = 1 (check passed)
    if (g_hyper_enabled && orig_temp > 0 && orig_temp < max_safe && args->ret == 0) {
        logkd(LOG_TAG "Overriding ret 0 -> 1 (preventing premature trip, temp=%d C < %d C)\n",
              orig_temp, max_safe);
        args->ret = 1;
    }
}

// Hook for store_BN_TestMode (called when lynxd/user sets BN_TestMode)
// Signature: ssize_t store_BN_TestMode(struct device *dev, struct device_attribute *attr, const char *buf, size_t count)
void before_store_bn_test(hook_fargs4_t *args, void *udata)
{
    const char *buf = (const char *)args->arg2;
    if (buf) {
        if (buf[0] == '1') {
            g_hyper_enabled = 1;
            logkd(LOG_TAG "BN_TestMode=1 detected: Hyper Charging unthrottle ENABLED.\n");
        } else if (buf[0] == '0') {
            g_hyper_enabled = 0;
            logkd(LOG_TAG "BN_TestMode=0 detected: Hyper Charging unthrottle DISABLED (OEM fallback).\n");
        }
    }
}

// Hook for dv2_stop_algo (called when charger is unplugged)
// Signature: int dv2_stop_algo(struct dv2_algo_info *info)
void before_dv2_stop(hook_fargs1_t *args, void *udata)
{
    g_hyper_enabled = 0;
    logkd(LOG_TAG "dv2_stop_algo called (Charger disconnected): Unthrottle reset to 0 (SAFE DORMANT).\n");
}

// Module Control (from userspace via kpm ctl0)
static long lynx_thermal_control0(const char *args, char *__user out_msg, int outlen)
{
    if (args) {
        if (k_strcmp(args, "hyper_on") == 0 || k_strcmp(args, "1") == 0) {
            g_hyper_enabled = 1;
            logkd(LOG_TAG "Control: Hyper unthrottle forced ON\n");
        } else if (k_strcmp(args, "hyper_off") == 0 || k_strcmp(args, "0") == 0) {
            g_hyper_enabled = 0;
            logkd(LOG_TAG "Control: Hyper unthrottle forced OFF\n");
        }
    }
    return 0;
}

// Module Init
static long lynx_thermal_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Thermal Unthrottle KPM v2.0 (Dynamic Hyper-Aware)...\n");

    if (kallsyms_lookup_name) {
        target_dv2_check_thermal = (void *)kallsyms_lookup_name("__dv2_check_thermal_level");
        if (!target_dv2_check_thermal) {
            target_dv2_check_thermal = (void *)kallsyms_lookup_name("dv2_check_thermal_level");
        }
        target_store_bn_test = (void *)kallsyms_lookup_name("store_BN_TestMode");
        target_dv2_stop = (void *)kallsyms_lookup_name("dv2_stop_algo");
    }

    if (target_dv2_check_thermal) {
        logkd(LOG_TAG "Hooking __dv2_check_thermal_level at %px\n", target_dv2_check_thermal);
        hook_err_t err = hook_wrap3(target_dv2_check_thermal, before_dv2_check, after_dv2_check, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Installed hook on __dv2_check_thermal_level successfully!\n");
        } else {
            logke(LOG_TAG "Failed hook __dv2_check_thermal_level: %d\n", err);
        }
    }

    if (target_store_bn_test) {
        logkd(LOG_TAG "Hooking store_BN_TestMode at %px\n", target_store_bn_test);
        hook_err_t err = hook_wrap4(target_store_bn_test, before_store_bn_test, 0, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Installed hook on store_BN_TestMode successfully!\n");
        }
    }

    if (target_dv2_stop) {
        logkd(LOG_TAG "Hooking dv2_stop_algo at %px\n", target_dv2_stop);
        hook_err_t err = hook_wrap1(target_dv2_stop, before_dv2_stop, 0, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Installed hook on dv2_stop_algo successfully!\n");
        }
    }

    // Default to active on init so user immediately gets hyper benefits
    g_hyper_enabled = 1;
    logkd(LOG_TAG "KPM v2.0 active. Hyper-aware gating armed.\n");
    return 0;
}

// Module Exit / Unload
static long lynx_thermal_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx Thermal Unthrottle KPM v2.0...\n");
    g_hyper_enabled = 0;
    if (target_dv2_check_thermal) {
        unhook(target_dv2_check_thermal);
    }
    if (target_store_bn_test) {
        unhook(target_store_bn_test);
    }
    if (target_dv2_stop) {
        unhook(target_dv2_stop);
    }
    logkd(LOG_TAG "Unhooked all functions. Kernel restored cleanly.\n");
    return 0;
}

KPM_INIT(lynx_thermal_init);
KPM_CTL0(lynx_thermal_control0);
KPM_EXIT(lynx_thermal_exit);
