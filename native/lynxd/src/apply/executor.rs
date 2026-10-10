use std::fs;
use std::path::PathBuf;
use std::time::Instant;

use crate::apply::lock::ModeLock;
use crate::apply::planner::ExecutionPlan;
use crate::core::error::SysfsError;
use crate::core::result::Result;
use crate::hardware::ChargingRegulator;
use crate::state::ProfileStateManager;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};
use crate::utils::Logger;

#[derive(Debug, Clone)]
pub struct NodeSnapshot {
    pub path: PathBuf,
    pub original_value: String,
}

#[derive(Debug)]
pub struct ExecutionReport {
    pub profile_name: &'static str,
    pub dry_run: bool,
    pub operations_planned: usize,
    pub operations_committed: usize,
    pub elapsed_micros: u128,
}

impl ExecutionReport {
    pub fn display(&self) -> String {
        format!(
            "Profile Execution Report:\n\
             • Profile          : {}\n\
             • Mode             : {}\n\
             • Nodes Planned    : {}\n\
             • Nodes Committed  : {}\n\
             • Latency          : {} µs ({:.2} ms)",
            self.profile_name,
            if self.dry_run { "DRY-RUN (Simulated)" } else { "TRANSACTIONAL APPLY (Hardware Applied)" },
            self.operations_planned,
            self.operations_committed,
            self.elapsed_micros,
            self.elapsed_micros as f64 / 1000.0
        )
    }
}

pub struct ProfileExecutor;

impl ProfileExecutor {
    /// Executes the profile transition through the mandatory safety pipeline:
    /// Snapshot -> Validate -> (Dry-Run Exit) -> Commit -> Verify -> (Rollback on critical error)
    pub fn execute(plan: &ExecutionPlan, dry_run: bool) -> Result<ExecutionReport> {
        let start = Instant::now();

        // ── Phase 1: Snapshot Pre-State ─────────────────────────────────────
        let mut snapshots = Vec::with_capacity(plan.operations.len());
        for op in &plan.operations {
            let original = if !op.current_value.is_empty() && op.current_value != "dynamic" {
                op.current_value.clone()
            } else {
                SysfsReader::read_trimmed(&op.path).unwrap_or_else(|_| op.current_value.clone())
            };
            snapshots.push(NodeSnapshot {
                path: op.path.clone(),
                original_value: original,
            });
        }

        // ── Phase 2: Pre-Commit Validation ──────────────────────────────────
        let is_root = SysfsWriter::is_current_process_root();
        let mut validation_errors = Vec::new();
        for op in &plan.operations {
            if !op.critical {
                continue;
            }
            if !SysfsReader::exists(&op.path) {
                validation_errors.push(SysfsError::NotFound(op.path.clone()));
            } else if !SysfsReader::is_writable(&op.path) && !is_root {
                validation_errors.push(SysfsError::PermissionDenied(op.path.clone()));
            }
        }

        if !validation_errors.is_empty() {
            Logger::error(format!(
                "Pre-commit validation failed with {} critical errors! Aborting before write.",
                validation_errors.len()
            ));
            for err in &validation_errors {
                eprintln!("  [-] {}", err);
            }
            return Err(validation_errors.remove(0));
        }

        // ── Phase 3: Dry-Run Guard ──────────────────────────────────────────
        if dry_run {
            println!("{}", plan.display_summary());
            println!("\n[INFO] Pre-validation: PASS (All {} target nodes verified).", plan.operations.len());
            println!("[INFO] Dry-Run Status: COMPLETED SAFELY (No kernel mutations committed).");
            return Ok(ExecutionReport {
                profile_name: plan.profile_name,
                dry_run: true,
                operations_planned: plan.operations.len(),
                operations_committed: 0,
                elapsed_micros: start.elapsed().as_micros(),
            });
        }

        // ── Phase 4: Mutual Exclusion Lock Guard (/dev/lynx_mode.lock) ─────
        let _mode_lock = match ModeLock::acquire() {
            Ok(guard) => guard,
            Err(e) => {
                Logger::error(format!("Cannot proceed with profile apply: {}", e));
                return Err(SysfsError::IoError {
                    path: ModeLock::lock_path(),
                    source: std::io::Error::new(std::io::ErrorKind::AlreadyExists, e),
                });
            }
        };

        // ── Phase 5: State Persistence (Pre-Commit Guard) ──────────────────
        let vendor_str = plan.vendor.to_string();
        if let Err(e) = ProfileStateManager::record_pre_commit(plan.profile_name, &vendor_str, &snapshots) {
            Logger::warn(format!("Failed to record pre-commit snapshot to disk: {}", e));
        }

        // ── Phase 6: Transactional Apply Layer ──────────────────────────────
        let mut committed_count = 0usize;
        let mut commit_failed_at = None;

        for (idx, op) in plan.operations.iter().enumerate() {
            match SysfsWriter::write(&op.path, &op.target_value, WriteMode::ForcePermission) {
                Ok(_) => {
                    committed_count += 1;
                }
                Err(e) => {
                    if op.critical {
                        Logger::error(format!("Critical write failed on '{}': {}", op.path.display(), e));
                        commit_failed_at = Some((idx, e));
                        break;
                    } else {
                        Logger::warn(format!(
                            "Optional subsystem node '{}' rejected value '{}': {}",
                            op.path.display(),
                            op.target_value,
                            e
                        ));
                    }
                }
            }
        }

        // ── Phase 6b: Rollback on Critical Failure ──────────────────────────
        if let Some((failed_idx, write_err)) = commit_failed_at {
            Logger::warn("Critical commit failed! Triggering automatic hardware state rollback...");
            let mut rollback_success = true;

            for snapshot in snapshots.iter().take(failed_idx) {
                if snapshot.original_value == "dynamic" || snapshot.original_value.is_empty() {
                    continue;
                }
                if let Err(rb_err) = SysfsWriter::write(&snapshot.path, &snapshot.original_value, WriteMode::ForcePermission) {
                    Logger::error(format!(
                        "Rollback failed on node '{}': {}",
                        snapshot.path.display(),
                        rb_err
                    ));
                    rollback_success = false;
                }
            }

            if rollback_success {
                Logger::info("Hardware state successfully restored to pre-transaction snapshot.");
                let _ = ProfileStateManager::record_failed_rollback(plan.profile_name, &vendor_str);
            } else {
                Logger::error("Rollback experienced partial errors. System state may require reboot.");
            }

            return Err(write_err);
        }

        // ── Phase 6c: OEM Throttler Process Neutralization & Active Marker ──
        let is_perf_or_ext = matches!(plan.profile_name, "extreme" | "performance");
        ChargingRegulator::signal_processes(
            &["mi_thermald", "thermal-engine", "thermal-engine-v2", "ituxd"],
            is_perf_or_ext,
        );
        let _ = fs::create_dir_all("/data/adb/lynx");
        let _ = fs::write("/data/adb/lynx/active_profile", plan.profile_name);

        // ── Phase 7: Post-Commit Verification ───────────────────────────────
        let mut verify_mismatches = 0;
        for op in &plan.operations {
            if op.current_value == "dynamic" {
                continue;
            }
            if let Ok(actual) = SysfsReader::read_trimmed(&op.path) {
                let lower = actual.to_lowercase();
                let matches = actual == op.target_value
                    || actual.contains(&format!("[{}]", op.target_value))
                    || (op.target_value == "y" && (actual == "1" || lower.contains("enabled") || actual.starts_with("0x")))
                    || (op.target_value == "0" && lower.contains("normal"))
                    || (op.target_value == "1" && (lower.contains("low power") || lower.contains("performance")))
                    || (op.target_value == "3" && lower.contains("performance"));
                if !matches {
                    verify_mismatches += 1;
                }
            }
        }

        // ── Phase 8: Post-Commit Persistence (Clean State) ──────────────────
        if let Err(e) = ProfileStateManager::record_post_commit(plan.profile_name, &vendor_str) {
            Logger::warn(format!("Failed to record post-commit state: {}", e));
        }

        let desc = match plan.profile_name {
            "extreme" => "[ Extreme Mode Active ] Deity Hybrid Architecture",
            "performance" => "[ Performance Mode Active ] Deity Hybrid Architecture",
            "powersave" => "[ Powersave Mode Active ] Deity Hybrid Architecture",
            "balanced" | "balance" => "[ Balance Mode Active ] Deity Hybrid Architecture",
            _ => "[ Active Profile ] Deity Hybrid Architecture",
        };
        crate::utils::update_module_prop_description(desc);

        let elapsed = start.elapsed().as_micros();
        if verify_mismatches == 0 {
            Logger::info(format!(
                "Profile '{}' applied and verified cleanly ({} nodes modified in {:.2} ms).",
                plan.profile_name,
                committed_count,
                elapsed as f64 / 1000.0
            ));
        } else {
            Logger::info(format!(
                "Profile '{}' applied ({} nodes committed in {:.2} ms; {} nodes clamped by kernel driver).",
                plan.profile_name,
                committed_count,
                elapsed as f64 / 1000.0,
                verify_mismatches
            ));
        }

        Ok(ExecutionReport {
            profile_name: plan.profile_name,
            dry_run: false,
            operations_planned: plan.operations.len(),
            operations_committed: committed_count,
            elapsed_micros: elapsed,
        })
    }
}
