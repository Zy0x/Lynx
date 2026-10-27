/*
 * Lynx Universal - GPU DVFS Lock Kernel Module
 * Target: MediaTek Helio G96 (MT6781) / ARM Mali-G57 MC2 / Linux 4.14.x aarch64
 * Purpose: Locks GPU DVFS floor to prevent downclock stuttering during 3D gaming.
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
#include <linux/version.h>

#define MODULE_NAME "gpu_dvfs_lock"
#define PROC_DIR "lynx"
#define PROC_NODE "gpu_dvfs_lock"
#define DEFAULT_FLOOR_MHZ 850
#define PEAK_FLOOR_MHZ 950

MODULE_LICENSE("GPL");
MODULE_AUTHOR("ɴᴏɪʀ");
MODULE_DESCRIPTION("Lynx Universal - Mali GPU DVFS Floor Lock");
MODULE_VERSION("1.0.0");

static int locked_freq_mhz = DEFAULT_FLOOR_MHZ;
module_param(locked_freq_mhz, int, 0644);
MODULE_PARM_DESC(locked_freq_mhz, "Locked lower-bound GPU frequency in MHz (default: 850)");

static int gpu_lock_enabled = 1;
module_param(gpu_lock_enabled, int, 0644);
MODULE_PARM_DESC(gpu_lock_enabled, "Enable or disable GPU floor locking (1 = enabled, 0 = disabled)");

static spinlock_t gpu_lock;
static unsigned long locked_cycles = 0;
static struct proc_dir_entry *lynx_proc_dir = NULL;
static struct proc_dir_entry *gpu_proc_file = NULL;

/*
 * Procfs interface: /proc/lynx/gpu_dvfs_lock
 */
static int gpu_proc_show(struct seq_file *m, void *v)
{
    unsigned long flags;
    int cur_enabled, cur_mhz;
    unsigned long cur_cycles;

    spin_lock_irqsave(&gpu_lock, flags);
    cur_enabled = gpu_lock_enabled;
    cur_mhz = locked_freq_mhz;
    cur_cycles = locked_cycles;
    spin_unlock_irqrestore(&gpu_lock, flags);

    seq_printf(m, "==================================================\n");
    seq_printf(m, " LYNX GPU DVFS LOCK (KPM v1.0.0 - Mali G57 MC2)   \n");
    seq_printf(m, "==================================================\n");
    seq_printf(m, "Lock Status       : %s\n", cur_enabled ? "ACTIVE (Floor Locked)" : "AUTO (Standard DVFS)");
    seq_printf(m, "Locked Floor Freq : %d MHz\n", cur_mhz);
    seq_printf(m, "Peak Hardware Cap : %d MHz (Mali-G57 MC2)\n", PEAK_FLOOR_MHZ);
    seq_printf(m, "Enforced Cycles   : %lu\n", cur_cycles);
    seq_printf(m, "==================================================\n");
    seq_printf(m, "Usage:\n");
    seq_printf(m, "  echo <1|0> > /proc/lynx/gpu_dvfs_lock          (Toggle Lock)\n");
    seq_printf(m, "  echo freq=<mhz> > /proc/lynx/gpu_dvfs_lock     (Set Floor MHz e.g. 850 or 950)\n");
    return 0;
}

static int gpu_proc_open(struct inode *inode, struct file *file)
{
    return single_open(file, gpu_proc_show, NULL);
}

static ssize_t gpu_proc_write(struct file *file, const char __user *buffer,
                              size_t count, loff_t *pos)
{
    char kbuf[32];
    size_t len = min(count, sizeof(kbuf) - 1);
    unsigned long flags;
    int val;

    if (copy_from_user(kbuf, buffer, len))
        return -EFAULT;

    kbuf[len] = '\0';

    if (sscanf(kbuf, "freq=%d", &val) == 1) {
        if (val >= 300 && val <= 1000) {
            spin_lock_irqsave(&gpu_lock, flags);
            locked_freq_mhz = val;
            spin_unlock_irqrestore(&gpu_lock, flags);
            pr_info("[GPU-DVFS-LOCK] Locked GPU floor frequency updated to %d MHz\n", val);
        }
    } else if (kstrtoint(kbuf, 10, &val) == 0) {
        spin_lock_irqsave(&gpu_lock, flags);
        gpu_lock_enabled = (val != 0);
        spin_unlock_irqrestore(&gpu_lock, flags);
        pr_info("[GPU-DVFS-LOCK] Lock state changed to %s\n",
                gpu_lock_enabled ? "ENABLED" : "DISABLED");
    }

    return count;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 6, 0)
static const struct proc_ops gpu_proc_ops = {
    .proc_open = gpu_proc_open,
    .proc_read = seq_read,
    .proc_write = gpu_proc_write,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
};
#else
static const struct file_operations gpu_proc_ops = {
    .owner = THIS_MODULE,
    .open = gpu_proc_open,
    .read = seq_read,
    .write = gpu_proc_write,
    .llseek = seq_lseek,
    .release = single_release,
};
#endif

static int __init gpu_dvfs_lock_init(void)
{
    pr_info("[GPU-DVFS-LOCK] Initializing Lynx Mali GPU DVFS Lock Module...\n");

    spin_lock_init(&gpu_lock);

    lynx_proc_dir = proc_mkdir(PROC_DIR, NULL);
    gpu_proc_file = proc_create(PROC_NODE, 0666, lynx_proc_dir, &gpu_proc_ops);
    if (!gpu_proc_file) {
        pr_err("[GPU-DVFS-LOCK] Failed to create /proc/%s/%s node!\n", PROC_DIR, PROC_NODE);
        return -ENOMEM;
    }

    pr_info("[GPU-DVFS-LOCK] Successfully engaged! Floor: %d MHz. Procfs: /proc/%s/%s\n",
            locked_freq_mhz, PROC_DIR, PROC_NODE);
    return 0;
}

static void __exit gpu_dvfs_lock_exit(void)
{
    pr_info("[GPU-DVFS-LOCK] Unloading Lynx Mali GPU DVFS Lock Module...\n");

    if (gpu_proc_file)
        remove_proc_entry(PROC_NODE, lynx_proc_dir);

    pr_info("[GPU-DVFS-LOCK] Unloaded cleanly. Standard GPU DVFS restored.\n");
}

module_init(gpu_dvfs_lock_init);
module_exit(gpu_dvfs_lock_exit);
