#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <stdint.h>
#include <errno.h>

#define __NR_supercall 45
#define SUPERCALL_KPM_LOAD 0x1020
#define SUPERCALL_KPM_UNLOAD 0x1021
#define SUPERCALL_KPM_CONTROL 0x1022
#define SUPERCALL_KPM_NUMS 0x1030
#define SUPERCALL_KPM_LIST 0x1031
#define SUPERCALL_KPM_INFO 0x1032

#define KPM_LOAD_RESULT_MAGIC 0x4b504d52
#define KPM_LOAD_ERROR_MESSAGE_LEN 160

struct kpm_load_result {
    unsigned int magic;
    unsigned int size;
    int code;
    char message[KPM_LOAD_ERROR_MESSAGE_LEN];
};

static long make_cmd(int subcmd) {
    return ((long)0x000d03 << 32) | (0x1158 << 16) | subcmd;
}

int main(int argc, char **argv) {
    const char *action = (argc > 1) ? argv[1] : "list";
    const char *skey = getenv("SUPERKEY") ? getenv("SUPERKEY") : "123456";

    if (strcmp(action, "unload") == 0) {
        const char *name = (argc > 2) ? argv[2] : "lynx_gpu_uv";
        printf("Unloading KPM: %s\n", name);
        long ret = syscall(__NR_supercall, skey, make_cmd(SUPERCALL_KPM_UNLOAD), name, NULL);
        printf("Unload syscall return: %ld (errno=%d: %s)\n", ret, errno, strerror(errno));
        return ret == 0 ? 0 : 1;
    } else if (strcmp(action, "ctl") == 0) {
        const char *name = (argc > 2) ? argv[2] : "lynx_oc_uv";
        const char *ctl_args = (argc > 3) ? argv[3] : "help";
        char out_msg[256];
        memset(out_msg, 0, sizeof(out_msg));
        printf("Sending CTL to KPM %s: '%s'\n", name, ctl_args);
        long ret = syscall(__NR_supercall, skey, make_cmd(SUPERCALL_KPM_CONTROL), name, ctl_args, out_msg, sizeof(out_msg));
        printf("Control syscall return: %ld (errno=%d: %s)\n", ret, errno, strerror(errno));
        printf("Out message: '%s'\n", out_msg);
        return ret == 0 ? 0 : 1;
    } else if (strcmp(action, "list") == 0) {
        char buf[1024];
        memset(buf, 0, sizeof(buf));
        long ret = syscall(__NR_supercall, skey, make_cmd(SUPERCALL_KPM_LIST), buf, sizeof(buf));
        printf("List syscall return: %ld (errno=%d: %s)\n", ret, errno, strerror(errno));
        if (ret >= 0) {
            printf("Active KPMs:\n%s\n", buf);
        }
        return ret >= 0 ? 0 : 1;
    } else { // load
        const char *path = (strcmp(action, "load") == 0 && argc > 2) ? argv[2] : action;
        const char *args = (argc > 3) ? argv[3] : "";

        printf("Loading KPM: %s\n", path);
        struct kpm_load_result result;
        memset(&result, 0, sizeof(result));
        result.magic = KPM_LOAD_RESULT_MAGIC;
        result.size = sizeof(result);

        long ret = syscall(__NR_supercall, skey, make_cmd(SUPERCALL_KPM_LOAD), path, args, &result);
        printf("Load syscall return: %ld (errno=%d: %s)\n", ret, errno, strerror(errno));
        printf("result.magic: 0x%x, code: %d, message: '%s'\n", result.magic, result.code, result.message);
        return ret == 0 ? 0 : 1;
    }
}
