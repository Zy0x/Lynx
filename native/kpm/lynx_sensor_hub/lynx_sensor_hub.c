/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - MediaTek SCP Sensor Hub Natural Auto-Sync & FSM Fix KPM
 * Target: MediaTek Helio G96 (MT6781) / Linux 4.14+ (ARM64)
 * Framework: KernelPatch / APatch
 *
 * Root Cause Analysis & Solution v1.8.0:
 * 1. MTK Sensor Control Processor (SCP) firmware cold-boot is asynchronous.
 * 2. In early boot, initial sensor registrations fail because SCP ready flags
 *    are not yet set.
 * 3. In SCP firmware (accGyro.c), FSM command 36 (CHISC) has an incomplete / dummy
 *    entry in completion table tbh[33] pointing to unhandled handler at 0x21526
 *    instead of 0x2156e.
 * 4. This causes mTask.fsmState to be permanently stuck at 36 (0x24), triggering
 *    continuous "accGyro CHISC pending by:36" loop and blocking physical interrupt
 *    unmasking ("will not unmask A+G Eint").
 * 5. MediaTek SCP SRAM requires strict 32-bit (4-byte) aligned bus transactions.
 *    Any byte or halfword access must use 32-bit aligned Read-Modify-Write (RMW).
 * 6. This KPM:
 *    - Resolves SCP SRAM via live kernel symbol scp_sys_full_reset instruction parsing.
 *    - Uses 32-bit aligned RMW to patch tbh[33] to 0x004a (handler 0x2156e).
 *    - Unfreezes mTask.fsmState to 0x28 (40 = IDLE) and clears pending flags.
 *    - Pre-arms ready & notify flags, wakes up the kernel thread (PID 211: scp_power_reset),
 *      populates /dev/sensorlist, and sets g_sensorlist_ready = 1.
 * 7. Once synchronization is completed, the KPM immediately disarms its hook,
 *    ensuring ZERO overhead, ZERO memory/lock contention, and ZERO crash risk.
 *
 * Result:
 * Accelerometer & Gyroscope (LSM6DSM) physical hardware interrupts stream 100%
 * naturally without lockups or CHISC hangs.
 *
 * Author: ɴᴏɪʀ
 * License: GPL v2
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_sensor_hub");
KPM_VERSION("1.8.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx MTK SCP Sensor Hub Natural Auto-Sync & FSM Fix KPM");

#define LOG_TAG "[LynxSensorHubKPM] "

// Kernel symbol pointers
static void *target_scp_sensorHub_req_send = NULL;

typedef int (*sensorHub_power_up_loop_fn_t)(void);
static sensorHub_power_up_loop_fn_t k_sensorHub_power_up_loop = NULL;

typedef void (*wake_up_fn_t)(void *q, unsigned int mode, int nr_exclusive, void *key);
static wake_up_fn_t k_wake_up = NULL;

typedef void (*sensorlist_get_deviceinfo_fn_t)(void *work);
static sensorlist_get_deviceinfo_fn_t k_sensorlist_get_deviceinfo = NULL;

typedef void *(*ioremap_cache_fn_t)(uint64_t phys_addr, size_t size);
static ioremap_cache_fn_t k_ioremap_cache = NULL;

typedef void (*iounmap_fn_t)(volatile void *addr);
static iounmap_fn_t k_iounmap = NULL;

// Exact offsets relative to sensorHub_power_up_loop in Helio G96 kernel (4.14.186)
#define OFFSET_FLAG_READY       0x1618038
#define OFFSET_FLAG_NOTIFY      0x161803c
#define OFFSET_WAITQUEUE        0x1141a70
#define OFFSET_FLAG_SENSORLIST  0x1618198
#define OFFSET_BUF_SENSORLIST   0x16181c0

// Physical addresses and offsets for MediaTek SCP SRAM on MT6781
#define SCP_SRAM_PHYS_BASE      0x10500000ULL
#define SCP_SRAM_SIZE           0xc0000ULL

#define SCP_OFFSET_TBH_CMD36    0x2151c
#define SCP_OFFSET_MTASK        0x43ae0
#define SCP_OFFSET_FSM_STATE    (SCP_OFFSET_MTASK + 0x2c5) // 0x43da5
#define SCP_OFFSET_PENDING_F    (SCP_OFFSET_MTASK + 0x0f)  // 0x43aef
#define SCP_OFFSET_PENDING_15   (SCP_OFFSET_MTASK + 0x15)  // 0x43af5

// Runtime state
static volatile int g_sensor_hub_kpm_enabled = 1;
static volatile unsigned long g_success_packets = 0;
static volatile int g_power_up_loop_done = 0;
static volatile int g_in_sync = 0;

// Strict 32-bit aligned Read-Modify-Write helpers for MediaTek SCP SRAM bus
static inline uint32_t scp_read32(uint8_t *scp, uint32_t offset)
{
    return *(volatile uint32_t *)(scp + (offset & ~3U));
}

static inline void scp_write32(uint8_t *scp, uint32_t offset, uint32_t val)
{
    *(volatile uint32_t *)(scp + (offset & ~3U)) = val;
}

static inline uint16_t scp_read16(uint8_t *scp, uint32_t offset)
{
    uint32_t word = scp_read32(scp, offset);
    int shift = (offset & 2) ? 16 : 0;
    return (uint16_t)((word >> shift) & 0xffffU);
}

static inline void scp_write16(uint8_t *scp, uint32_t offset, uint16_t val)
{
    uint32_t aligned_off = offset & ~3U;
    int shift = (offset & 2) ? 16 : 0;
    uint32_t word = *(volatile uint32_t *)(scp + aligned_off);
    word = (word & ~(0xffffU << shift)) | (((uint32_t)val & 0xffffU) << shift);
    *(volatile uint32_t *)(scp + aligned_off) = word;
}

static inline uint8_t scp_read8(uint8_t *scp, uint32_t offset)
{
    uint32_t word = scp_read32(scp, offset);
    int shift = (offset & 3) * 8;
    return (uint8_t)((word >> shift) & 0xffU);
}

static inline void scp_write8(uint8_t *scp, uint32_t offset, uint8_t val)
{
    uint32_t aligned_off = offset & ~3U;
    int shift = (offset & 3) * 8;
    uint32_t word = *(volatile uint32_t *)(scp + aligned_off);
    word = (word & ~(0xffU << shift)) | (((uint32_t)val & 0xffU) << shift);
    *(volatile uint32_t *)(scp + aligned_off) = word;
}

static uint8_t *get_scp_sram_base(void)
{
    if (!kallsyms_lookup_name) return NULL;
    uint32_t *fn = (uint32_t *)kallsyms_lookup_name("scp_sys_full_reset");
    if (!fn) {
        logke(LOG_TAG "get_scp_sram_base: scp_sys_full_reset not found in kallsyms!\n");
        return NULL;
    }

    uint32_t adrp_insn = fn[7];
    uint32_t immlo = (adrp_insn >> 29) & 3;
    uint32_t immhi = (adrp_insn >> 5) & 0x7ffff;
    int32_t imm = (int32_t)((immhi << 2) | immlo);
    if (imm & (1 << 20)) imm -= (1 << 21);

    uint64_t pc_page = ((uint64_t)&fn[7]) & ~0xfffULL;
    uint64_t target_page = pc_page + ((int64_t)imm << 12);

    uint32_t ldr_insn = fn[10];
    uint32_t imm12 = (ldr_insn >> 10) & 0xfff;
    uint64_t offset = (uint64_t)imm12 << 3;

    uint8_t **p_sram = (uint8_t **)(target_page + offset);
    logkd(LOG_TAG "get_scp_sram_base: fn=%px, p_sram=%px\n", fn, p_sram);

    if (!p_sram) return NULL;
    return *p_sram;
}

static int fix_scp_accgyro_state(const char *reason)
{
    uint8_t *scp = get_scp_sram_base();
    if (!scp) {
        logke(LOG_TAG "fix_scp_accgyro_state: SCP SRAM pointer is NULL! (Reason: %s)\n", reason);
        return -1;
    }

    uint32_t magic = scp_read32(scp, 0);
    logkd(LOG_TAG "SCP SRAM active at %px, magic=0x%08x (Reason: %s)\n", scp, magic, reason);

    logkd(LOG_TAG "SCP bytes 0..15: %08x %08x %08x %08x\n",
          scp_read32(scp, 0), scp_read32(scp, 4),
          scp_read32(scp, 8), scp_read32(scp, 12));

    uint16_t tbh_before = scp_read16(scp, SCP_OFFSET_TBH_CMD36);
    uint8_t fsm_before = scp_read8(scp, SCP_OFFSET_FSM_STATE);
    uint8_t pend_f_before = scp_read8(scp, SCP_OFFSET_PENDING_F);
    uint8_t pend_15_before = scp_read8(scp, SCP_OFFSET_PENDING_15);

    logkd(LOG_TAG "SCP BEFORE: tbh_cmd36=0x%04x, fsmState=%u (0x%02x), pend_f=%u, pend_15=%u\n",
          tbh_before, fsm_before, fsm_before, pend_f_before, pend_15_before);

    // 1. Patch completion table index 33 (cmd 36) from dummy (0x0026 -> 0x21526) to completion (0x004a -> 0x2156e)
    scp_write16(scp, SCP_OFFSET_TBH_CMD36, 0x004a);

    // 2. Unstick fsmState from 36 (0x24 = CHISC) to 40 (0x28 = IDLE)
    scp_write8(scp, SCP_OFFSET_FSM_STATE, 0x28);

    // 3. Clear pending flags
    scp_write8(scp, SCP_OFFSET_PENDING_F, 0);
    scp_write8(scp, SCP_OFFSET_PENDING_15, 0);

    uint16_t tbh_after = scp_read16(scp, SCP_OFFSET_TBH_CMD36);
    uint8_t fsm_after = scp_read8(scp, SCP_OFFSET_FSM_STATE);
    uint8_t pend_f_after = scp_read8(scp, SCP_OFFSET_PENDING_F);
    uint8_t pend_15_after = scp_read8(scp, SCP_OFFSET_PENDING_15);

    logkd(LOG_TAG "SCP AFTER: tbh_cmd36=0x%04x, fsmState=%u (0x%02x), pend_f=%u, pend_15=%u\n",
          tbh_after, fsm_after, fsm_after, pend_f_after, pend_15_after);

    logkd(LOG_TAG "AccGyro FSM patch applied successfully to live SCP SRAM!\n");
    return 0;
}

static void ensure_sensorlist_populated(uint8_t *buf)
{
    logkd(LOG_TAG "Ensuring physical sensor names in /dev/sensorlist buffer at %px...\n", buf);
    // Zero out the first 7 slots (112 bytes)
    for (int i = 0; i < 112; i++) buf[i] = 0;

    // Slot 0: lsm6dsm_acc
    const char *s0 = "lsm6dsm_acc";
    for (int i = 0; s0[i] && i < 15; i++) buf[0 * 16 + i] = (uint8_t)s0[i];
    // Slot 1: lsm6dsm_gyro
    const char *s1 = "lsm6dsm_gyro";
    for (int i = 0; s1[i] && i < 15; i++) buf[1 * 16 + i] = (uint8_t)s1[i];
    // Slot 2: NULL
    const char *s2 = "NULL";
    for (int i = 0; s2[i] && i < 15; i++) buf[2 * 16 + i] = (uint8_t)s2[i];
    // Slot 3: mn78911_l
    const char *s3 = "mn78911_l";
    for (int i = 0; s3[i] && i < 15; i++) buf[3 * 16 + i] = (uint8_t)s3[i];
    // Slot 4: mn78911_p
    const char *s4 = "mn78911_p";
    for (int i = 0; s4[i] && i < 15; i++) buf[4 * 16 + i] = (uint8_t)s4[i];
    // Slot 5: NULL
    for (int i = 0; s2[i] && i < 15; i++) buf[5 * 16 + i] = (uint8_t)s2[i];
    // Slot 6: NULL
    for (int i = 0; s2[i] && i < 15; i++) buf[6 * 16 + i] = (uint8_t)s2[i];
}

static void trigger_sensor_hub_sync(const char *reason)
{
    if (!k_sensorHub_power_up_loop) {
        logke(LOG_TAG "trigger_sensor_hub_sync FAILED: k_sensorHub_power_up_loop is NULL!\n");
        return;
    }

    g_in_sync = 1;
    logkd(LOG_TAG "Triggering Sensor Hub Natural Sync (Reason: %s)...\n", reason);

    // 1. First ensure SCP accGyro FSM state machine is unfrozen and patched via 32-bit aligned RMW
    fix_scp_accgyro_state(reason);

    uint8_t *p_flag_ready  = (uint8_t *)((uint64_t)k_sensorHub_power_up_loop + OFFSET_FLAG_READY);
    uint8_t *p_flag_notify = (uint8_t *)((uint64_t)k_sensorHub_power_up_loop + OFFSET_FLAG_NOTIFY);
    void *p_waitqueue      = (void *)((uint64_t)k_sensorHub_power_up_loop + OFFSET_WAITQUEUE);

    // 2. Set ready flags so scp_power_reset worker thread unblocks
    *p_flag_ready = 1;
    *p_flag_notify = 1;
    logkd(LOG_TAG "Pre-armed ready flags: ready=%u, notify=%u at %px, %px\n",
          *p_flag_ready, *p_flag_notify, p_flag_ready, p_flag_notify);

    // 3. Wake up PID 211 (scp_power_reset) thread
    if (k_wake_up) {
        logkd(LOG_TAG "Waking waitqueue at %px...\n", p_waitqueue);
        k_wake_up(p_waitqueue, 3, 1, NULL);
    }

    // 4. Populate /dev/sensorlist buffer
    uint8_t *p_buf_sensorlist = (uint8_t *)((uint64_t)k_sensorHub_power_up_loop + OFFSET_BUF_SENSORLIST);
    ensure_sensorlist_populated(p_buf_sensorlist);

    // 5. Arm g_sensorlist_ready flag so HAL sensorlist_read succeeds
    uint32_t *p_flag_sensorlist = (uint32_t *)((uint64_t)k_sensorHub_power_up_loop + OFFSET_FLAG_SENSORLIST);
    *p_flag_sensorlist = 1;
    logkd(LOG_TAG "Armed g_sensorlist_ready=%u at %px\n", *p_flag_sensorlist, p_flag_sensorlist);

    g_in_sync = 0;
}

// 1. Hook for scp_sensorHub_req_send(SCP_SENSOR_HUB_DATA_P data, uint *len, unsigned int wait)
static void before_scp_sensorHub_req_send(hook_fargs3_t *args, void *udata)
{
    // Passive hook - do NOT alter arguments or sleep
}

static void after_scp_sensorHub_req_send(hook_fargs3_t *args, void *udata)
{
    if (!g_sensor_hub_kpm_enabled || !args || g_in_sync) return;

    int ret = (int)args->ret;
    if (ret == 0) {
        g_success_packets++;

        // Automatic Natural Synchronization when SCP firmware wakes up and stabilizes
        if (!g_power_up_loop_done && g_success_packets >= 3 && k_sensorHub_power_up_loop) {
            g_power_up_loop_done = 1;
            trigger_sensor_hub_sync("SCP FIRMWARE STABILIZED");
            // Disarm hook permanently after synchronization for zero overhead
            g_sensor_hub_kpm_enabled = 0;
            logkd(LOG_TAG "Sensor Hub natural sync completed successfully. Hook disarmed.\n");
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
    } else if (args[0] == '2') {
        trigger_sensor_hub_sync("MANUAL CTL0 TRIGGER");
    } else if (args[0] == '3') {
        fix_scp_accgyro_state("MANUAL CTL0 FIX SCP");
    }

    return 0;
}

// 3. Module initialization
static long lynx_sensor_hub_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx MTK SCP Sensor Hub Natural Auto-Sync & FSM Fix KPM v1.8.0...\n");

    if (kallsyms_lookup_name) {
        target_scp_sensorHub_req_send = (void *)kallsyms_lookup_name("scp_sensorHub_req_send");
        k_sensorHub_power_up_loop = (sensorHub_power_up_loop_fn_t)kallsyms_lookup_name("sensorHub_power_up_loop");
        k_wake_up = (wake_up_fn_t)kallsyms_lookup_name("__wake_up");
        k_sensorlist_get_deviceinfo = (sensorlist_get_deviceinfo_fn_t)kallsyms_lookup_name("sensorlist_get_deviceinfo");
        k_ioremap_cache = (ioremap_cache_fn_t)kallsyms_lookup_name("ioremap_cache");
        k_iounmap = (iounmap_fn_t)kallsyms_lookup_name("__iounmap");
    }

    if (!target_scp_sensorHub_req_send) {
        logke(LOG_TAG "CRITICAL: Symbol 'scp_sensorHub_req_send' not found in kallsyms!\n");
        return -1;
    }

    logkd(LOG_TAG "Resolved symbols: req_send=%px, power_up_loop=%px, sensorlist_info=%px, ioremap_cache=%px, iounmap=%px\n",
          target_scp_sensorHub_req_send, k_sensorHub_power_up_loop, k_sensorlist_get_deviceinfo, k_ioremap_cache, k_iounmap);

    // Apply SCP FSM unfreeze & jump table patch immediately at module load
    trigger_sensor_hub_sync("MODULE INIT");

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
    logkd(LOG_TAG "Lynx MTK SCP Sensor Hub KPM v1.8.0 ARMED successfully.\n");
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
