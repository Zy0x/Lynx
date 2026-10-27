MODULE_NAME = gpu_dvfs_lock
obj-m += $(MODULE_NAME).o

ARCH ?= arm64
CROSS_COMPILE ?= aarch64-linux-android-
KDIR ?= /lib/modules/$(shell uname -r)/build

all:
	$(MAKE) -C $(KDIR) M=$(PWD) ARCH=$(ARCH) CROSS_COMPILE=$(CROSS_COMPILE) modules

clean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean
	rm -f *.o *.ko *.mod.c *.mod *.order *.symvers
