/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Lynx Universal - Self-Contained KernelPatch Module (KPM) SDK Header
 * Compatible with KernelPatch d03+ (ARM64 Linux 4.14+)
 */

#ifndef _LYNX_KPM_SDK_H_
#define _LYNX_KPM_SDK_H_

typedef unsigned char uint8_t;
typedef unsigned short uint16_t;
typedef unsigned int uint32_t;
typedef unsigned long long uint64_t;
typedef signed int int32_t;
typedef signed long long int64_t;
typedef unsigned long size_t;

#ifndef NULL
#define NULL ((void *)0)
#endif

#define __user

#define __section(S) __attribute__((section(S)))
#define __used __attribute__((used))
#define __aligned(x) __attribute__((aligned(x)))

#define KPM_NAME(x) \
    static const char __kpm_info_name[] __used __section(".kpm.info") __aligned(1) = "name=" x
#define KPM_VERSION(x) \
    static const char __kpm_info_version[] __used __section(".kpm.info") __aligned(1) = "version=" x
#define KPM_LICENSE(x) \
    static const char __kpm_info_license[] __used __section(".kpm.info") __aligned(1) = "license=" x
#define KPM_AUTHOR(x) \
    static const char __kpm_info_author[] __used __section(".kpm.info") __aligned(1) = "author=" x
#define KPM_DESCRIPTION(x) \
    static const char __kpm_info_description[] __used __section(".kpm.info") __aligned(1) = "description=" x

typedef long (*kpm_init_fn_t)(const char *args, const char *event, void *__user reserved);
typedef long (*kpm_ctl0_fn_t)(const char *args, char *__user out_msg, int outlen);
typedef long (*kpm_exit_fn_t)(void *__user reserved);

#define KPM_INIT(fn) \
    static kpm_init_fn_t __kpm_initcall_##fn __used __section(".kpm.init") __aligned(8) = fn
#define KPM_CTL0(fn) \
    static kpm_ctl0_fn_t __kpm_ctlmodule_##fn __used __section(".kpm.ctl0") __aligned(8) = fn
#define KPM_EXIT(fn) \
    static kpm_exit_fn_t __kpm_exitcall_##fn __used __section(".kpm.exit") __aligned(8) = fn

typedef enum {
    HOOK_NO_ERR = 0,
} hook_err_t;

typedef struct {
    uint64_t data[8];
} hook_local_t;

typedef struct {
    void *chain;
    int skip_origin;
    int _pad;
    hook_local_t local;
    uint64_t ret;
    uint64_t arg0;
    uint64_t arg1;
    uint64_t arg2;
    uint64_t arg3;
    uint64_t arg4;
    uint64_t arg5;
    uint64_t arg6;
    uint64_t arg7;
} hook_fargs_t;

typedef hook_fargs_t hook_fargs0_t;
typedef hook_fargs_t hook_fargs1_t;
typedef hook_fargs_t hook_fargs2_t;
typedef hook_fargs_t hook_fargs3_t;
typedef hook_fargs_t hook_fargs4_t;

extern int (*printk)(const char *fmt, ...);
extern unsigned long (*kallsyms_lookup_name)(const char *name);
extern hook_err_t hook_wrap(void *func, int32_t argno, void *before, void *after, void *udata);
extern void unhook(void *func);

#define hook_wrap0(func, before, after, udata) hook_wrap((func), 0, (void *)(before), (void *)(after), (void *)(udata))
#define hook_wrap1(func, before, after, udata) hook_wrap((func), 1, (void *)(before), (void *)(after), (void *)(udata))
#define hook_wrap2(func, before, after, udata) hook_wrap((func), 2, (void *)(before), (void *)(after), (void *)(udata))
#define hook_wrap3(func, before, after, udata) hook_wrap((func), 3, (void *)(before), (void *)(after), (void *)(udata))
#define hook_wrap4(func, before, after, udata) hook_wrap((func), 4, (void *)(before), (void *)(after), (void *)(udata))

#define logkd(fmt, ...) printk("\0016" fmt, ##__VA_ARGS__)
#define logke(fmt, ...) printk("\0013" fmt, ##__VA_ARGS__)

#endif /* _LYNX_KPM_SDK_H_ */
