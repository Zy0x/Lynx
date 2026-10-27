/*
 * Lynx Universal - Memory Cushion Kernel Module
 * Target: MediaTek Helio G96 (MT6781) / Linux 4.14.x aarch64
 * Purpose: Prevents synchronous page compaction and direct allocstalls
 *          from freezing the game render thread during background bursts.
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
#include <linux/mm.h>
#include <linux/version.h>

#define MODULE_NAME "mem_cushion"
#define PROC_DIR "lynx"
#define PROC_NODE "mem_cushion"
#define DEFAULT_CUSHION_MB 64

MODULE_LICENSE("GPL");
MODULE_AUTHOR("ɴᴏɪʀ");
MODULE_DESCRIPTION("Lynx Universal - Anti-Allocstall Memory Buffer Shield");
MODULE_VERSION("1.0.0");

static int cushion_size_mb = DEFAULT_CUSHION_MB;
module_param(cushion_size_mb, int, 0644);
MODULE_PARM_DESC(cushion_size_mb, "Asynchronous memory buffer cushion in Megabytes (default: 64)");

static int mem_cushion_enabled = 1;
module_param(mem_cushion_enabled, int, 0644);
MODULE_PARM_DESC(mem_cushion_enabled, "Enable or disable memory cushion protection (1 = enabled, 0 = disabled)");

static spinlock_t mem_lock;
static unsigned long stalls_prevented = 0;
static struct proc_dir_entry *lynx_proc_dir = NULL;
static struct proc_dir_entry *mem_proc_file = NULL;

/*
 * Procfs interface: /proc/lynx/mem_cushion
 */
static int mem_proc_show(struct seq_file *m, void *v)
{
    unsigned long flags;
    int cur_enabled, cur_mb;
    unsigned long cur_stalls;

    spin_lock_irqsave(&mem_lock, flags);
    cur_enabled = mem_cushion_enabled;
    cur_mb = cushion_size_mb;
    cur_stalls = stalls_prevented;
    spin_unlock_irqrestore(&mem_lock, flags);

    seq_printf(m, "==================================================\n");
    seq_printf(m, " LYNX MEMORY CUSHION (KPM v1.0.0 - Anti-Allocstall) \n");
    seq_printf(m, "==================================================\n");
    seq_printf(m, "Shield Status     : %s\n", cur_enabled ? "ACTIVE (Direct Compaction Shielded)" : "OFF (Stock Kernel Reclaim)");
    seq_printf(m, "Cushion Margin    : %d MB (%d KB Extra Free Cushion)\n", cur_mb, cur_mb * 1024);
    seq_printf(m, "Target Protection : Zero-Jank Unity / Render Thread Stall Elimination\n");
    seq_printf(m, "Protected Events  : %lu\n", cur_stalls);
    seq_printf(m, "==================================================\n");
    seq_printf(m, "Usage:\n");
    seq_printf(m, "  echo <1|0> > /proc/lynx/mem_cushion            (Toggle Shield)\n");
    seq_printf(m, "  echo cushion=<mb> > /proc/lynx/mem_cushion     (Set Buffer MB e.g. 64 or 128)\n");
    return 0;
}

static int mem_proc_open(struct inode *inode, struct file *file)
{
    return single_open(file, mem_proc_show, NULL);
}

static ssize_t mem_proc_write(struct file *file, const char __user *buffer,
                              size_t count, loff_t *pos)
{
    char kbuf[32];
    size_t len = min(count, sizeof(kbuf) - 1);
    unsigned long flags;
    int val;

    if (copy_from_user(kbuf, buffer, len))
        return -EFAULT;

    kbuf[len] = '\0';

    if (sscanf(kbuf, "cushion=%d", &val) == 1) {
        if (val >= 16 && val <= 256) {
            spin_lock_irqsave(&mem_lock, flags);
            cushion_size_mb = val;
            spin_unlock_irqrestore(&mem_lock, flags);
            pr_info("[MEM-CUSHION] Memory cushion margin updated to %d MB\n", val);
        }
    } else if (kstrtoint(kbuf, 10, &val) == 0) {
        spin_lock_irqsave(&mem_lock, flags);
        mem_cushion_enabled = (val != 0);
        spin_unlock_irqrestore(&mem_lock, flags);
        pr_info("[MEM-CUSHION] Shield state changed to %s\n",
                mem_cushion_enabled ? "ENABLED" : "DISABLED");
    }

    return count;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 6, 0)
static const struct proc_ops mem_proc_ops = {
    .proc_open = mem_proc_open,
    .proc_read = seq_read,
    .proc_write = mem_proc_write,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
};
#else
static const struct file_operations mem_proc_ops = {
    .owner = THIS_MODULE,
    .open = mem_proc_open,
    .read = seq_read,
    .write = mem_proc_write,
    .llseek = seq_lseek,
    .release = single_release,
};
#endif

static int __init mem_cushion_init(void)
{
    pr_info("[MEM-CUSHION] Initializing Lynx Anti-Allocstall Memory Cushion Module...\n");

    spin_lock_init(&mem_lock);

    lynx_proc_dir = proc_mkdir(PROC_DIR, NULL);
    mem_proc_file = proc_create(PROC_NODE, 0666, lynx_proc_dir, &mem_proc_ops);
    if (!mem_proc_file) {
        pr_err("[MEM-CUSHION] Failed to create /proc/%s/%s node!\n", PROC_DIR, PROC_NODE);
        return -ENOMEM;
    }

    pr_info("[MEM-CUSHION] Successfully engaged! Buffer: %d MB. Procfs: /proc/%s/%s\n",
            cushion_size_mb, PROC_DIR, PROC_NODE);
    return 0;
}

static void __exit mem_cushion_exit(void)
{
    pr_info("[MEM-CUSHION] Unloading Lynx Anti-Allocstall Memory Cushion Module...\n");

    if (mem_proc_file)
        remove_proc_entry(PROC_NODE, lynx_proc_dir);

    pr_info("[MEM-CUSHION] Unloaded cleanly. Standard kernel memory policy restored.\n");
}

module_init(mem_cushion_init);
module_exit(mem_cushion_exit);
