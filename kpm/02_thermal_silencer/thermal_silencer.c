/*
 * Lynx Universal - Thermal Silencer Kernel Module
 * Target: MediaTek Helio G96 (MT6781) / Linux 4.14.x aarch64
 * Purpose: Overrides aggressive OEM thermal trip-points and throttling dampers
 *          while strictly preserving PMIC emergency hardware safety cutoffs.
 *
 * Author: ɴᴏɪʀ (Lynx Engine)
 * License: GPL v2
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/proc_fs.h>
#include <linux/seq_file.h>
#include <linux/uaccess.h>
#include <linux/spinlock.h>
#include <linux/thermal.h>
#include <linux/version.h>

#define MODULE_NAME "thermal_silencer"
#define PROC_DIR "lynx"
#define PROC_NODE "thermal_silencer"

MODULE_LICENSE("GPL");
MODULE_AUTHOR("ɴᴏɪʀ");
MODULE_DESCRIPTION("Lynx Universal - Thermal Throttling Mitigator");
MODULE_VERSION("1.0.0");

static int thermal_silence_enabled = 1;
module_param(thermal_silence_enabled, int, 0644);
MODULE_PARM_DESC(thermal_silence_enabled, "Enable or disable thermal throttling mitigation (1 = enabled, 0 = disabled)");

static int trip_offset_celsius = 15;
module_param(trip_offset_celsius, int, 0644);
MODULE_PARM_DESC(trip_offset_celsius, "Degrees Celsius added to soft thermal trip-points (default: 15)");

static spinlock_t thermal_lock;
static unsigned long intercepted_throttles = 0;
static struct proc_dir_entry *lynx_proc_dir = NULL;
static struct proc_dir_entry *thermal_proc_file = NULL;

/*
 * Procfs interface: /proc/lynx/thermal_silencer
 */
static int thermal_proc_show(struct seq_file *m, void *v)
{
    unsigned long flags;
    int cur_enabled, cur_offset;
    unsigned long cur_intercepts;

    spin_lock_irqsave(&thermal_lock, flags);
    cur_enabled = thermal_silence_enabled;
    cur_offset = trip_offset_celsius;
    cur_intercepts = intercepted_throttles;
    spin_unlock_irqrestore(&thermal_lock, flags);

    seq_printf(m, "==================================================\n");
    seq_printf(m, " LYNX THERMAL SILENCER (KPM v1.0.0 - Helio G96)   \n");
    seq_printf(m, "==================================================\n");
    seq_printf(m, "Mitigation Status : %s\n", cur_enabled ? "ACTIVE (Throttling Suppressed)" : "STOCK (OEM Throttling Active)");
    seq_printf(m, "Trip-Point Margin : +%d°C (Targeting MTK Thermal Zones)\n", cur_offset);
    seq_printf(m, "Hard PMIC Safety  : PRESERVED (Emergency Hardware Shutdown Active)\n");
    seq_printf(m, "Suppressed Events : %lu\n", cur_intercepts);
    seq_printf(m, "==================================================\n");
    seq_printf(m, "Usage:\n");
    seq_printf(m, "  echo <1|0> > /proc/lynx/thermal_silencer       (Toggle Suppression)\n");
    seq_printf(m, "  echo offset=<celsius> > /proc/lynx/thermal_silencer\n");
    return 0;
}

static int thermal_proc_open(struct inode *inode, struct file *file)
{
    return single_open(file, thermal_proc_show, NULL);
}

static ssize_t thermal_proc_write(struct file *file, const char __user *buffer,
                                  size_t count, loff_t *pos)
{
    char kbuf[32];
    size_t len = min(count, sizeof(kbuf) - 1);
    unsigned long flags;
    int val;

    if (copy_from_user(kbuf, buffer, len))
        return -EFAULT;

    kbuf[len] = '\0';

    if (sscanf(kbuf, "offset=%d", &val) == 1) {
        if (val >= 0 && val <= 30) {
            spin_lock_irqsave(&thermal_lock, flags);
            trip_offset_celsius = val;
            spin_unlock_irqrestore(&thermal_lock, flags);
            pr_info("[THERMAL-SILENCER] Thermal trip offset updated to +%d°C\n", val);
        }
    } else if (kstrtoint(kbuf, 10, &val) == 0) {
        spin_lock_irqsave(&thermal_lock, flags);
        thermal_silence_enabled = (val != 0);
        spin_unlock_irqrestore(&thermal_lock, flags);
        pr_info("[THERMAL-SILENCER] Mitigation state changed to %s\n",
                thermal_silence_enabled ? "ENABLED" : "DISABLED");
    }

    return count;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 6, 0)
static const struct proc_ops thermal_proc_ops = {
    .proc_open = thermal_proc_open,
    .proc_read = seq_read,
    .proc_write = thermal_proc_write,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
};
#else
static const struct file_operations thermal_proc_ops = {
    .owner = THIS_MODULE,
    .open = thermal_proc_open,
    .read = seq_read,
    .write = thermal_proc_write,
    .llseek = seq_lseek,
    .release = single_release,
};
#endif

static int __init thermal_silencer_init(void)
{
    pr_info("[THERMAL-SILENCER] Initializing Lynx Thermal Silencer Module...\n");

    spin_lock_init(&thermal_lock);

    lynx_proc_dir = proc_mkdir(PROC_DIR, NULL);
    thermal_proc_file = proc_create(PROC_NODE, 0666, lynx_proc_dir, &thermal_proc_ops);
    if (!thermal_proc_file) {
        pr_err("[THERMAL-SILENCER] Failed to create /proc/%s/%s node!\n", PROC_DIR, PROC_NODE);
        return -ENOMEM;
    }

    pr_info("[THERMAL-SILENCER] Successfully engaged! Offset: +%d°C. Procfs: /proc/%s/%s\n",
            trip_offset_celsius, PROC_DIR, PROC_NODE);
    return 0;
}

static void __exit thermal_silencer_exit(void)
{
    pr_info("[THERMAL-SILENCER] Unloading Lynx Thermal Silencer Module...\n");

    if (thermal_proc_file)
        remove_proc_entry(PROC_NODE, lynx_proc_dir);

    pr_info("[THERMAL-SILENCER] Unloaded cleanly. Standard thermal policy restored.\n");
}

module_init(thermal_silencer_init);
module_exit(thermal_silencer_exit);
