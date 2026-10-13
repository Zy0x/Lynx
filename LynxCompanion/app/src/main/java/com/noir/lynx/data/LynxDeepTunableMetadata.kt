package com.noir.lynx.data

import java.io.File

internal object LynxDeepTunableMetadata {
    fun resolveFriendlyMetadata(tunable: DeepTunable): DeepTunable {
        var name = tunable.name
        var desc = tunable.desc
        var category = tunable.category
        var type = tunable.type
        var options = tunable.options
        var min = tunable.min
        var max = tunable.max
        var step = tunable.step
        var unit = tunable.unit
        var rec = tunable.recommendation
        val path = tunable.path.lowercase()
        val base = File(tunable.path.substringBefore(":")).name.lowercase()

        // 1. Check if help text contains inline option definitions (# 0: Disable, 1: Normal...)
        if (tunable.help.isNotBlank() && options.isEmpty()) {
            val parsedOpts = LynxTunableOptionParser.parseOptionsFromHelp(tunable.help)
            if (parsedOpts.isNotEmpty()) {
                options = parsedOpts
                type = if (parsedOpts.size == 2 && parsedOpts.any { it.value == "0" } && parsedOpts.any { it.value == "1" }) {
                    TunableType.BOOL
                } else {
                    TunableType.CHOICE
                }
            }
        }

        // 2. Specific Known Nodes & Subsystems
        when {
            // PPM Policies
            tunable.path.startsWith("/proc/ppm/policy_status:") -> {
                val idx = tunable.path.substringAfter(":")
                category = "CPU & Scheduler"
                type = TunableType.BOOL
                when (idx) {
                    "0" -> {
                        name = "PPM PTPOD Throttle"
                        desc = "Power-Thermal Prediction On Demand CPU policy."
                        rec = "Nonaktif saat gaming kompetitif."
                    }
                    "1" -> {
                        name = "PPM User Tuning (UT)"
                        desc = "Kebijakan tuning performa CPU oleh user-space."
                        rec = "Aktif (1) untuk mengizinkan tweak performa."
                    }
                    "2" -> {
                        name = "PPM Force Limit"
                        desc = "Batas paksa frekuensi CPU saat kondisi baterai kritis."
                        rec = "Nonaktif (0) agar frekuensi tidak tercekik."
                    }
                    "3" -> {
                        name = "PPM Power Throttle"
                        desc = "Pembatasan performa CPU untuk penghematan daya."
                        rec = "Nonaktif (0) untuk performa konstan."
                    }
                    "4" -> {
                        name = "PPM Thermal Throttle"
                        desc = "Throttling suhu CPU MediaTek (PPM Thermal Protection)."
                        rec = "Nonaktif (0) jika memakai cooler eksternal."
                    }
                    "5" -> {
                        name = "PPM DLPT Power Limit"
                        desc = "Dynamic Loading Power Throttling."
                        rec = "Nonaktif (0) saat game berat."
                    }
                    "6" -> {
                        name = "PPM Hard User Limit"
                        desc = "Batas atas frekuensi CPU mutlak dari sistem."
                        rec = "Aktif (1)."
                    }
                    "7" -> {
                        name = "PPM User Limit"
                        desc = "Batas frekuensi CPU user-space."
                        rec = "Aktif (1)."
                    }
                    "8" -> {
                        name = "PPM LCM Off Power Save"
                        desc = "Downclock CPU saat layar HP mati (hemat baterai)."
                        rec = "Aktif (1) untuk daya tahan baterai standby."
                    }
                    "9" -> {
                        name = "PPM System Boost"
                        desc = "Akselerasi CPU saat task berat terdeteksi."
                        rec = "Aktif (1) untuk respon instan."
                    }
                }
            }

            // Virtual Memory (VM)
            base == "dirty_ratio" -> {
                name = "Dirty Ratio RAM"
                category = "Memory & VM"
                desc = "Persentase maksimal RAM yang menampung dirty data sebelum kernel memaksa proses menulis ke storage."
                type = TunableType.SLIDER
                min = 1f; max = 90f; step = 1f; unit = "%"
                rec = "Default: 20%, Gaming: 10% (cegah writeback micro-freeze)"
            }
            base == "dirty_background_ratio" -> {
                name = "Dirty Background Ratio"
                category = "Memory & VM"
                desc = "Batas persentase RAM dirty data sebelum flush berjalan di background."
                type = TunableType.SLIDER
                min = 1f; max = 50f; step = 1f; unit = "%"
                rec = "Default: 10%, Gaming: 5%"
            }
            base == "vfs_cache_pressure" -> {
                name = "VFS Cache Pressure"
                category = "Memory & VM"
                desc = "Kecenderungan kernel mereclaim memory cache VFS (dentry & inode directory)."
                type = TunableType.SLIDER
                min = 10f; max = 200f; step = 5f; unit = ""
                rec = "Gaming: 50-80 (tahan cache), Multitasking: 100"
            }
            base == "swappiness" -> {
                name = "ZRAM Swappiness"
                category = "Memory & VM"
                desc = "Agresivitas kernel dalam memindahkan anonymous memory pages ke ZRAM/Swap."
                type = TunableType.SLIDER
                min = 0f; max = 200f; step = 5f; unit = ""
                rec = "ZRAM aktif: 100-160, Tanpa ZRAM: 60"
            }
            base == "compaction_proactiveness" -> {
                name = "Memory Compaction Proactiveness"
                category = "Memory & VM"
                desc = "Seberapa proaktif kernel mendefrag memori RAM di background."
                type = TunableType.SLIDER
                min = 0f; max = 100f; step = 5f; unit = ""
                rec = "Default: 20, Gaming: 0-10"
            }
            base == "watermark_scale_factor" -> {
                name = "Watermark Scale Factor"
                category = "Memory & VM"
                desc = "Jarak buffer memori bebas kernel sebelum kswapd terbangun."
                type = TunableType.SLIDER
                min = 10f; max = 400f; step = 10f; unit = ""
                rec = "Gaming: 100-200"
            }

            // CPU & Schedutil
            base == "sched_util_clamp_min" -> {
                name = "CPU Uclamp Min (Task Boost)"
                category = "CPU & Scheduler"
                desc = "Batas utilitas minimum penjadwal task CPU (Uclamp). Nilai lebih tinggi memaksa CPU berjalan pada frekuensi lebih tinggi."
                type = TunableType.SLIDER
                min = 0f; max = 1024f; step = 16f; unit = "util"
                rec = "Gaming: 300-600, Baterai: 0"
            }
            base == "sched_util_clamp_max" -> {
                name = "CPU Uclamp Max (Task Cap)"
                category = "CPU & Scheduler"
                desc = "Batas utilitas maksimum penjadwal task CPU (Uclamp cap)."
                type = TunableType.SLIDER
                min = 0f; max = 1024f; step = 16f; unit = "util"
                rec = "Default: 1024 (unrestricted)"
            }
            base == "up_rate_limit_us" -> {
                name = "Schedutil Up Rate Limit"
                category = "CPU & Scheduler"
                desc = "Waktu tunggu minimum CPU schedutil sebelum menaikkan frekuensi ke tingkat lebih tinggi."
                type = TunableType.SLIDER
                min = 0f; max = 10000f; step = 100f; unit = "µs"
                rec = "Gaming: 0-500 µs (respon instan), Baterai: 1000 µs"
            }
            base == "down_rate_limit_us" -> {
                name = "Schedutil Down Rate Limit"
                category = "CPU & Scheduler"
                desc = "Waktu tunggu minimum CPU schedutil sebelum menurunkan frekuensi saat beban berkurang."
                type = TunableType.SLIDER
                min = 0f; max = 50000f; step = 500f; unit = "µs"
                rec = "Gaming: 20000-40000 µs (tahan clock tinggi), Baterai: 5000 µs"
            }
            base == "rate_limit_us" -> {
                name = "Schedutil Rate Limit"
                category = "CPU & Scheduler"
                desc = "Jeda waktu perbaruan frekuensi governor schedutil."
                type = TunableType.SLIDER
                min = 0f; max = 20000f; step = 500f; unit = "µs"
                rec = "Gaming: 500-1000 µs"
            }
            base == "sched_energy_aware" -> {
                name = "Energy Aware Scheduling (EAS)"
                category = "CPU & Scheduler"
                desc = "Mengaktifkan penjadwalan hemat energi CPU ARM big.LITTLE."
                type = TunableType.BOOL
                rec = "1 = Aktif (Hemat Daya), 0 = Nonaktif (Fokus Performa)"
            }

            // GPU & Graphics
            base == "boost_amp" -> {
                name = "GED GPU Boost Amplitude"
                category = "GPU & Graphics"
                desc = "Amplitudo boost frekuensi GPU MediaTek saat frame rendering game mendesak."
                type = TunableType.STEPPER
                min = 0f; max = 3f; step = 1f; unit = "lvl"
                rec = "Level 2 atau 3 untuk game berat 90/120 FPS"
            }
            base == "adrenoboost" -> {
                name = "Adreno GPU Boost Level"
                category = "GPU & Graphics"
                desc = "Tingkat agresivitas devfreq GPU Qualcomm Adreno menaikkan clock."
                type = TunableType.STEPPER
                min = 0f; max = 3f; step = 1f; unit = "lvl"
                rec = "Level 2 (Optimal), Level 3 (Maksimal)"
            }
            base == "gpufreq_power_mode" -> {
                name = "MTK GPU Power Mode"
                category = "GPU & Graphics"
                desc = "Profil mode daya GPU MediaTek driver."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Power Save"),
                    TunableOption("1", "1 - Balance"),
                    TunableOption("2", "2 - Sport"),
                    TunableOption("3", "3 - Turbo Boost")
                )
                rec = "3 (Turbo Boost) untuk frame rate stabil"
            }
            base in listOf("ged_boost_enable", "boost_gpu_enable", "enable_cpu_boost", "enable_gpu_boost", "gx_game_mode", "gx_boost_on", "gpu_dvfs_enable") -> {
                name = when (base) {
                    "ged_boost_enable" -> "GED Master Boost"
                    "boost_gpu_enable" -> "GED GPU Frequency Boost"
                    "enable_cpu_boost" -> "GED CPU Scaling Boost"
                    "enable_gpu_boost" -> "GED Direct GPU Boost"
                    "gx_game_mode" -> "MediaTek GX Game Engine"
                    "gx_boost_on" -> "MediaTek GX Instant Boost"
                    "gpu_dvfs_enable" -> "GPU Dynamic Voltage Scaling"
                    else -> base
                }
                category = "GPU & Graphics"
                desc = "Akselerator grafis dan latensi frame driver MediaTek GED."
                type = TunableType.BOOL
                rec = "1 = Aktif untuk gaming stabil"
            }
            base == "gpu_boost_level" -> {
                name = "GED GPU Driver Boost Level"
                category = "GPU & Graphics"
                desc = "Tingkat dorongan performa GPU level driver MediaTek (-1=Auto, 0=Off, 1=Boost, 2=Max)."
                type = TunableType.STEPPER
                min = -1f; max = 2f; step = 1f; unit = "lvl"
                rec = "1 atau 2 untuk gaming kompetitif"
            }
            base in listOf("custom_boost_gpu_freq", "custom_upbound_gpu_freq") -> {
                name = if (base == "custom_boost_gpu_freq") "GED Custom Boost Freq Index" else "GED Custom Max Freq Index"
                category = "GPU & Graphics"
                desc = "Indeks tabel frekuensi GPU kustom driver MediaTek."
                type = TunableType.STEPPER
                min = 0f; max = 60f; step = 1f; unit = "idx"
            }
            base == "dvfs_margin_value" || base == "timer_base_dvfs_margin" -> {
                name = if (base == "dvfs_margin_value") "GED DVFS Frequency Margin" else "GED Timer Frequency Margin"
                category = "GPU & Graphics"
                desc = "Batas margin toleransi kenaikan frekuensi GPU driver MediaTek."
                type = TunableType.SLIDER
                min = 0f; max = 100f; step = 1f; unit = "%"
                rec = "Gaming: 20-50% untuk respon clock GPU instan"
            }
            base in listOf("fpsgo_enable", "boost_ta", "switch_idleprefer", "ultra_rescue") -> {
                name = when (base) {
                    "fpsgo_enable" -> "MediaTek FPSGO Game Engine"
                    "boost_ta" -> "Boost Top App (FPSGO)"
                    "switch_idleprefer" -> "FBT Idle Core Preference"
                    "ultra_rescue" -> "FPSGO Ultra Rescue"
                    else -> base
                }
                category = "GPU & Graphics"
                desc = when (base) {
                    "fpsgo_enable" -> "Sakelar utama engine penstabil frame rate & governor MediaTek."
                    "boost_ta" -> "Memprioritaskan thread aplikasi aktif langsung ke CPU core performa."
                    "switch_idleprefer" -> "Mengarahkan eksekusi task frame ke core idle guna memangkas latensi rendering."
                    "ultra_rescue" -> "Penyelamat darurat frame rate: mendeteksi stutter berat dan langsung memacu hardware."
                    else -> "Pengaturan engine grafis & frame MediaTek FPSGO."
                }
                type = TunableType.BOOL
                rec = "1 = Aktif untuk frame rate stabil"
            }
            path.contains("core_ctl") && (base == "enable" || base == "is_big_cluster") -> {
                name = if (base == "enable") "Qualcomm Core Control Switch" else "Big Cluster Identifier"
                category = "CPU & Scheduler"
                desc = "Manajemen hotplug core CPU performa Qualcomm."
                type = TunableType.BOOL
                rec = "1 = Aktif"
            }
            path.contains("core_ctl") && (base == "min_cpus" || base == "max_cpus") -> {
                name = if (base == "min_cpus") "Core Control Min Cores" else "Core Control Max Cores"
                category = "CPU & Scheduler"
                desc = "Batas jumlah inti CPU online pada kluster performa Qualcomm."
                type = TunableType.STEPPER
                min = 1f; max = 8f; step = 1f; unit = "cores"
            }
            path.contains("core_ctl") && (base == "busy_up_thres" || base == "busy_down_thres") -> {
                name = if (base == "busy_up_thres") "Core Control Busy Up Threshold" else "Core Control Busy Down Threshold"
                category = "CPU & Scheduler"
                desc = "Ambang batas utilisasi untuk menyalakan/mematikan core CPU performa Qualcomm."
                type = TunableType.SLIDER
                min = 0f; max = 100f; step = 1f; unit = "%"
            }
            path.endsWith("/proc/ppm/enabled") -> {
                name = "MediaTek PPM Master Switch"
                category = "CPU & Scheduler"
                desc = "Sakelar utama Performance and Power Management (PPM) MediaTek."
                type = TunableType.BOOL
                rec = "1 = Aktif untuk manajemen frekuensi dinamis"
            }
            path.contains("hps") && base == "enabled" -> {
                name = "MediaTek HPS Hotplug Switch"
                category = "CPU & Scheduler"
                desc = "Sakelar CPU Hotplug System MediaTek."
                type = TunableType.BOOL
            }
            base == "timer_migration" -> {
                name = "Timer CPU Migration"
                category = "CPU & Scheduler"
                desc = "Memindahkan timer kernel ke CPU hemat daya saat idle."
                type = TunableType.BOOL
                rec = "0 = Nonaktif saat gaming (latensi konsisten), 1 = Hemat baterai"
            }
            base == "randomize_va_space" -> {
                name = "Address Space Layout Randomization (ASLR)"
                category = "General"
                desc = "Mekanisme pengacakan alamat memori sistem untuk proteksi keamanan kernel."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Nonaktif (Performa Ekstrem)"),
                    TunableOption("1", "1 - Konservatif"),
                    TunableOption("2", "2 - Penuh (Default OEM)")
                )
                rec = "2 (Aman), 0 (Gaming Benchmark)"
            }

            // Storage & I/O
            base == "read_ahead_kb" -> {
                name = "I/O Read-Ahead Cache (${tunable.path.split("/").getOrNull(3) ?: "Storage"})"
                category = "Storage & I/O"
                desc = "Ukuran cache read-ahead storage internal sebelum data dibaca oleh aplikasi."
                type = TunableType.SLIDER
                min = 64f; max = 2048f; step = 64f; unit = "KB"
                rec = "128-512 KB optimal untuk game & aplikasi"
            }
            base == "scheduler" -> {
                name = "I/O Scheduler (${tunable.path.split("/").getOrNull(3) ?: "Storage"})"
                category = "Storage & I/O"
                desc = "Algoritma antrian I/O storage internal kernel."
                type = TunableType.CHOICE
                rec = "UFS Storage: 'none' atau 'mq-deadline'"
            }

            // Power, Thermal & Charging
            base == "bypass_charger" || base == "direct_charging" -> {
                name = "Bypass Charging (Direct Motherboard)"
                category = "Power & Thermal"
                desc = "Mengalirkan arus charger langsung ke motherboard tanpa mengisi baterai saat bermain game."
                type = TunableType.BOOL
                rec = "Aktifkan (1) saat bermain game sambil cas agar HP dingin"
            }
            base == "enable_sc" -> {
                name = "Smart Charging Switch"
                category = "Power & Thermal"
                desc = "Sakelar fitur pengisian daya pintar OEM untuk mengontrol proteksi suhu dan pengisian."
                type = TunableType.BOOL
                rec = "Aktif (1) untuk proteksi pengisian adaptif"
            }
            base == "Pump_Express" -> {
                name = "MediaTek Pump Express"
                category = "Power & Thermal"
                desc = "Protokol fast charging MediaTek Pump Express."
                type = TunableType.BOOL
                rec = "Aktif (1) untuk pengisian daya cepat"
            }
            base == "sw_jeita" -> {
                name = "Software JEITA Protection"
                category = "Power & Thermal"
                desc = "Proteksi termal charging berbasis standar baterai JEITA."
                type = TunableType.BOOL
                rec = "Aktif (1) demi keamanan baterai"
            }
            base.contains("input_current") || base.contains("chg1_current") || base.contains("chg2_current") || base.contains("sc_ibat_limit") -> {
                name = "Batas Arus Pengisian ($base)"
                category = "Power & Thermal"
                desc = "Batas arus pengisian daya baterai dalam satuan miliampere (mA)."
                type = TunableType.INT
                unit = "mA"
                rec = "1500-2500 mA untuk charging sejuk saat game"
            }
            path.contains("thermal_zone") && base == "mode" -> {
                val tzName = tunable.path.split("/").find { it.startsWith("thermal_zone") } ?: "zone"
                name = "Thermal Throttling ($tzName)"
                category = "Power & Thermal"
                desc = "Status proteksi throttling termal hardware untuk zona $tzName."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("enabled", "enabled - Aktif (OEM)"),
                    TunableOption("disabled", "disabled - Nonaktif (Bypass)")
                )
                rec = "Perhatian: 'disabled' hanya disarankan dengan pendingin eksternal"
            }
            path.contains("thermal_zone") && base == "policy" -> {
                val tzName = tunable.path.split("/").find { it.startsWith("thermal_zone") } ?: "zone"
                name = "Thermal Policy ($tzName)"
                category = "Power & Thermal"
                desc = "Algoritma mitigasi panas zona $tzName (step_wise, power_allocator, user_space)."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("step_wise", "step_wise (Default OEM)"),
                    TunableOption("power_allocator", "power_allocator (Intelligent)"),
                    TunableOption("user_space", "user_space (Manual)")
                )
            }

            // Virtual Memory Additional
            base == "extra_free_kbytes" -> {
                name = "Extra Free Kbytes"
                category = "Memory & VM"
                desc = "Memori bebas ekstra yang dipertahankan kernel untuk mencegah lag dan OOM saat membuka aplikasi berat."
                type = TunableType.SLIDER
                min = 0f; max = 200000f; step = 4096f; unit = "KB"
                rec = "Gaming: 24576 - 65536 KB untuk menghindari freeze mendadak"
            }
            base == "min_free_kbytes" -> {
                name = "Min Free Kbytes Reserve"
                category = "Memory & VM"
                desc = "Cadangan memori minimum absolut kernel Linux sebelum memicu pembersihan darurat."
                type = TunableType.SLIDER
                min = 4096f; max = 131072f; step = 2048f; unit = "KB"
                rec = "Optimal: 8192 - 32768 KB"
            }
            base == "dirty_expire_centisecs" -> {
                name = "Dirty Expire Interval"
                category = "Memory & VM"
                desc = "Waktu tunggu (dalam seperseratus detik) sebelum data kotor di RAM dianggap kadaluarsa dan harus ditulis ke disk."
                type = TunableType.SLIDER
                min = 100f; max = 6000f; step = 100f; unit = "cs"
                rec = "Default: 3000 cs (30s), Gaming: 1000 cs (10s) untuk flush lebih sering"
            }
            base == "dirty_writeback_centisecs" -> {
                name = "Dirty Writeback Wakeup"
                category = "Memory & VM"
                desc = "Jeda waktu kernel bangun secara berkala untuk mengecek data kotor yang perlu ditulis ke penyimpanan."
                type = TunableType.SLIDER
                min = 100f; max = 3000f; step = 50f; unit = "cs"
                rec = "Optimal: 500 cs (5 detik)"
            }
            base == "stat_interval" -> {
                name = "VM Stat Interval"
                category = "Memory & VM"
                desc = "Interval waktu pembaruan statistik virtual memory oleh kernel. Nilai lebih tinggi mengurangi CPU jitter."
                type = TunableType.SLIDER
                min = 1f; max = 30f; step = 1f; unit = "s"
                rec = "Gaming: 5-10 detik untuk mengurangi interupsi CPU"
            }
            base == "extfrag_threshold" -> {
                name = "External Fragmentation Threshold"
                category = "Memory & VM"
                desc = "Batas toleransi fragmentasi memori sebelum kernel memicu kompaksi memori."
                type = TunableType.SLIDER
                min = 0f; max = 1000f; step = 50f; unit = ""
                rec = "Default: 500"
            }
            base == "max_map_count" -> {
                name = "Max Virtual Memory Maps"
                category = "Memory & VM"
                desc = "Batas maksimum area pemetaan memori (mmap) per proses. Sangat penting untuk game besar & emulator."
                type = TunableType.INT
                rec = "Gaming & Emulator: 262144 s/d 1048576"
            }

            // CPU & Kernel Scheduler Additional
            base == "sched_latency_ns" -> {
                name = "CFS Scheduler Latency"
                category = "CPU & Scheduler"
                desc = "Periode rotasi pengeksekusian seluruh task yang siap berjalan di kernel CFS."
                type = TunableType.SLIDER
                min = 1000000f; max = 24000000f; step = 1000000f; unit = "ns"
                rec = "Default: 10000000 ns (10ms), Gaming: 4000000-6000000 ns (respon cepat)"
            }
            base == "sched_min_granularity_ns" -> {
                name = "CFS Min Granularity"
                category = "CPU & Scheduler"
                desc = "Batas waktu eksekusi minimum sebelum sebuah task CPU dapat di-preempt oleh task lain."
                type = TunableType.SLIDER
                min = 500000f; max = 10000000f; step = 500000f; unit = "ns"
                rec = "Gaming: 1000000-2000000 ns"
            }
            base == "sched_wakeup_granularity_ns" -> {
                name = "CFS Wakeup Granularity"
                category = "CPU & Scheduler"
                desc = "Keuntungan latency yang dibutuhkan oleh task yang baru bangun untuk menggeser task yang sedang berjalan."
                type = TunableType.SLIDER
                min = 500000f; max = 15000000f; step = 500000f; unit = "ns"
                rec = "Gaming: 1000000-3000000 ns"
            }
            base == "sched_migration_cost_ns" -> {
                name = "Task Migration Cost"
                category = "CPU & Scheduler"
                desc = "Waktu minimum task harus diam sebelum kernel memutuskan memindahkannya ke inti CPU lain (menjaga cache L1/L2)."
                type = TunableType.SLIDER
                min = 0f; max = 5000000f; step = 100000f; unit = "ns"
                rec = "Gaming: 500000 ns (tahan task pada core performa)"
            }
            base == "sched_child_runs_first" -> {
                name = "Child Process Runs First"
                category = "CPU & Scheduler"
                desc = "Menjalankan child process terlebih dahulu saat proses melakukan fork."
                type = TunableType.BOOL
                rec = "0 = Nonaktif (Default), 1 = Aktif"
            }
            base == "sched_schedstats" -> {
                name = "Kernel Scheduler Stats"
                category = "CPU & Scheduler"
                desc = "Perekaman statistik penjadwal kernel. Mematikan fitur ini mengurangi overhead CPU."
                type = TunableType.BOOL
                rec = "0 = Nonaktif (Kurangi CPU overhead & jitter)"
            }
            base == "sched_big_task_rotation" -> {
                name = "Big Task Core Rotation"
                category = "CPU & Scheduler"
                desc = "Rotasi task berat antar core performa untuk mendistribusikan panas secara merata."
                type = TunableType.BOOL
                rec = "1 = Aktif untuk pencegahan thermal throttling lokal"
            }
            base == "sched_uclamp_util_min" -> {
                name = "Kernel Uclamp Util Min"
                category = "CPU & Scheduler"
                desc = "Batas utilitas minimum universal untuk seluruh task penjadwal kernel."
                type = TunableType.SLIDER
                min = 0f; max = 1024f; step = 16f; unit = "util"
                rec = "Gaming: 300-600, Normal: 0"
            }
            base == "sched_uclamp_util_max" -> {
                name = "Kernel Uclamp Util Max"
                category = "CPU & Scheduler"
                desc = "Batas utilitas maksimum universal untuk seluruh task penjadwal kernel."
                type = TunableType.SLIDER
                min = 0f; max = 1024f; step = 16f; unit = "util"
                rec = "Default: 1024 (unrestricted)"
            }
            base == "power_efficient" -> {
                name = "Power Efficient Workqueue"
                category = "CPU & Scheduler"
                desc = "Mengarahkan antrian tugas sistem ke CPU hemat daya saat idle."
                type = TunableType.BOOL
                rec = "0 = Nonaktif saat gaming (performa maksimal), 1 = Hemat baterai"
            }

            // Network & TCP Congestion Control
            base == "tcp_congestion_control" -> {
                name = "TCP Congestion Algorithm"
                category = "Network & Ping"
                desc = "Algoritma kontrol kepadatan koneksi internet untuk stabilitas ping dan throughput."
                type = TunableType.CHOICE
                rec = "Gaming: 'bbr' atau 'cubic' untuk ping terendah"
            }
            base == "tcp_fastopen" -> {
                name = "TCP Fast Open (TFO)"
                category = "Network & Ping"
                desc = "Mempercepat inisiasi koneksi TCP dengan mengirimkan data payload pada SYN packet pertama."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Nonaktif"),
                    TunableOption("1", "1 - Client Only"),
                    TunableOption("2", "2 - Server Only"),
                    TunableOption("3", "3 - Keduanya (Client & Server)")
                )
                rec = "3 = Optimal (koneksi instan)"
            }
            base == "tcp_ecn" -> {
                name = "Explicit Congestion Notification (ECN)"
                category = "Network & Ping"
                desc = "Notifikasi kemacetan jaringan tanpa perlu menjatuhkan paket (packet loss prevention)."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Nonaktif"),
                    TunableOption("1", "1 - Aktif"),
                    TunableOption("2", "2 - Hanya jika diminta server")
                )
                rec = "1 atau 2 untuk mengurangi packet drop pada game online"
            }
            base in listOf("tcp_sack", "tcp_tw_reuse", "tcp_low_latency", "tcp_window_scaling", "tcp_timestamps") -> {
                name = when (base) {
                    "tcp_sack" -> "TCP Selective Acknowledgment (SACK)"
                    "tcp_tw_reuse" -> "TCP TIME_WAIT Socket Reuse"
                    "tcp_low_latency" -> "TCP Low Latency Mode"
                    "tcp_window_scaling" -> "TCP Window Scaling"
                    "tcp_timestamps" -> "TCP Timestamps Verification"
                    else -> base
                }
                category = "Network & Ping"
                desc = "Optimasi protokol jaringan untuk respon dan latency data real-time."
                type = TunableType.BOOL
                rec = "Aktif (1) untuk stabilitas koneksi gaming"
            }
            base == "read_wakeup_threshold" -> {
                name = "Kernel Entropy Read Threshold"
                category = "General"
                desc = "Batas ketersediaan entropy acak sebelum proses pembaca terbangun."
                type = TunableType.SLIDER
                min = 64f; max = 2048f; step = 64f; unit = "bits"
                rec = "Enthusiast: 1024-2048 bits"
            }
            base == "printk" || base == "printk_devkmsg" -> {
                name = "Kernel Printk Logging Level"
                category = "General"
                desc = "Tingkat pencatatan log kernel sistem. Mengurangi log membebaskan siklus CPU saat gaming."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Senyap (Performa Ekstrem)"),
                    TunableOption("3", "3 - Hanya Error Kritis"),
                    TunableOption("7", "7 - Semua Log (Debug Pabrik)")
                )
                rec = "0 atau 3 untuk mengurangi CPU overhead"
            }

            // Storage I/O Additional
            base in listOf("add_random", "iostats", "rotational", "nomerges") -> {
                name = when (base) {
                    "add_random" -> "Storage Entropy Contribution"
                    "iostats" -> "I/O Disk Statistics Logging"
                    "rotational" -> "Rotational Storage Flag"
                    "nomerges" -> "Disable Request Merging"
                    else -> base
                }
                category = "Storage & I/O"
                desc = "Parameter antrian penyimpanan internal kernel."
                type = TunableType.BOOL
                rec = if (base == "rotational") "0 (Flash Storage / UFS)" else "0 (Kurangi overhead)"
            }
            base == "nr_requests" -> {
                name = "I/O Request Queue Depth"
                category = "Storage & I/O"
                desc = "Jumlah alokasi antrian pembacaan dan penulisan blok I/O sebelum proses diblokir."
                type = TunableType.SLIDER
                min = 64f; max = 2048f; step = 64f; unit = "req"
                rec = "Default: 128, Gaming & I/O Berat: 256-512"
            }

            // Display & Touch
            base == "game_switch_enable" || base == "touch_game_mode" -> {
                name = "Panel Touch Gaming Mode"
                category = "Display & Touch"
                desc = "Menaikkan sampling rate touch digitizer layar untuk respon sentuhan instan."
                type = TunableType.BOOL
                rec = "Aktifkan (1) untuk sensitivitas sentuhan kompetitif"
            }

            // Fallbacks based on value
            type == TunableType.TEXT -> {
                val v = tunable.value.trim()
                if (v == "0" || v == "1" || v.equals("enabled", true) || v.equals("disabled", true) || v.equals("y", true) || v.equals("n", true)) {
                    type = TunableType.BOOL
                    if (desc.isBlank()) desc = "Pengaturan sakelar status kernel (1=Aktif, 0=Nonaktif)."
                } else if (v.matches(Regex("^-?\\d+$"))) {
                    val num = v.toLongOrNull() ?: 0L
                    if (num in 0..5 && (path.contains("boost") || path.contains("mode") || path.contains("level"))) {
                        type = TunableType.STEPPER
                        min = 0f; max = 5f; step = 1f; unit = "lvl"
                    } else if (path.contains("ratio") || path.contains("percent") || path.contains("pct")) {
                        type = TunableType.SLIDER
                        min = 0f; max = 100f; step = 1f; unit = "%"
                    } else if (path.contains("rate_limit") || path.contains("latency")) {
                        type = TunableType.SLIDER
                        min = 0f; max = 20000f; step = 100f; unit = "µs"
                    } else {
                        type = TunableType.INT
                    }
                }
            }
        }

        return tunable.copy(
            name = name,
            desc = desc,
            category = category,
            type = type,
            options = options,
            min = min,
            max = max,
            step = step,
            unit = unit,
            recommendation = rec
        )
    }


}
