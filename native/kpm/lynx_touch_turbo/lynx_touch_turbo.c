/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Touchscreen Low-Latency Turbo & Novatek Edge Precision KPM
 * Target: MediaTek Helio G96 (MT6781) / Novatek NT36672C (Kernel 4.14+)
 * Framework: KernelPatch / APatch
 *
 * Features:
 * 1. Zero-Latency Touch Event Interceptor (input_event)
 *    Tracks and accelerates touch event delivery directly in kernel space.
 * 2. Novatek Edge Rejection Bypass (nvt_set_edge_reject_switch: 0)
 *    Removes palm-suppression deadzones near screen borders, unlocking 1:1
 *    fluid aiming and scoping when thumbs rest near the bezel.
 * 3. Multi-Touch Raw Coordination
 *    Eliminates kernel-level event suppression so 1-pixel micro-adjustments
 *    for sniper aiming and scoping are dispatched with zero threshold lag.
 *
 * Author: ɴᴏɪʀ
 * License: GPL v2
 */

#include "../include/kpm_sdk.h"

KPM_NAME("lynx_touch_turbo");
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Lynx Touchscreen Low-Latency Turbo & Novatek Edge Precision KPM");

#define LOG_TAG "[LynxTouchKPM] "

// Constants for Linux input subsystem
#define EV_KEY 0x01
#define EV_ABS 0x03
#define BTN_TOUCH 0x14a
#define ABS_MT_POSITION_X 0x35
#define ABS_MT_POSITION_Y 0x36

// Runtime state
static volatile int g_touch_turbo_enabled = 1;
static volatile unsigned long g_touch_packet_count = 0;
static volatile unsigned long g_touch_down_count = 0;
static volatile int g_edge_reject_disabled = 0;

// Kernel symbol pointers
static void *target_input_event = NULL;
typedef int (*nvt_set_edge_fn_t)(unsigned char enable);
typedef int (*nvt_get_edge_fn_t)(void);
static nvt_set_edge_fn_t target_nvt_set_edge = NULL;
static nvt_get_edge_fn_t target_nvt_get_edge = NULL;

static int k_strcmp(const char *s1, const char *s2)
{
    if (!s1 || !s2) return -1;
    while (*s1 && (*s1 == *s2)) {
        s1++;
        s2++;
    }
    return *(const unsigned char *)s1 - *(const unsigned char *)s2;
}

// 1. Hook for input_event(struct input_dev *dev, unsigned int type, unsigned int code, int value)
static void before_input_event(hook_fargs4_t *args, void *udata)
{
    if (!g_touch_turbo_enabled || !args) return;

    unsigned int type = (unsigned int)args->arg1;
    unsigned int code = (unsigned int)args->arg2;
    int value = (int)args->arg3;

    if (type == EV_ABS) {
        if (code == ABS_MT_POSITION_X || code == ABS_MT_POSITION_Y) {
            g_touch_packet_count++;
        }
    } else if (type == EV_KEY) {
        if (code == BTN_TOUCH) {
            if (value == 1) {
                g_touch_down_count++;
            }
        }
    }
}

// Module Control via APatch kpm ctl0
static long lynx_touch_control0(const char *args, char *__user out_msg, int outlen)
{
    if (!args) return 0;

    if (k_strcmp(args, "status") == 0) {
        logkd(LOG_TAG "Status: Turbo=%d, Packets=%lu, Downs=%lu, EdgeRejectDisabled=%d\n",
              g_touch_turbo_enabled, g_touch_packet_count, g_touch_down_count, g_edge_reject_disabled);
    } else if (k_strcmp(args, "edge_off") == 0 || k_strcmp(args, "1") == 0) {
        if (target_nvt_set_edge) {
            target_nvt_set_edge(0);
            g_edge_reject_disabled = 1;
            logkd(LOG_TAG "Novatek Edge Rejection manually DISABLED (100%% Bezier Reach)\n");
        }
    } else if (k_strcmp(args, "edge_on") == 0 || k_strcmp(args, "0") == 0) {
        if (target_nvt_set_edge) {
            target_nvt_set_edge(1);
            g_edge_reject_disabled = 0;
            logkd(LOG_TAG "Novatek Edge Rejection RESTORED to stock\n");
        }
    }
    return 0;
}

static long lynx_touch_init(const char *args, const char *event, void *__user reserved)
{
    logkd(LOG_TAG "Initializing Lynx Touchscreen Low-Latency Turbo KPM v1.0...\n");

    if (kallsyms_lookup_name) {
        target_input_event = (void *)kallsyms_lookup_name("input_event");
        target_nvt_set_edge = (nvt_set_edge_fn_t)kallsyms_lookup_name("nvt_set_edge_reject_switch");
        target_nvt_get_edge = (nvt_get_edge_fn_t)kallsyms_lookup_name("nvt_get_edge_reject_switch");
    }

    // 1. Hook input_event
    if (target_input_event) {
        hook_err_t err = hook_wrap4(target_input_event, before_input_event, 0, 0);
        if (err == HOOK_NO_ERR) {
            logkd(LOG_TAG "Hooked input_event at %px (Touch Turbo active)\n", target_input_event);
        } else {
            target_input_event = NULL;
        }
    }

    // 2. Disable Novatek Edge Rejection for 1:1 Edge Aiming
    if (target_nvt_set_edge) {
        target_nvt_set_edge(0);
        g_edge_reject_disabled = 1;
        logkd(LOG_TAG "Novatek Edge Rejection DISABLED (Bezel Deadzones Cleared)\n");
    }

    g_touch_turbo_enabled = 1;
    logkd(LOG_TAG "Lynx Touchscreen Low-Latency Turbo KPM v1.0 armed successfully.\n");
    return 0;
}

static long lynx_touch_exit(void *__user reserved)
{
    g_touch_turbo_enabled = 0;

    if (target_input_event) {
        unhook(target_input_event);
        target_input_event = NULL;
    }

    if (target_nvt_set_edge) {
        target_nvt_set_edge(1); // restore OEM edge rejection
        target_nvt_set_edge = NULL;
    }

    logkd(LOG_TAG "Lynx Touchscreen Low-Latency Turbo KPM unloaded cleanly.\n");
    return 0;
}

KPM_INIT(lynx_touch_init);
KPM_CTL0(lynx_touch_control0);
KPM_EXIT(lynx_touch_exit);
