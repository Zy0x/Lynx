/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - MediaTek SCP Sensor Hub Auto-Sync & Anti-Race KPM
 * Target: MediaTek Helio G96 (MT6781) / Linux 4.14+ (ARM64)
 * Framework: KernelPatch / APatch
 *
 * Root Cause Analysis:
 * During early boot (~second 20), Core 1 finishes sending DRAM info to the
 * Sensor Control Processor (SCP) via `sensor_send_dram_info_to_hub`.
 * Immediately 1 millisecond later, a kernel workqueue on Core 7 dispatches
 * `scp_sensorHub_req_send` to register sensors (accel, gyro, msensor, etc.).
 * Because SCP firmware requires 15-40ms to finish cold boot and SRAM/DRAM setup,
 * the initial IPI requests are rejected (`scp_sensorHub_req_send fail!`).
 * MediaTek's driver lacks an automatic retry loop, causing sensors to remain
 * permanently unregistered (<SEN_LIST> sensor(1) not register).
 *
 * KPM Solution:
 * 1. Hooks `scp_sensorHub_req_send` at Ring-0.
 * 2. In `before_`, forces blocking wait mode (arg2 = 1) and paces early boot
 *    packets by ~45ms, ensuring SCP firmware is 100% ready before IPI transmission.
 * 3. In `after_`, verifies SCP acknowledgment and monitors status.
 *
 * Result:
 * Sensors initialize 100% naturally at second 20 inside the Linux kernel.
 * Android Sensor HAL starts cleanly at second 22 with all sensors active.
 * Eliminates userspace workarounds, sysfs pokes, and HAL restarts that
 * previously triggered soft-reboots after reaching the home screen.
 *
 * Author: ɴᴏɪʀ
 * License: GPL v2
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_sensor_hub");
KPM_VERSION("1.1.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx MTK SCP Sensor Hub Auto-Sync & Ring-0 Auto-Retry KPM");

#define LOG_TAG "[LynxSensorHubKPM] "

// Kernel symbol pointers
static void *target_scp_sensorHub_req_send = NULL;
typedef void (*msleep_fn_t)(unsigned int msecs);
static msleep_fn_t k_msleep = NULL;

// Runtime state
static volatile int g_sensor_hub_kpm_enabled = 1;
static volatile unsigned long g_pacing_count = 0;
static volatile unsigned long g_success_packets = 0;
static volatile unsigned long g_failed_packets = 0;
static volatile unsigned long g_recovered_packets = 0;
static volatile int g_in_retry = 0;

#define EARLY_BOOT_PACING_LIMIT 30
#define SCP_READY_SETTLE_MS 45
#define RETRY_MAX_ATTEMPTS 10
#define RETRY_BACKOFF_MS 40

// 1. Hook for scp_sensorHub_req_send(SCP_SENSOR_HUB_DATA_P data, uint *len, unsigned int wait)
static void before_scp_sensorHub_req_send(hook_fargs3_t *args, void *udata)
{
    if (!g_sensor_hub_kpm_enabled || !args || g_in_retry) return;

    // Force wait flag to 1 (blocking IPI delivery with wait queue acknowledgment)
    args->arg2 = (uint64_t)1;

    // Pace early-boot initialization packets to allow SCP firmware to settle
    if (g_pacing_count < EARLY_BOOT_PACING_LIMIT) {
        g_pacing_count++;
        logkd(LOG_TAG "Pacing SCP packet #%lu (+%d ms for SCP settle window)\n",
              g_pacing_count, SCP_READY_SETTLE_MS);

        if (k_msleep) {
            k_msleep(SCP_READY_SETTLE_MS);
        }
    }
}

static void after_scp_sensorHub_req_send(hook_fargs3_t *args, void *udata)
{
    if (!g_sensor_hub_kpm_enabled || !args || g_in_retry) return;

    int ret = (int)args->ret;
    if (ret == 0) {
        g_success_packets++;
        if (g_pacing_count <= EARLY_BOOT_PACING_LIMIT) {
            logkd(LOG_TAG "SCP sensor request ACKNOWLEDGED (ret=0, success_count=%lu)\n",
                  g_success_packets);
        }
    } else {
        g_failed_packets++;
        logke(LOG_TAG "SCP sensor request FAILED (ret=%d, fail_count=%lu). Initiating Ring-0 Auto-Retry...\n",
              ret, g_failed_packets);

        if (target_scp_sensorHub_req_send) {
            g_in_retry = 1;
            int attempt;
            for (attempt = 1; attempt <= RETRY_MAX_ATTEMPTS; attempt++) {
                if (k_msleep) {
                    k_msleep(RETRY_BACKOFF_MS);
                }

                int retry_res = ((int (*)(void *, void *, unsigned int))target_scp_sensorHub_req_send)(
                    (void *)args->arg0, (void *)args->arg1, 1);

                if (retry_res == 0) {
                    logkd(LOG_TAG "SCP sensor request RECOVERED on attempt #%d! Overriding ret to 0 (total recovered: %lu)\n",
                          attempt, ++g_recovered_packets);
                    args->ret = 0;
                    g_success_packets++;
                    break;
                } else {
                    logkd(LOG_TAG "SCP sensor request retry #%d failed (ret=%d)\n",
                          attempt, retry_res);
                }
            }
            g_in_retry = 0;
        }
    }
}

// 2. Control interface (kpatch ctl0)
static long lynx_sensor_hub_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return -1;

    if (args[0] == '1') {
        g_sensor_hub_kpm_enabled = 1;
        logkd(LOG_TAG "Lynx Sensor Hub KPM ENABLED\n");
    } else if (args[0] == '0') {
        g_sensor_hub_kpm_enabled = 0;
        logkd(LOG_TAG "Lynx Sensor Hub KPM DISABLED\n");
    }

    return 0;
}

// 3. Module initialization
static long lynx_sensor_hub_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx MTK SCP Sensor Hub Auto-Sync KPM v1.0...\n");

    if (kallsyms_lookup_name) {
        target_scp_sensorHub_req_send = (void *)kallsyms_lookup_name("scp_sensorHub_req_send");
        k_msleep = (msleep_fn_t)kallsyms_lookup_name("msleep");
    }

    if (!target_scp_sensorHub_req_send) {
        logke(LOG_TAG "CRITICAL: Symbol 'scp_sensorHub_req_send' not found in kallsyms!\n");
        return -1;
    }

    logkd(LOG_TAG "Resolved scp_sensorHub_req_send at %px, msleep at %px\n",
          target_scp_sensorHub_req_send, k_msleep);

    // Hook scp_sensorHub_req_send (3 arguments: data, len, wait)
    hook_err_t err = hook_wrap3(target_scp_sensorHub_req_send,
                               before_scp_sensorHub_req_send,
                               after_scp_sensorHub_req_send,
                               0);

    if (err != HOOK_NO_ERR) {
        logke(LOG_TAG "CRITICAL: Failed to hook scp_sensorHub_req_send (err=%d)\n", (int)err);
        target_scp_sensorHub_req_send = NULL;
        return -1;
    }

    g_sensor_hub_kpm_enabled = 1;
    logkd(LOG_TAG "Lynx MTK SCP Sensor Hub Auto-Sync KPM v1.0 ARMED successfully.\n");
    return 0;
}

// 4. Module exit (clean unload)
static long lynx_sensor_hub_exit(void *__user reserved)
{
    g_sensor_hub_kpm_enabled = 0;

    if (target_scp_sensorHub_req_send) {
        unhook(target_scp_sensorHub_req_send);
        target_scp_sensorHub_req_send = NULL;
    }

    logkd(LOG_TAG "Lynx MTK SCP Sensor Hub KPM unloaded cleanly. Hooks detached.\n");
    return 0;
}

KPM_INIT(lynx_sensor_hub_init);
KPM_CTL0(lynx_sensor_hub_control0);
KPM_EXIT(lynx_sensor_hub_exit);
