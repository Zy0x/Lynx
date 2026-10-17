package com.noir.lynx.core.locale

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as Material3Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.res.stringResource
import com.noir.lynx.R

private data class DynamicUiText(val pattern: Regex, val resource: Int)

/** Patterns for legacy Compose labels whose values are interpolated at runtime. */
private val dynamicUiTextResources = listOf(
    DynamicUiText(Regex("^Profil dasar: (.+)$"), R.string.dynamic_profile_base),
    DynamicUiText(Regex("^CPU: (.+) · ubah$"), R.string.dynamic_profile_cpu),
    DynamicUiText(Regex("^GPU: (.+) · ubah$"), R.string.dynamic_profile_gpu),
    DynamicUiText(Regex("^([☑□]) Sertakan kebijakan termal saat ini$"), R.string.dynamic_profile_include_thermal),
    DynamicUiText(Regex("^([☑□]) Sertakan batas pengisian saat ini$"), R.string.dynamic_profile_include_battery),
    DynamicUiText(Regex("^Kustomisasi (.+) akan dihapus dan nilai bawaan Lynx dipulihkan\\.$"), R.string.dynamic_profile_delete_confirmation),
    DynamicUiText(Regex("^Kustomisasi (.+)$"), R.string.dynamic_profile_customize_title),
    DynamicUiText(Regex("^Anda dapat menimpa (.+) atau menyimpan perubahan sebagai profil baru\\.$"), R.string.dynamic_profile_overwrite_confirmation),
    DynamicUiText(Regex("^Profil Kustom Aktif: (.+)$"), R.string.dynamic_profile_active),
    DynamicUiText(Regex("^Basis: (.+) • (.+)$"), R.string.dynamic_profile_base_description),
    DynamicUiText(Regex("^Induk (.+) · override lokal (.+)$"), R.string.dynamic_profile_parent_overrides),
    DynamicUiText(Regex("^Mode (.+) Aktif$"), R.string.dynamic_mode_active),
    DynamicUiText(Regex("^Apakah Anda yakin ingin memulihkan partisi dari backup '(.+)'\\?$"), R.string.dynamic_backup_restore_confirmation),
    DynamicUiText(Regex("^(\\d+) Inti \\((.+)\\) • (.+)-(.*) MHz$"), R.string.dynamic_cpu_cluster_summary),
    DynamicUiText(Regex("^Pilih Frekuensi (Minimum|Maksimum)$"), R.string.dynamic_cpu_frequency_picker),
    DynamicUiText(Regex("^Policy (.+) \\((.+) tunables ditemukan\\)$"), R.string.dynamic_cpu_policy_tunables),
    DynamicUiText(Regex("^Policy (.+) \\((.+)\\)$"), R.string.dynamic_cpu_policy),
    DynamicUiText(Regex("^Parameter Lanjutan: (.+)$"), R.string.dynamic_cpu_advanced_parameters),
    DynamicUiText(Regex("^Nilai \\((.+)\\)$"), R.string.dynamic_cpu_value_unit),
    DynamicUiText(Regex("^Driver: (.+) • Kontrol status tidur inti CPU$"), R.string.dynamic_cpu_idle_driver),
    DynamicUiText(Regex("^Ketuk core \\(C0-C(.+)\\) pada masing-masing kelompok tugas untuk mengizinkan atau melarang thread berjalan pada inti fisik tersebut\\.$"), R.string.dynamic_cpu_core_instructions),
    DynamicUiText(Regex("^TABEL LENGKAP STEPPING FREKUENSI \\((.+) OPP\\)$"), R.string.dynamic_cpu_frequency_table),
    DynamicUiText(Regex("^Perizinan: (.+)$"), R.string.dynamic_cpu_permissions),
    DynamicUiText(Regex("^STATUS INTI CPU \\((.+) CORES\\)$"), R.string.dynamic_cpu_core_status),
    DynamicUiText(Regex("^Fitur Kernel Aktif: (.+)$"), R.string.dynamic_gpu_active_features),
    DynamicUiText(Regex("^(.+) node kernel terverifikasi • Akses I/O langsung$"), R.string.dynamic_gpu_verified_nodes),
    DynamicUiText(Regex("^Tersedia (.+) step frekuensi clock OPP$"), R.string.dynamic_gpu_clock_steps),
    DynamicUiText(Regex("^(.+) Kontrol$"), R.string.dynamic_gpu_control_count),
    DynamicUiText(Regex("^Driver Fisik • (.+)$"), R.string.dynamic_gpu_physical_driver),
    DynamicUiText(Regex("^Shield Anti-Throttling OEM \\((.+)\\)$"), R.string.dynamic_oem_throttling_shield),
    DynamicUiText(Regex("^\\((.+)\\)"), R.string.dynamic_count_in_parentheses),
    DynamicUiText(Regex("^Tersedia: (.+) MB$"), R.string.dynamic_memory_available),
    DynamicUiText(Regex("^Bebas: (.+) MB$"), R.string.dynamic_memory_free),
    DynamicUiText(Regex("^Ukuran Alokasi ZRAM \\(Skala RAM Fisik: (.+) MB\\)$"), R.string.dynamic_zram_allocation_scale),
    DynamicUiText(Regex("^Muat Lebih Banyak \\((.+) tersisa\\)$"), R.string.dynamic_load_more),
    DynamicUiText(Regex("^Terapkan \\((.+)\\)$"), R.string.dynamic_apply_value),
    DynamicUiText(Regex("^DISTRIBUSI FRAMETIME \\((.+) FRAMES\\)$"), R.string.dynamic_benchmark_frame_distribution),
    DynamicUiText(Regex("^Nilai saat ini: (.+)$"), R.string.dynamic_current_value),
    DynamicUiText(Regex("^(.+) \\(Nilai saat ini: (.+)\\)$"), R.string.dynamic_feature_current_value),
    DynamicUiText(Regex("^Efek: (.+)$"), R.string.dynamic_effect_description),
    DynamicUiText(Regex("^Detik ke-(.+)s$"), R.string.dynamic_seconds_offset),
    DynamicUiText(Regex("^Kondisi: (.+) MHz • (.+)% Load • (.+)°C$"), R.string.dynamic_gpu_event_conditions),
    DynamicUiText(Regex("^Riwayat Sesi Rekaman \\((.+)/20\\)$"), R.string.dynamic_recorded_session_history),
    DynamicUiText(Regex("^Metrik Sesi: (.+)$"), R.string.dynamic_session_metrics),
    DynamicUiText(Regex("^Pukul (.+) • Durasi (.+)s • (.+) Frame$"), R.string.dynamic_session_time_summary),
    DynamicUiText(Regex("^Reset ke (.+)$"), R.string.dynamic_reset_to_label),
    DynamicUiText(Regex("^Terapkan Offset Voltase \\((.+) mV\\)$"), R.string.dynamic_apply_voltage_offset),
    DynamicUiText(Regex("^(.+) Tingkatan$"), R.string.dynamic_cpu_frequency_level_count),
    DynamicUiText(Regex("^(.+) Cores$"), R.string.dynamic_cpu_core_count),
    DynamicUiText(Regex("^Edit Tunable: (.+)$"), R.string.dynamic_edit_tunable),
    DynamicUiText(Regex("^Node: (.+)$"), R.string.dynamic_node_label),
    DynamicUiText(Regex("^Legacy Minfree Levels: (.+)$"), R.string.dynamic_legacy_minfree_levels),
    DynamicUiText(Regex("^Read Wakeup Threshold: (.+) bits$"), R.string.dynamic_read_wakeup_threshold),
    DynamicUiText(Regex("^Write Wakeup Threshold: (.+) bits$"), R.string.dynamic_write_wakeup_threshold),
    DynamicUiText(Regex("^Headphone Gain \\(L/R\\): (.+)dB / (.+)dB$"), R.string.dynamic_headphone_gain),
    DynamicUiText(Regex("^Speaker Gain: (.+)dB$"), R.string.dynamic_speaker_gain),
    DynamicUiText(Regex("^Microphone Gain: (.+)dB$"), R.string.dynamic_microphone_gain),
    DynamicUiText(Regex("^Driver: (.+) • Kernel C-States$"), R.string.dynamic_cpu_driver_cstates),
    DynamicUiText(Regex("^Core CPU (.+) diaktifkan \\(Online\\)$"), R.string.dynamic_cpu_core_enabled),
    DynamicUiText(Regex("^Core CPU (.+) dimatikan \\(Offline\\)$"), R.string.dynamic_cpu_core_disabled),
    DynamicUiText(Regex("^Gagal mengubah status Core (.+)\\.$"), R.string.dynamic_cpu_core_status_failed),
    DynamicUiText(Regex("^Gagal mengubah status C-State (.+)$"), R.string.dynamic_cpu_cstate_status_failed),
    DynamicUiText(Regex("^Script #(.+)$"), R.string.dynamic_script_fallback_name),
    DynamicUiText(Regex("^Profil Efisiensi '(.+)' berhasil diterapkan$"), R.string.dynamic_cpu_idle_preset_applied),
    DynamicUiText(Regex("^Gagal menerapkan preset CPU Idle '(.+)'$"), R.string.dynamic_cpu_idle_preset_failed),
    DynamicUiText(Regex("^ARMPLL Mode Aktif \\(Hemat Daya\\)$"), R.string.dynamic_armpll_power_saving),
    DynamicUiText(Regex("^ARMPLL Mode Dimatikan \\(Performa\\)$"), R.string.dynamic_armpll_performance),
    // BEGIN GENERATED FEEDBACK DYNAMIC TEXTS
    DynamicUiText(Regex("^Aturan profil untuk (.+) disimpan!$"), R.string.dynamic_feedback_automation_rule_saved),
    DynamicUiText(Regex("^Batas permintaan arus: (.+) mA$"), R.string.dynamic_feedback_battery_current_limit),
    DynamicUiText(Regex("^Kalibrasi Sel Baterai: (.+)x \\(Dual-Cell 2S Series\\)$"), R.string.dynamic_feedback_battery_calibration_dual),
    DynamicUiText(Regex("^Kalibrasi Sel Baterai: (.+)x \\(Single Cell Standar\\)$"), R.string.dynamic_feedback_battery_calibration_single),
    DynamicUiText(Regex("^Pengujian Selesai: (.+)★ \\((.+) mΩ\\) - (.+)$"), R.string.dynamic_feedback_cable_test_result),
    DynamicUiText(Regex("^Gagal menjalankan pengujian kabel: (.+)$"), R.string.dynamic_feedback_cable_test_failed),
    DynamicUiText(Regex("^Benchmark gagal: (.+)$"), R.string.dynamic_feedback_benchmark_failed),
    DynamicUiText(Regex("^Deep Scan selesai: (.+) parameter hardware ditemukan!$"), R.string.dynamic_feedback_deep_scan_complete),
    DynamicUiText(Regex("^Parameter diterapkan: (.+) = (.+)$"), R.string.dynamic_feedback_parameter_applied),
    DynamicUiText(Regex("^Gagal menerapkan parameter ke (.+)$"), R.string.dynamic_feedback_parameter_apply_failed),
    DynamicUiText(Regex("^Node tidak ditemukan atau tidak dapat dibaca: (.+)$"), R.string.dynamic_feedback_node_unavailable),
    DynamicUiText(Regex("^Skrip '(.+)' disimpan$"), R.string.dynamic_feedback_script_saved),
    DynamicUiText(Regex("^Mengeksekusi '(.+)'\\.\\.\\.$"), R.string.dynamic_feedback_script_running),
    DynamicUiText(Regex("^Eksekusi selesai \\(Exit: (.+)\\)$"), R.string.dynamic_feedback_script_exit),
    DynamicUiText(Regex("^Dmesg diekspor ke (.+)$"), R.string.dynamic_feedback_dmesg_exported),
    DynamicUiText(Regex("^Kernel menolak perubahan \\(nilai terbaca: (.+)\\)\\. Parameter dikunci read-only\\.$"), R.string.dynamic_feedback_kernel_rejected_value),
    DynamicUiText(Regex("^DC Dimming diaktifkan$"), R.string.dynamic_feedback_dc_dimming_enabled),
    DynamicUiText(Regex("^DC Dimming dinonaktifkan$"), R.string.dynamic_feedback_dc_dimming_disabled),
    DynamicUiText(Regex("^Aturan grafis untuk (.+) disimpan$"), R.string.dynamic_feedback_graphics_rule_saved),
    DynamicUiText(Regex("^Kalibrasi warna diaktifkan \\((.+)\\)$"), R.string.dynamic_feedback_color_calibration_enabled),
    DynamicUiText(Regex("^Profil warna '(.+)' diterapkan$"), R.string.dynamic_feedback_color_profile_applied),
    DynamicUiText(Regex("^Sesi (.+) tersimpan \\((.+)s, (.+) frames\\)$"), R.string.dynamic_feedback_session_saved),
    DynamicUiText(Regex("^Laporan berhasil diekspor ke: (.+)$"), R.string.dynamic_feedback_report_exported),
    DynamicUiText(Regex("^Profil (.+) tersimpan$"), R.string.dynamic_feedback_kernel_profile_saved),
    DynamicUiText(Regex("^Profil dasar (.+) gagal diterapkan\\. Mengembalikan ke mode (.+)\\.$"), R.string.dynamic_feedback_kernel_profile_apply_failed),
    DynamicUiText(Regex("^Profil (.+) diterapkan$"), R.string.dynamic_feedback_kernel_profile_applied),
    DynamicUiText(Regex("^Perhatian: Inisialisasi parsial \\((.+)\\)$"), R.string.dynamic_feedback_partial_initialization),
    DynamicUiText(Regex("^LMK preset '(.+)' diterapkan$"), R.string.dynamic_feedback_lmk_preset_applied),
    DynamicUiText(Regex("^Ditambahkan ke Game Mode: (.+)$"), R.string.dynamic_feedback_game_package_added),
    DynamicUiText(Regex("^Dihapus dari Game Mode: (.+)$"), R.string.dynamic_feedback_game_package_removed),
    DynamicUiText(Regex("^Wakelock kernel diperbarui \\((.+) sumber\\)$"), R.string.dynamic_feedback_wakelock_count),
    DynamicUiText(Regex("^Wakelock '(.+)' diblokir$"), R.string.dynamic_feedback_wakelock_blocked),
    DynamicUiText(Regex("^Wakelock '(.+)' dibuka$"), R.string.dynamic_feedback_wakelock_unblocked),
    DynamicUiText(Regex("^Parameter (.+) diperbarui \\(Mode Kustom\\)$"), R.string.dynamic_feedback_tunable_updated),
    DynamicUiText(Regex("^Gagal memperbarui (.+)$"), R.string.dynamic_feedback_tunable_update_failed),
    DynamicUiText(Regex("^Hysteresis migrasi diperbarui \\((.+)% / (.+)% - Mode Kustom\\)$"), R.string.dynamic_feedback_migration_hysteresis),
    DynamicUiText(Regex("^Mode arsitektur (.+) aktif \\(Mode Kustom\\)$"), R.string.dynamic_feedback_arch_mode_enabled),
    DynamicUiText(Regex("^Gagal mengubah mode ke (.+)$"), R.string.dynamic_feedback_arch_mode_failed),
    DynamicUiText(Regex("^Preset Penjadwal '(.+)' berhasil diterapkan$"), R.string.dynamic_feedback_scheduler_preset_applied),
    DynamicUiText(Regex("^Gagal menerapkan preset penjadwal '(.+)'$"), R.string.dynamic_feedback_scheduler_preset_failed),
    DynamicUiText(Regex("^Profil CPU Sets '(.+)' berhasil diterapkan$"), R.string.dynamic_feedback_cpuset_preset_applied),
    DynamicUiText(Regex("^Gagal menerapkan profil CPU Sets '(.+)'$"), R.string.dynamic_feedback_cpuset_preset_failed),
    DynamicUiText(Regex("^Gagal memperbarui Core (.+) pada (.+)$"), R.string.dynamic_feedback_core_group_failed),
    DynamicUiText(Regex("^Power-Efficient Workqueue diaktifkan \\(Hemat Daya\\)$"), R.string.dynamic_feedback_workqueue_enabled),
    DynamicUiText(Regex("^Power-Efficient Workqueue dinonaktifkan \\(Latensi Rendah\\)$"), R.string.dynamic_feedback_workqueue_disabled),
    DynamicUiText(Regex("^Qualcomm Touchboost diaktifkan$"), R.string.dynamic_feedback_qualcomm_touchboost_enabled),
    DynamicUiText(Regex("^Qualcomm Touchboost dinonaktifkan$"), R.string.dynamic_feedback_qualcomm_touchboost_disabled),
    DynamicUiText(Regex("^Akselerasi Bus CCI & Memori DDR diaktifkan$"), R.string.dynamic_feedback_cci_enabled),
    DynamicUiText(Regex("^Akselerasi Bus CCI & Memori DDR dinonaktifkan$"), R.string.dynamic_feedback_cci_disabled),
    DynamicUiText(Regex("^Mode Daya DVFS Silikon diatur ke (.+)$"), R.string.dynamic_feedback_dvfs_label),
    DynamicUiText(Regex("^Bypass Limit Arus Puncak \\(DLPT & Imax\\) diaktifkan$"), R.string.dynamic_feedback_dlpt_enabled),
    DynamicUiText(Regex("^Bypass Limit Arus Puncak \\(DLPT & Imax\\) dinonaktifkan$"), R.string.dynamic_feedback_dlpt_disabled),
    DynamicUiText(Regex("^Akselerasi Bus Memori & Cache L3 \\(Devfreq\\) diaktifkan$"), R.string.dynamic_feedback_devfreq_accel_enabled),
    DynamicUiText(Regex("^Akselerasi Bus Memori & Cache L3 \\(Devfreq\\) dinonaktifkan$"), R.string.dynamic_feedback_devfreq_accel_disabled),
    DynamicUiText(Regex("^Frekuensi Bus Memori & Interkoneksi diatur ke (.+)$"), R.string.dynamic_feedback_memory_bus_frequency),
    DynamicUiText(Regex("^Akselerasi Respons Sentuhan diaktifkan$"), R.string.dynamic_feedback_touch_accel_enabled),
    DynamicUiText(Regex("^Akselerasi Respons Sentuhan dinonaktifkan$"), R.string.dynamic_feedback_touch_accel_disabled),
    DynamicUiText(Regex("^Pertahankan Plafon Clock Puncak diaktifkan$"), R.string.dynamic_feedback_peak_clock_enabled),
    DynamicUiText(Regex("^Pertahankan Plafon Clock Puncak dinonaktifkan$"), R.string.dynamic_feedback_peak_clock_disabled),
    DynamicUiText(Regex("^Algoritma ZRAM berhasil diubah ke (.+)$"), R.string.dynamic_feedback_zram_algorithm_applied),
    DynamicUiText(Regex("^Gagal mengubah algoritma ZRAM ke (.+)$"), R.string.dynamic_feedback_zram_algorithm_failed),
    DynamicUiText(Regex("^VM Dirty Ratio diatur ke (.+)%$"), R.string.dynamic_feedback_vm_dirty_ratio),
    DynamicUiText(Regex("^VM Dirty Background: (.+)%$"), R.string.dynamic_feedback_vm_dirty_background),
    DynamicUiText(Regex("^VFS Cache Pressure diatur ke (.+)$"), R.string.dynamic_feedback_vfs_cache_pressure),
    DynamicUiText(Regex("^VM Expire Time: (.+)s$"), R.string.dynamic_feedback_vm_expire_time),
    DynamicUiText(Regex("^VM Writeback Interval: (.+)s$"), R.string.dynamic_feedback_vm_writeback_interval),
    DynamicUiText(Regex("^VM Stat Interval: (.+)s$"), R.string.dynamic_feedback_vm_stat_interval),
    DynamicUiText(Regex("^Preset VM '(.+)' berhasil diterapkan$"), R.string.dynamic_feedback_vm_preset_applied),
    DynamicUiText(Regex("^Gagal menerapkan preset VM '(.+)'$"), R.string.dynamic_feedback_vm_preset_failed),
    DynamicUiText(Regex("^Refresh rate diatur ke (.+)Hz$"), R.string.dynamic_feedback_refresh_rate_set),
    DynamicUiText(Regex("^Engine SoC diubah ke: (.+)$"), R.string.dynamic_feedback_soc_engine_set),
    DynamicUiText(Regex("^Offset voltase (.+)mV diaplikasikan$"), R.string.dynamic_feedback_voltage_offset),
    DynamicUiText(Regex("^Threshold entropi disimpan \\((.+) / (.+)\\)$"), R.string.dynamic_feedback_entropy_threshold),
    DynamicUiText(Regex("^SELinux diubah ke (.+)$"), R.string.dynamic_feedback_selinux_mode),
    DynamicUiText(Regex("^Port ADB berhasil dikunci ke (.+)$"), R.string.dynamic_feedback_adb_port_locked),
    DynamicUiText(Regex("^Gagal menyimpan port ADB (.+)$"), R.string.dynamic_feedback_adb_port_save_failed),
    DynamicUiText(Regex("^KSM diaktifkan$"), R.string.dynamic_feedback_ksm_enabled),
    DynamicUiText(Regex("^KSM dinonaktifkan$"), R.string.dynamic_feedback_ksm_disabled),
    DynamicUiText(Regex("^TCP: (.+) berhasil diterapkan$"), R.string.dynamic_feedback_tcp_applied),
    DynamicUiText(Regex("^Gagal menerapkan TCP (.+) \\(Kernel menolak\\)$"), R.string.dynamic_feedback_tcp_apply_failed),
    DynamicUiText(Regex("^Preset Governor (.+) disetel ke: (.+)$"), R.string.dynamic_feedback_governor_preset),
    DynamicUiText(Regex("^Dmesg diekspor ke (.+)$"), R.string.dynamic_feedback_dmesg_path),
    DynamicUiText(Regex("^DISTRIBUSI FRAMETIME \\((.+) FRAMES\\)$"), R.string.dynamic_benchmark_frametime_distribution),
    DynamicUiText(Regex("^Headphone Gain \\(L/R\\): (.+)dB / (.+)dB$"), R.string.dynamic_headphone_gain),
    DynamicUiText(Regex("^Speaker Gain: (.+)dB$"), R.string.dynamic_speaker_gain),
    DynamicUiText(Regex("^Microphone Gain: (.+)dB$"), R.string.dynamic_microphone_gain),
    DynamicUiText(Regex("^Legacy Minfree Levels: (.+)$"), R.string.dynamic_legacy_minfree_levels),
    DynamicUiText(Regex("^Read Wakeup Threshold: (.+) bits$"), R.string.dynamic_read_wakeup_threshold),
    DynamicUiText(Regex("^Write Wakeup Threshold: (.+) bits$"), R.string.dynamic_write_wakeup_threshold),
    DynamicUiText(Regex("^Fitur Kernel Aktif: (.+)$"), R.string.dynamic_active_kernel_features),
    DynamicUiText(Regex("^Tersedia (.+) step frekuensi clock OPP$"), R.string.dynamic_available_gpu_opp_steps),
    DynamicUiText(Regex("^Edit Tunable: (.+)$"), R.string.dynamic_edit_tunable_title),
    DynamicUiText(Regex("^STATUS INTI CPU \\((.+) CORES\\)$"), R.string.dynamic_cpu_core_status_count),
    DynamicUiText(Regex("^TABEL LENGKAP STEPPING FREKUENSI \\((.+) OPP\\)$"), R.string.dynamic_frequency_step_table_count),
    DynamicUiText(Regex("^Driver Fisik • (.+)$"), R.string.dynamic_physical_gpu_driver),
    DynamicUiText(Regex("^Kondisi: (.+) MHz • (.+)% Load • (.+)$"), R.string.dynamic_gpu_session_condition),
    DynamicUiText(Regex("^Muat Lebih Banyak \\((.+) tersisa\\)$"), R.string.dynamic_load_more_tunables),
    DynamicUiText(Regex("^Skor Kompatibilitas Kernel: (.+)%$"), R.string.dynamic_kernel_compatibility_score),
    DynamicUiText(Regex("^(.+) Sensor Thermal Hardware Aktif$"), R.string.dynamic_thermal_sensor_count),
    DynamicUiText(Regex("^Sumber Wakeup Tertinggi \\((.+)\\)$"), R.string.dynamic_top_wakeup_source_count),
    DynamicUiText(Regex("^(.+)x bangun$"), R.string.dynamic_wakeup_count),
    DynamicUiText(Regex("^Pustaka Skrip Kernel & Sistem \\((.+)\\)$"), R.string.dynamic_script_library_count),
    DynamicUiText(Regex("^Aktif: (.+)$"), R.string.dynamic_tunable_active_value),
    DynamicUiText(Regex("^Min: (.+)$"), R.string.dynamic_tunable_min_value),
    DynamicUiText(Regex("^Max: (.+)$"), R.string.dynamic_tunable_max_value),
    DynamicUiText(Regex("^Rentang: (.+) - (.+) (.*)$"), R.string.dynamic_tunable_value_range),
    DynamicUiText(Regex("^Tidak ada parameter yang cocok dengan '(.+)'$"), R.string.dynamic_parameter_search_empty),
    DynamicUiText(Regex("^(.+) Aturan Profil Dikonfigurasi$"), R.string.dynamic_app_profile_rule_count),
    DynamicUiText(Regex("^Batas Arus Pengisian \\((.+)\\)$"), R.string.dynamic_tunable_charging_current_name),
    DynamicUiText(Regex("^I/O Read-Ahead Cache \\((.+)\\)$"), R.string.dynamic_tunable_read_ahead_name),
    DynamicUiText(Regex("^I/O Scheduler \\((.+)\\)$"), R.string.dynamic_tunable_io_scheduler_name),
    DynamicUiText(Regex("^Thermal Policy \\((.+)\\)$"), R.string.dynamic_tunable_thermal_policy_name),
    DynamicUiText(Regex("^Thermal Throttling \\((.+)\\)$"), R.string.dynamic_tunable_thermal_throttling_name),
    DynamicUiText(Regex("^Algoritma mitigasi panas zona (.+) \\(step_wise, power_allocator, user_space\\)\\.$"), R.string.dynamic_tunable_thermal_policy_description),
    DynamicUiText(Regex("^Status proteksi throttling termal hardware untuk zona (.+)\\.$"), R.string.dynamic_tunable_thermal_status_description),
    DynamicUiText(Regex("^PPM Policy (.+): (.+)$"), R.string.dynamic_tunable_ppm_policy_name),
    DynamicUiText(Regex("^● LOCKED : (.+)$"), R.string.dynamic_network_port_locked),
    DynamicUiText(Regex("^⚡ MEMULIHKAN : (.+)$"), R.string.dynamic_network_port_restoring),
    DynamicUiText(Regex("^Akselerasi MediaTek \\((.+) & FPSGO\\)$"), R.string.dynamic_gpu_mtk_title),
    DynamicUiText(Regex("^Akselerasi Hardware GPU \\((.+)\\)$"), R.string.dynamic_gpu_generic_title),
    DynamicUiText(Regex("^\\+(.+)%$"), R.string.dynamic_gpu_percent_plus),
    DynamicUiText(Regex("^(.+)%$"), R.string.dynamic_gpu_percent),
    DynamicUiText(Regex("^(.+) ms$"), R.string.dynamic_gpu_milliseconds),
    DynamicUiText(Regex("^Kluster (.+) Mid$"), R.string.dynamic_cpu_mid_cluster),
    DynamicUiText(Regex("^Kluster (.+)$"), R.string.dynamic_cpu_cluster),
    DynamicUiText(Regex("^(.+) Dibatasi$"), R.string.dynamic_cpu_performance_limited),
    DynamicUiText(Regex("^(.+) Siaga$"), R.string.dynamic_cpu_performance_standby),
    DynamicUiText(Regex("^(.+) Core Diparkir \\(Offline\\)$"), R.string.dynamic_cpu_cores_offline),
    DynamicUiText(Regex("^(.+) MHz @ (.+) mV$"), R.string.dynamic_cpu_cci_freq_voltage),
    DynamicUiText(Regex("^(.+) MHz$"), R.string.dynamic_cpu_cci_freq),
    DynamicUiText(Regex("^Mode (.+) \\(Gaming Stabil\\)$"), R.string.dynamic_cpu_unified_mode),
    DynamicUiText(Regex("^Batas arus manual disetel ke (.+) mA dengan penguncian sysfs aktif\\.$"), R.string.dynamic_charging_custom_current),
    DynamicUiText(Regex("^Mode Kustom Aktif • (.+) mA$"), R.string.dynamic_charging_custom_pill),
    DynamicUiText(Regex("^Kluster (.+) · (.+)$"), R.string.dynamic_cpu_cluster_revision),
    DynamicUiText(Regex("^Kluster (.+) \\((.+)\\)$"), R.string.dynamic_cpu_cluster_detail),
    DynamicUiText(Regex("^Core (.+) · (.+) · (.+) Level OPP · Gov: (.+)$"), R.string.dynamic_cpu_core_opp_governor),
    DynamicUiText(Regex("^Benchmarking\\.\\.\\. (.+)s$"), R.string.dynamic_hud_benchmark_countdown),
    DynamicUiText(Regex("^Sebagian buffer dikomposisikan via GPU Client Fallback \\((.+)\\)\\.$"), R.string.dynamic_gpu_client_composition),
    DynamicUiText(Regex("^Daftar Node Fisik Driver \\((.+) Node\\)$"), R.string.dynamic_physical_driver_node_list),
    DynamicUiText(Regex("^(.+) Threads$"), R.string.dynamic_process_thread_count),
    DynamicUiText(Regex("^Core (.+)$"), R.string.dynamic_process_cpu_affinity),
    DynamicUiText(Regex("^(-?\\d+) \\((.+)\\)$"), R.string.dynamic_process_nice_priority),
    DynamicUiText(Regex("^Zone (.+)$"), R.string.dynamic_thermal_zone_label),
    DynamicUiText(Regex("^Stab (.+)% · 1%L (.+)$"), R.string.dynamic_hud_stability_percentile),
    DynamicUiText(Regex("^(.+)ms tahan sleep$"), R.string.dynamic_wakelock_prevent_suspend),
    DynamicUiText(Regex("^(.+) Piksel$"), R.string.dynamic_resolution_pixels),
    DynamicUiText(Regex("^HAL: (.+)$"), R.string.dynamic_gpu_hal_architecture),
    DynamicUiText(Regex("^Thermal Throttling: (.+)$"), R.string.dynamic_gpu_thermal_throttling_warning),
    DynamicUiText(Regex("^Saturation: (.+)$"), R.string.dynamic_color_saturation),
    DynamicUiText(Regex("^KCAL Saturation: (.+)$"), R.string.dynamic_kcal_saturation),
    // END GENERATED FEEDBACK DYNAMIC TEXTS
)

private val recommendationUiTextResources = mapOf(
    "Sangat Disarankan [ON] untuk respons bidikan instan" to R.string.recommendation_gaming_instant_response,
    "[OFF] atau [ON] sesuai preferensi kelancaran UI" to R.string.recommendation_balance_ui_smoothness,
    "[OFF] untuk efisiensi daya saat navigasi ringan" to R.string.recommendation_battery_light_navigation,
    "Sangat Disarankan [ON] untuk mencegah frame drop mendadak" to R.string.recommendation_gaming_prevent_drops,
    "[OFF] untuk manajemen arus standar pabrik" to R.string.recommendation_balance_oem_current,
    "[OFF] untuk menjaga konsumsi puncak baterai" to R.string.recommendation_battery_peak_draw,
    "[OFF] dengan cooler eksternal, atau [ON] tanpa cooler" to R.string.recommendation_thermal_cooler,
    "Sangat Disarankan [ON] untuk keamanan suhu harian" to R.string.recommendation_thermal_daily_safety,
    "[ON] efisiensi termal maksimal" to R.string.recommendation_thermal_efficiency,
)

private data class MatchResolution(val resourceId: Int, val rawArguments: List<String>)

private val NO_MATCH_SENTINEL = MatchResolution(-1, emptyList())
private val dynamicResolutionCache = object : android.util.LruCache<String, MatchResolution>(1024) {}

/** Localize static accessibility labels with the same catalog used by visible text. */
@Composable
fun localizedUiText(source: String): String {
    if (source.length <= 1) return source
    legacyUiTextResources[source]?.let { return stringResource(it) }
    recommendationUiTextResources[source]?.let { return stringResource(it) }

    val cached = synchronized(dynamicResolutionCache) { dynamicResolutionCache.get(source) }
    if (cached != null) {
        if (cached === NO_MATCH_SENTINEL) return source
        val localizedArguments = cached.rawArguments.map { argument ->
            legacyUiTextResources[argument]?.let { stringResource(it) } ?: argument
        }
        return stringResource(cached.resourceId, *localizedArguments.toTypedArray())
    }

    for (entry in dynamicUiTextResources) {
        val match = entry.pattern.matchEntire(source) ?: continue
        val rawArgs = match.groupValues.drop(1)
        val resolution = MatchResolution(entry.resource, rawArgs)
        synchronized(dynamicResolutionCache) {
            dynamicResolutionCache.put(source, resolution)
        }
        val localizedArguments = rawArgs.map { argument ->
            legacyUiTextResources[argument]?.let { stringResource(it) } ?: argument
        }
        return stringResource(entry.resource, *localizedArguments.toTypedArray())
    }

    synchronized(dynamicResolutionCache) {
        dynamicResolutionCache.put(source, NO_MATCH_SENTINEL)
    }
    return source
}

/** Compatibility bridge while the legacy UI literals are moved into explicit string resources. */
@Composable
fun LynxText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val localized = localizedUiText(text)
    Material3Text(
        text = localized,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
fun LynxText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    Material3Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout,
        style = style
    )
}

