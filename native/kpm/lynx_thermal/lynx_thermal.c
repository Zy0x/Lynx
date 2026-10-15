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
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic TPCB Thermal Override for Direct Charge");

#define LOG_TAG "[LynxKPM] "

// Symbols to hook dynamically
static void *target_dv2_check_thermal = NULL;

// Maximum allowed physical PCB temperature before emergency safety fallback (degrees Celsius)
#define HARD_MAX_SAFE_PCB_TEMP 52

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

static int find_dv2_sym_cb(void *data, const char *name, struct module *mod, unsigned long addr)
{
    if (name && (k_strcmp(name, "__dv2_check_thermal_level") == 0 || k_strcmp(name, "dv2_check_thermal_level") == 0)) {
        *(void **)data = (void *)addr;
        return 1;
    }
    return 0;
}

static void *target_dv2_check_tpcb = NULL;

// Before Hook for __dv2_check_thermal_level
// Kernel signature: int __dv2_check_thermal_level(struct dv2_algo_info *info, struct dv2_thermal_sensor *sensor)
// Disassembly:
//   ldr x9, [x1, #0x18]    ; table pointer
//   ldr w3, [x1, #0x8]     ; current sensor temperature
//   cmp w3, w4             ; compare temp with max table entry
//   b.ge trip_label        ; if temp >= max, fail check
//   b.le ok_label          ; if temp <= min, level 0 ok
void before_dv2_check(hook_fargs3_t *args, void *udata)
{
    uint64_t sensor = args->arg1;
    args->local.data[0] = 0;
    args->local.data[1] = sensor;

    if (sensor > 0xffffff8000000000ULL && ((sensor & 7) == 0)) {
        int orig_temp = *(volatile int *)(sensor + 8);
        const char *name = *(const char **)sensor;

        args->local.data[0] = (uint64_t)orig_temp;

        if (orig_temp > 32 && orig_temp < HARD_MAX_SAFE_PCB_TEMP) {
            logkd(LOG_TAG "Sensor '%s' temp=%d C -> clamped to 28 C (Thermal Unthrottled!)\n",
                  name ? name : "unknown", orig_temp);
            // Clamp temp to 28 C while check function evaluates!
            *(volatile int *)(sensor + 8) = 28;
        } else if (orig_temp >= HARD_MAX_SAFE_PCB_TEMP) {
            logkd(LOG_TAG "EMERGENCY: Sensor '%s' temp=%d C >= %d C safe limit! Allowing hardware emergency trip.\n",
                  name ? name : "unknown", orig_temp, HARD_MAX_SAFE_PCB_TEMP);
        }
    }
}

// After Hook for __dv2_check_thermal_level
void after_dv2_check(hook_fargs3_t *args, void *udata)
{
    int orig_temp = (int)args->local.data[0];
    uint64_t sensor = args->local.data[1];

    // Restore real physical temperature in sensor struct
    if (sensor > 0xffffff8000000000ULL && orig_temp > 0) {
        *(volatile int *)(sensor + 8) = orig_temp;
    }

    // Ensure ret remains 1 (true = thermal check passed) as long as temp is below emergency limit
    if (orig_temp > 0 && orig_temp < HARD_MAX_SAFE_PCB_TEMP && args->ret == 0) {
        logkd(LOG_TAG "Overriding ret 0 -> 1 (preventing emergency stop, temp=%d C < %d C)\n",
              orig_temp, HARD_MAX_SAFE_PCB_TEMP);
        args->ret = 1;
    }
}

// Module Initialization
static long lynx_thermal_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Thermal Unthrottle KPM...\n");

    // Dynamic symbol resolution using KernelPatch kallsyms
    if (kallsyms_lookup_name) {
        target_dv2_check_thermal = (void *)kallsyms_lookup_name("__dv2_check_thermal_level");
        if (!target_dv2_check_thermal) {
            target_dv2_check_thermal = (void *)kallsyms_lookup_name("dv2_check_thermal_level");
        }
        target_dv2_check_tpcb = (void *)kallsyms_lookup_name("__dv2_check_tpcb_level");
    }

    // Fallback: iterate symbols if lookup by name failed
    if (!target_dv2_check_thermal && kallsyms_on_each_symbol) {
        kallsyms_on_each_symbol(find_dv2_sym_cb, &target_dv2_check_thermal);
    }

    if (target_dv2_check_thermal) {
        logkd(LOG_TAG "Found __dv2_check_thermal_level at %px\n", target_dv2_check_thermal);

        // Dump first 16 instructions of __dv2_check_thermal_level
        uint32_t *code = (uint32_t *)target_dv2_check_thermal;
        logkd(LOG_TAG "code __dv2_check_thermal_level:\n");
        for (int i = 0; i < 16; i += 4) {
            logkd(LOG_TAG "  [%02d..%02d] %08x %08x %08x %08x\n",
                  i, i+3, code[i], code[i+1], code[i+2], code[i+3]);
        }

        hook_err_t err = hook_wrap3(target_dv2_check_thermal, before_dv2_check, after_dv2_check, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Successfully installed inline hook on __dv2_check_thermal_level!\n");
        } else {
            logke(LOG_TAG "Failed to hook __dv2_check_thermal_level, error: %d\n", err);
        }
    } else {
        logke(LOG_TAG "Symbol __dv2_check_thermal_level not found in kallsyms\n");
    }

    if (target_dv2_check_tpcb) {
        logkd(LOG_TAG "Found __dv2_check_tpcb_level at %px\n", target_dv2_check_tpcb);
        uint32_t *code_tpcb = (uint32_t *)target_dv2_check_tpcb;
        logkd(LOG_TAG "code __dv2_check_tpcb_level:\n");
        for (int i = 0; i < 16; i += 4) {
            logkd(LOG_TAG "  [%02d..%02d] %08x %08x %08x %08x\n",
                  i, i+3, code_tpcb[i], code_tpcb[i+1], code_tpcb[i+2], code_tpcb[i+3]);
        }
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
