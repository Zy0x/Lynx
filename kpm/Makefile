SUBDIRS = 01_eas_sentinel 02_thermal_silencer 03_gpu_dvfs_lock 04_mem_cushion

all:
	@for dir in $(SUBDIRS); do \
		$(MAKE) -C $$dir; \
	done

clean:
	@for dir in $(SUBDIRS); do \
		$(MAKE) -C $$dir clean; \
	done
