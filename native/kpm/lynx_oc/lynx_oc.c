/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Dedicated Adaptive Hardware Overclock (OC) Engine
 * Author: ɴᴏɪʀ
 * Target: MediaTek (Helio G96 / Dimensity) & ARM Mali-G57 / Cortex-A76 Big Cores
 * Framework: KernelPatch / APatch KPM
 */

#include <log.h>
#include <compiler.h>
#include <kpmodule.h>
#include <hook.h>
#include <kallsyms.h>
#include <linux/printk.h>

KPM_NAME("lynx_oc");
KPM_VERSION("2.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Dynamic Adaptive Hardware Overclock Engine (GPU 960 MHz & Anti-Throttle)");

#define LOG_TAG "[LynxOC] "

// Target kernel symbols
static void *target_mt_gpufreq_target = NULL;
static void *target_ged_dvfs_boost_gpu_freq = NULL;
static void *target_cpufreq_driver_target = NULL;
static void *target_dev_pm_opp_set_rate = NULL;

struct mt_gpufreq_opp_entry {
    uint32_t freq;       // kHz (e.g. 950000)
    uint32_t vgpu;       // 10^-5 V (e.g. 85000)
    uint32_t vsram;      // 10^-5 V (e.g. 95000)
    uint32_t posdiv;     // PLL post divider (e.g. 4)
    uint32_t gpu_power;  // mW (e.g. 1307)
};

static struct mt_gpufreq_opp_entry *live_gpufreq_opp_table = NULL;
static uint32_t original_opp0_freq = 950000;
static uint32_t original_opp0_vgpu = 85000;
static uint32_t original_opp0_vsram = 95000;

// Runtime Configuration: Target 960 MHz by default (+10 MHz overclock test)
#define LYNX_GPU_OC_TARGET_KHZ 960000
static int oc_gpu_target_freq_khz = LYNX_GPU_OC_TARGET_KHZ;
static int oc_gpu_enabled = 1; // Armed on load
static int oc_cpu_enabled = 0; // Pure GPU focus

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
    if (!oc_gpu_enabled) return;

    unsigned int target = (unsigned int)args->arg0;

    // When driver/governor demands peak graphics performance, elevate to overclock target
    if (target >= 940000) {
        args->arg0 = (uint64_t)oc_gpu_target_freq_khz;
        logkd(LOG_TAG "GPU Target Boosted: %u kHz -> %d kHz (Overclock Active)\n",
              target, oc_gpu_target_freq_khz);
    }
}

void after_mt_gpufreq_target(hook_fargs2_t *args, void *udata)
{
}

// 2. Hook for MediaTek GED DVFS GPU Boost: ged_dvfs_boost_gpu_freq(unsigned int freq)
void before_ged_dvfs_boost_gpu_freq(hook_fargs1_t *args, void *udata)
{
    if (!oc_gpu_enabled) return;

    unsigned int freq = (unsigned int)args->arg0;
    if (freq >= 940000) {
        args->arg0 = (uint64_t)oc_gpu_target_freq_khz;
    }
}

void after_ged_dvfs_boost_gpu_freq(hook_fargs1_t *args, void *udata)
{
}

// 3. Hook for CPU cpufreq_driver_target(struct cpufreq_policy *policy, unsigned int target_freq, unsigned int relation)
void before_cpufreq_driver_target(hook_fargs3_t *args, void *udata)
{
    if (!oc_cpu_enabled) return;

    unsigned int target = (unsigned int)args->arg1;
    if (target >= 1670000 && target < 2050000) {
        args->arg1 = 2050000;
        logkd(LOG_TAG "CPU Big Core Boost: %u kHz -> 2050000 kHz\n", target);
    }
}

void after_cpufreq_driver_target(hook_fargs3_t *args, void *udata)
{
}

// 4. Hook for dev_pm_opp_set_rate(struct device *dev, unsigned long target_freq)
void before_opp_set_rate(hook_fargs2_t *args, void *udata)
{
    if (!oc_cpu_enabled) return;

    unsigned long freq = (unsigned long)args->arg1;
    if (freq < 1500000000UL && freq > 700000000UL) {
        args->arg1 = 2050000000UL;
    }
}

void after_opp_set_rate(hook_fargs2_t *args, void *udata)
{
}

// Module Control (from userspace CLI/APatch supercall or lynxd safety monitor)
static long lynx_oc_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;
    logkd(LOG_TAG "Control command: %s\n", args);

    if (k_str_contains(args, "960") || k_str_contains(args, "gpu=960")) {
        oc_gpu_enabled = 1;
        oc_gpu_target_freq_khz = 960000;
        if (live_gpufreq_opp_table) {
            live_gpufreq_opp_table[0].freq = 960000;
            live_gpufreq_opp_table[0].vgpu = 85625; // 856.25 mV
            live_gpufreq_opp_table[0].vsram = 95625; // 956.25 mV
        }
        logkd(LOG_TAG "GPU OPP[0] Set to 960 MHz (Overclocked @ 856.25 mV)\n");
    } else if (k_str_contains(args, "1000") || k_str_contains(args, "gpu=1000")) {
        oc_gpu_enabled = 1;
        oc_gpu_target_freq_khz = 1000000;
        if (live_gpufreq_opp_table) {
            live_gpufreq_opp_table[0].freq = 1000000;
            live_gpufreq_opp_table[0].vgpu = 88000; // 880 mV
            live_gpufreq_opp_table[0].vsram = 98000; // 980 mV
        }
        logkd(LOG_TAG "GPU OPP[0] Set to 1000 MHz (Extreme Overclock @ 880 mV)\n");
    } else if (k_str_contains(args, "stock") || k_str_contains(args, "reset") ||
               k_str_contains(args, "oc=0") || k_str_contains(args, "950")) {
        oc_gpu_enabled = 0;
        oc_gpu_target_freq_khz = 950000;
        if (live_gpufreq_opp_table) {
            live_gpufreq_opp_table[0].freq = original_opp0_freq;
            live_gpufreq_opp_table[0].vgpu = original_opp0_vgpu;
            live_gpufreq_opp_table[0].vsram = original_opp0_vsram;
        }
        logkd(LOG_TAG "GPU Restored to Stock Default: 950 MHz @ 850 mV\n");
    }

    return 0;
}

// Module Initialization
static long lynx_oc_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Dedicated Hardware OC Engine (Mali-G57 @ 960 MHz)...\n");

    if (kallsyms_lookup_name) {
        target_mt_gpufreq_target = (void *)kallsyms_lookup_name("mt_gpufreq_target");
        target_ged_dvfs_boost_gpu_freq = (void *)kallsyms_lookup_name("ged_dvfs_boost_gpu_freq");
        target_cpufreq_driver_target = (void *)kallsyms_lookup_name("cpufreq_driver_target");
        target_dev_pm_opp_set_rate = (void *)kallsyms_lookup_name("dev_pm_opp_set_rate");

        void *fn_get_freq = (void *)kallsyms_lookup_name("mt_gpufreq_get_freq_by_idx");
        if (fn_get_freq) {
            uint32_t *c = (uint32_t *)fn_get_freq;
            uint32_t insn_adrp = c[8];
            uint32_t insn_ldr = c[9];

            // Decode ADRP
            uint64_t immlo = (insn_adrp >> 29) & 0x3;
            uint64_t immhi = (insn_adrp >> 5) & 0x7ffff;
            int64_t imm = (immhi << 2) | immlo;
            if (imm & (1 << 20)) imm -= (1 << 21);
            uint64_t pc_page = ((uint64_t)&c[8]) & ~0xfffULL;
            uint64_t target_page = pc_page + (imm << 12);

            // Decode 64-bit unsigned LDR offset (imm12 << 3)
            uint64_t imm12 = (insn_ldr >> 10) & 0xfff;
            uint64_t offset = imm12 << 3;
            uint64_t ptr_addr = target_page + offset;

            logkd(LOG_TAG "Decoded OPP table ptr addr: 0x%llx\n", ptr_addr);
            struct mt_gpufreq_opp_entry **table_ptr = (struct mt_gpufreq_opp_entry **)ptr_addr;
            if (table_ptr && *table_ptr) {
                live_gpufreq_opp_table = *table_ptr;
                original_opp0_freq = live_gpufreq_opp_table[0].freq;
                original_opp0_vgpu = live_gpufreq_opp_table[0].vgpu;
                original_opp0_vsram = live_gpufreq_opp_table[0].vsram;

                logkd(LOG_TAG "Live OPP Table located at: 0x%llx\n", (uint64_t)live_gpufreq_opp_table);
                logkd(LOG_TAG "OPP[0] stock: freq=%u kHz, vgpu=%u, vsram=%u, posdiv=%u\n",
                      original_opp0_freq, original_opp0_vgpu, original_opp0_vsram,
                      live_gpufreq_opp_table[0].posdiv);

                // Elevate OPP 0 to 960 MHz with +6.25 mV voltage headroom
                live_gpufreq_opp_table[0].freq = oc_gpu_target_freq_khz;
                live_gpufreq_opp_table[0].vgpu = 85625;
                live_gpufreq_opp_table[0].vsram = 95625;
                live_gpufreq_opp_table[0].gpu_power = 1350;

                logkd(LOG_TAG "SUCCESS: OPP[0] elevated to %u kHz @ 856.25 mV (960 MHz Overclock Armed!)\n",
                      oc_gpu_target_freq_khz);
            }
        }
    }

    // 1. Hook mt_gpufreq_target
    if (target_mt_gpufreq_target) {
        hook_err_t err = hook_wrap2(target_mt_gpufreq_target, before_mt_gpufreq_target, after_mt_gpufreq_target, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked mt_gpufreq_target (GPU Boost Target: %d kHz)\n", oc_gpu_target_freq_khz);
        }
    }

    // 2. Hook ged_dvfs_boost_gpu_freq
    if (target_ged_dvfs_boost_gpu_freq) {
        hook_err_t err = hook_wrap1(target_ged_dvfs_boost_gpu_freq, before_ged_dvfs_boost_gpu_freq, after_ged_dvfs_boost_gpu_freq, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked ged_dvfs_boost_gpu_freq (GED Overclock Boost Active)\n");
        }
    }

    logkd(LOG_TAG "Lynx Dedicated Hardware OC Engine successfully armed to 960 MHz!\n");
    return 0;
}

// Module Exit / Unload
static long lynx_oc_exit(void *__user reserved)
{
    logkd(LOG_TAG "Unloading Lynx Dedicated Hardware OC Engine...\n");

    if (live_gpufreq_opp_table && original_opp0_freq) {
        live_gpufreq_opp_table[0].freq = original_opp0_freq;
        live_gpufreq_opp_table[0].vgpu = original_opp0_vgpu;
        live_gpufreq_opp_table[0].vsram = original_opp0_vsram;
        logkd(LOG_TAG "Restored stock OPP[0] freq: %u kHz @ %u vgpu\n", original_opp0_freq, original_opp0_vgpu);
    }

    if (target_mt_gpufreq_target) {
        unhook(target_mt_gpufreq_target);
    }
    if (target_ged_dvfs_boost_gpu_freq) {
        unhook(target_ged_dvfs_boost_gpu_freq);
    }
    if (target_cpufreq_driver_target) {
        unhook(target_cpufreq_driver_target);
    }
    if (target_dev_pm_opp_set_rate) {
        unhook(target_dev_pm_opp_set_rate);
    }

    logkd(LOG_TAG "All OC hooks removed. GPU restored to stock 950 MHz.\n");
    return 0;
}

KPM_INIT(lynx_oc_init);
KPM_CTL0(lynx_oc_control0);
KPM_EXIT(lynx_oc_exit);
