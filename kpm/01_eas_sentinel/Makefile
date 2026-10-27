MODULE_NAME = eas_sentinel
obj-m += $(MODULE_NAME).o

# Target Architecture
ARCH ?= arm64
CROSS_COMPILE ?= aarch64-linux-android-

# Default Kernel Source Tree Path (Configure to your kernel source or headers)
KDIR ?= /lib/modules/$(shell uname -r)/build

all:
	$(MAKE) -C $(KDIR) M=$(PWD) ARCH=$(ARCH) CROSS_COMPILE=$(CROSS_COMPILE) modules

clean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean
	rm -f *.o *.ko *.mod.c *.mod *.order *.symvers
