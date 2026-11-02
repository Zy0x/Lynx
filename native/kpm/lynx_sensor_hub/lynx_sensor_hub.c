/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - MediaTek SCP Sensor Hub Auto-Sync & Anti-Race KPM
 * Target: MediaTek Helio G96 (MT6781) / Linux 4.14+ (ARM64)
 * Framework: KernelPatch / APatch
 *
 * Root Cause Analysis:
 * During early boot (~second 3), MTK kernel dispatches `sensorHub_power_up_work`
 * which calls `sensorHub_power_up_loop`. However, the MediaTek Sensor Control
 * Processor (SCP) firmware takes ~15 seconds to fully complete cold-boot and
 * IPI synchronization. As a result, the initial sensor registrations fail with
 * `<SEN_LIST> sensor(x) not register`, and MTK's driver never retries the loop.
 * Consequently, `/dev/m_*_misc` remain unpopulated (`accdevnum = 0`), and the
 * Android Sensor HAL starts with dummy MTK stubs.
 *
 * KPM Solution v1.3.0:
 * 1. Hooks `scp_sensorHub_req_send` at Ring-0.
 * 2. In `before_`, forces blocking wait mode (arg2 = 1) and paces early packets.
 * 3. In `after_`, detects the exact moment SCP becomes operational (first ret == 0).
 * 4. Sets SCP ready flags (flag1=1, flag2=1) and wakes the sensorHub waitqueue.
 *    `sensorHub_power_up_loop()` executes without hanging and registers all 16
 *    physical sensors naturally with SCP!
 * 5. Provides on-demand control via `kpatch ctl0 lynx_sensor_hub 2`.
 *
 * Result:
 * All physical sensors (`lsm6dsm_acc`, `lsm6dsm_gyro`, `mn78911_l`, `mn78911_p`)
 * initialize 100% naturally before Android Sensor HAL starts.
 * ZERO userspace pancingan, ZERO soft-reboot, ZERO framework crash.
 *
 * Author: ɴᴏɪʀ
 * License: GPL v2
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_sensor_hub");
KPM_VERSION("1.3.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx MTK SCP Sensor Hub Natural Auto-Sync KPM");

#define LOG_TAG "[LynxSensorHubKPM] "

// Kernel symbol pointers
static void *target_scp_sensorHub_req_send = NULL;
typedef void (*msleep_fn_t)(unsigned int msecs);
static msleep_fn_t k_msleep = NULL;

typedef int (*sensorHub_power_up_loop_fn_t)(void);
static sensorHub_power_up_loop_fn_t k_sensorHub_power_up_loop = NULL;

typedef void (*wake_up_fn_t)(void *q, unsigned int mode, int nr_exclusive, void *key);
static wake_up_fn_t k_wake_up = NULL;

// Exact offsets relative to sensorHub_power_up_loop in Helio G96 kernel (4.14.186)
#define OFFSET_FLAG_READY   0x1618038
#define OFFSET_FLAG_NOTIFY  0x161803c
#define OFFSET_WAITQUEUE    0x1141a70

// Runtime state
static volatile int g_sensor_hub_kpm_enabled = 1;
static volatile unsigned long g_pacing_count = 0;
static volatile unsigned long g_success_packets = 0;
static volatile unsigned long g_failed_packets = 0;
static volatile int g_power_up_loop_done = 0;
static volatile int g_in_sync = 0;

#define EARLY_BOOT_PACING_LIMIT 30
#define SCP_READY_SETTLE_MS 25

static void trigger_sensor_hub_sync(const char *reason)
{
    if (!k_sensorHub_power_up_loop) {
        logke(LOG_TAG "trigger_sensor_hub_sync FAILED: k_sensorHub_power_up_loop is NULL!\n");
        return;
    }

    g_in_sync = 1;
    logkd(LOG_TAG "Triggering Sensor Hub Natural Sync (Reason: %s)...\n", reason);

    uint8_t *p_flag_ready  = (uint8_t *)((uint64_t)k_sensorHub_power_up_loop + OFFSET_FLAG_READY);
    uint8_t *p_flag_notify = (uint8_t *)((uint64_t)k_sensorHub_power_up_loop + OFFSET_FLAG_NOTIFY);
    void *p_waitqueue      = (void *)((uint64_t)k_sensorHub_power_up_loop + OFFSET_WAITQUEUE);

    // Set ready flags so sensorHub_power_up_loop skips the waitqueue
    *p_flag_ready = 1;
    *p_flag_notify = 1;
    logkd(LOG_TAG "Pre-armed ready flags: ready=%u, notify=%u at %px, %px\n",
          *p_flag_ready, *p_flag_notify, p_flag_ready, p_flag_notify);

    // Wake up any sleeping waitqueue thread (mode 3 = TASK_INTERRUPTIBLE|TASK_UNINTERRUPTIBLE)
    if (k_wake_up) {
        logkd(LOG_TAG "Waking waitqueue at %px...\n", p_waitqueue);
        k_wake_up(p_waitqueue, 3, 1, NULL);
    }

    // Execute power_up_loop
    int loop_res = k_sensorHub_power_up_loop();
    logkd(LOG_TAG "sensorHub_power_up_loop() completed with ret=%d!\n", loop_res);

    g_in_sync = 0;
}

// 1. Hook for scp_sensorHub_req_send(SCP_SENSOR_HUB_DATA_P data, uint *len, unsigned int wait)
static void before_scp_sensorHub_req_send(hook_fargs3_t *args, void *udata)
{
    if (!g_sensor_hub_kpm_enabled || !args || g_in_sync) return;

    // Force wait flag to 1 (blocking IPI delivery with wait queue acknowledgment)
    args->arg2 = (uint64_t)1;

    // Pace early-boot initialization packets to allow SCP firmware to settle
    if (g_pacing_count < EARLY_BOOT_PACING_LIMIT) {
        g_pacing_count++;
        if (k_msleep) {
            k_msleep(SCP_READY_SETTLE_MS);
        }
    }
}

static void after_scp_sensorHub_req_send(hook_fargs3_t *args, void *udata)
{
    if (!g_sensor_hub_kpm_enabled || !args || g_in_sync) return;

    int ret = (int)args->ret;
    if (ret == 0) {
        g_success_packets++;
        if (g_pacing_count <= EARLY_BOOT_PACING_LIMIT) {
            logkd(LOG_TAG "SCP sensor request ACKNOWLEDGED (ret=0, success_count=%lu)\n",
                  g_success_packets);
        }

        // Automatic Natural Synchronization when SCP firmware wakes up
        if (!g_power_up_loop_done && k_sensorHub_power_up_loop) {
            g_power_up_loop_done = 1;
            trigger_sensor_hub_sync("SCP ACKNOWLEDGED FIRST PACKET");
        }
    } else {
        g_failed_packets++;
        logkd(LOG_TAG "SCP early packet not acknowledged (ret=%d, fail_count=%lu)\n",
              ret, g_failed_packets);
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
    } else if (args[0] == '2') {
        trigger_sensor_hub_sync("MANUAL CTL0 TRIGGER");
    }

    return 0;
}

// 3. Module initialization
static long lynx_sensor_hub_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx MTK SCP Sensor Hub Natural Auto-Sync KPM v1.3.0...\n");

    if (kallsyms_lookup_name) {
        target_scp_sensorHub_req_send = (void *)kallsyms_lookup_name("scp_sensorHub_req_send");
        k_msleep = (msleep_fn_t)kallsyms_lookup_name("msleep");
        k_sensorHub_power_up_loop = (sensorHub_power_up_loop_fn_t)kallsyms_lookup_name("sensorHub_power_up_loop");
        k_wake_up = (wake_up_fn_t)kallsyms_lookup_name("__wake_up");
    }

    if (!target_scp_sensorHub_req_send) {
        logke(LOG_TAG "CRITICAL: Symbol 'scp_sensorHub_req_send' not found in kallsyms!\n");
        return -1;
    }

    logkd(LOG_TAG "Resolved symbols: req_send=%px, power_up_loop=%px, wake_up=%px, msleep=%px\n",
          target_scp_sensorHub_req_send, k_sensorHub_power_up_loop, k_wake_up, k_msleep);

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
    logkd(LOG_TAG "Lynx MTK SCP Sensor Hub Natural Auto-Sync KPM v1.3.0 ARMED successfully.\n");
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
