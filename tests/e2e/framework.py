#!/usr/bin/env python3
"""
Lynx Universal Fast Charging Subsystem - E2E Testing Framework & Engine
Architecture: Opaque-box automated test harness for Requirements R1, R2, R3, R4.
Supports dual execution targets:
  1. Virtual Sysfs Mock Sandbox (Hermetic CI / Offline Linux & Windows Host)
  2. Live ADB Device Target (Active Read-Write Test Device Infinix-X698 MT6781)
"""

import os
import sys
import time
import json
import shutil
import tempfile
import subprocess
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any, Callable
from pathlib import Path


# -----------------------------------------------------------------------------
# Data Models & Result Tracking
# -----------------------------------------------------------------------------

@dataclass
class TestCaseResult:
    test_id: str
    tier: int
    feature: str
    title: str
    authoritative_source: str
    status: str  # "PASS", "FAIL", "SKIP", "ERROR"
    duration_s: float
    message: str = ""
    details: Dict[str, Any] = field(default_factory=dict)


@dataclass
class TestSuiteResult:
    suite_name: str
    total: int = 0
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    errors: int = 0
    duration_s: float = 0.0
    results: List[TestCaseResult] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "suite_name": self.suite_name,
            "total": self.total,
            "passed": self.passed,
            "failed": self.failed,
            "skipped": self.skipped,
            "errors": self.errors,
            "duration_s": round(self.duration_s, 4),
            "results": [
                {
                    "test_id": r.test_id,
                    "tier": r.tier,
                    "feature": r.feature,
                    "title": r.title,
                    "authoritative_source": r.authoritative_source,
                    "status": r.status,
                    "duration_s": round(r.duration_s, 4),
                    "message": r.message,
                    "details": r.details,
                }
                for r in self.results
            ],
        }

    def write_json(self, output_path: str):
        Path(output_path).parent.mkdir(parents=True, exist_ok=True)
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(self.to_dict(), f, indent=2)

    def write_junit_xml(self, output_path: str):
        Path(output_path).parent.mkdir(parents=True, exist_ok=True)
        xml = ['<?xml version="1.0" encoding="UTF-8"?>']
        xml.append(f'<testsuite name="{self.suite_name}" tests="{self.total}" failures="{self.failed}" errors="{self.errors}" skipped="{self.skipped}" time="{self.duration_s:.4f}">')
        for r in self.results:
            xml.append(f'  <testcase classname="Tier{r.tier}.{r.feature}" name="{r.test_id}_{r.title}" time="{r.duration_s:.4f}">')
            if r.status == "FAIL":
                xml.append(f'    <failure message="{r.message}">{json.dumps(r.details)}</failure>')
            elif r.status == "ERROR":
                xml.append(f'    <error message="{r.message}">{json.dumps(r.details)}</error>')
            elif r.status == "SKIP":
                xml.append(f'    <skipped message="{r.message}"/>')
            xml.append('  </testcase>')
        xml.append('</testsuite>')
        with open(output_path, "w", encoding="utf-8") as f:
            f.write("\n".join(xml))


# -----------------------------------------------------------------------------
# Target Abstraction: Mock Sandbox vs Live ADB Target
# -----------------------------------------------------------------------------

class BaseTarget:
    """Base interface for interaction with sysfs, kernel state, and daemon."""
    def read_node(self, path: str) -> Optional[str]:
        raise NotImplementedError

    def write_node(self, path: str, value: str) -> bool:
        raise NotImplementedError

    def node_exists(self, path: str) -> bool:
        raise NotImplementedError

    def get_permission(self, path: str) -> int:
        raise NotImplementedError

    def set_permission(self, path: str, mode: int) -> bool:
        raise NotImplementedError

    def execute_command(self, cmd: str) -> subprocess.CompletedProcess:
        raise NotImplementedError

    def cleanup(self):
        pass


class MockSandboxTarget(BaseTarget):
    """
    Hermetic virtual sysfs/proc mock sandbox implementing the full kernel
    hardware abstraction layer for MediaTek MT6781 and Transsion RT9759 charging.
    Allows automated opaque-box testing on any host without root or physical device.
    """
    def __init__(self, root_dir: Optional[str] = None):
        self.temp_dir = tempfile.mkdtemp(prefix="lynx_e2e_sandbox_") if not root_dir else root_dir
        self.root = Path(self.temp_dir).resolve()
        self.is_custom_root = root_dir is not None
        self._permissions: Dict[str, int] = {}
        self._init_layout()

    def _init_layout(self):
        """Build full directory and node hierarchy mirroring Infinix-X698 MT6781 kernel sysfs."""
        dirs = [
            # Battery & Power Supply
            "sys/class/power_supply/battery",
            "sys/class/power_supply/usb",
            "sys/class/power_supply/ac",
            "sys/class/power_supply/charger",
            "sys/class/power_supply/main",
            "sys/class/qcom-battery",
            # MediaTek Charger Platform
            "sys/devices/platform/charger",
            # Transsion ODM Battery Platform
            "sys/devices/platform/odm/odm:tran_battery",
            "sys/devices/platform/tran_battery",
            "sys/devices/platform/battery",
            # Transsion PCA DV2 Algorithm
            "sys/devices/platform/pca_dv2_algo",
            # Devicetree binary nodes
            "sys/firmware/devicetree/base/odm/mtk_pe50",
            # MediaTek Thermal Kernel Driver
            "proc/driver/thermal",
            # Cooling devices 50-60
            "sys/class/thermal",
            # RT9759 Direct Charge Pump I2C driver
            "sys/bus/i2c/drivers/rt9759/5-0066",
            "sys/devices/platform/11016000.i2c5/i2c-5/5-0066/rt_regmap_rt9759",
            # User-space /dev runtime nodes
            "dev",
            # Lynx Module Configuration
            "data/adb/modules/Lynx",
        ]
        for d in dirs:
            self._resolve(d).mkdir(parents=True, exist_ok=True)

        for i in range(53, 58):
            self._resolve(f"sys/class/thermal/cooling_device{i}").mkdir(parents=True, exist_ok=True)

        # Populate initial baseline values
        self._populate_baselines()

    def _populate_baselines(self):
        """Set safe OEM defaults matching un-modified Infinix Note 11S state."""
        defaults = {
            # Battery nodes
            "sys/class/power_supply/battery/capacity": "45",
            "sys/class/power_supply/battery/status": "Charging",
            "sys/class/power_supply/battery/health": "Good",
            "sys/class/power_supply/battery/temp": "310",  # 31.0 C
            "sys/class/power_supply/battery/voltage_now": "4020000",  # 4020 mV (CC phase)
            "sys/class/power_supply/battery/current_now": "4100000",  # 4100 mA default
            "sys/class/power_supply/battery/charging_enabled": "1",
            "sys/class/power_supply/battery/input_suspend": "0",
            "sys/class/power_supply/battery/fastcharge_mode": "0",
            "sys/class/power_supply/battery/fast_charge": "0",
            "sys/class/power_supply/battery/boost_current": "0",
            "sys/class/power_supply/battery/constant_charge_current_max": "4500000",
            "sys/class/power_supply/battery/constant_charge_current": "4500000",
            "sys/class/power_supply/battery/current_max": "4500000",
            "sys/class/power_supply/battery/input_current_limit": "4500000",
            "sys/class/power_supply/battery/charge_control_limit_max": "0",
            "sys/class/power_supply/battery/charge_control_limit": "0",
            "sys/class/power_supply/battery/thermal_input_current_limit": "0",
            # USB & AC Rails
            "sys/class/power_supply/usb/online": "1",
            "sys/class/power_supply/usb/type": "USB_PD",
            "sys/class/power_supply/usb/current_max": "3000000",
            "sys/class/power_supply/usb/hw_current_max": "3300000",
            "sys/class/power_supply/ac/online": "1",
            "sys/class/power_supply/charger/online": "1",
            # MediaTek Charger Platform
            "sys/devices/platform/charger/ADC_Charger_Voltage": "9280",  # 9.28V (RFC pump voltage)
            "sys/devices/platform/charger/Charger_Type": "9",           # Fast Charger / RFC
            "sys/devices/platform/charger/pe20": "1",
            "sys/devices/platform/charger/pe40": "1",
            "sys/devices/platform/charger/enable_sc": "0",              # Off by default until hyper
            "sys/devices/platform/charger/sc_ibat_limit": "2000",
            "sys/devices/platform/charger/input_current": "24576",
            "sys/devices/platform/charger/chg1_current": "24576",
            "sys/devices/platform/charger/chg2_current": "24576",
            "sys/devices/platform/charger/bypass_charger": "0",
            "sys/devices/platform/charger/tran_charger_full": "0",
            "sys/devices/platform/charger/BN_TestMode": "0",
            "sys/devices/platform/charger/BatteryNotify": "1",
            "sys/devices/platform/charger/sw_jeita": "1",
            "sys/devices/platform/charger/pdc_max_watt": "33",
            # Transsion ODM battery nodes
            "sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug": "[45,3000,50,2000,1000]",
            "sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger": "9280",
            "sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger": "315",  # 3150 mA
            "sys/devices/platform/odm/odm:tran_battery/Charging_CallState": "0",
            "sys/devices/platform/battery/Battery_Temperature": "31",
            # Transsion PCA DV2 Algo
            "sys/devices/platform/pca_dv2_algo/dv2_debug": "101 43 45 50 500 1000 1500",
            # MediaTek Thermal Kernel Nodes
            "proc/driver/thermal/clabcct": "1",
            "proc/driver/thermal/clabcct_lcmoff": "0",
            "proc/driver/thermal/tzbts_param": "45000",
            # Cooling Devices 53-57
            "sys/class/thermal/cooling_device53/type": "mtk-cl-bcct00",
            "sys/class/thermal/cooling_device53/cur_state": "0",
            "sys/class/thermal/cooling_device54/type": "mtk-cl-bcct01",
            "sys/class/thermal/cooling_device54/cur_state": "0",
            "sys/class/thermal/cooling_device55/type": "mtk-cl-bcct02",
            "sys/class/thermal/cooling_device55/cur_state": "0",
            "sys/class/thermal/cooling_device56/type": "abcct",
            "sys/class/thermal/cooling_device56/cur_state": "1",  # Active throttling baseline
            "sys/class/thermal/cooling_device57/type": "abcct_lcmoff",
            "sys/class/thermal/cooling_device57/cur_state": "0",
            # RT9759 Direct Charge Pump I2C
            "sys/bus/i2c/drivers/rt9759/5-0066/Ibus": "3150000",
            "sys/bus/i2c/drivers/rt9759/5-0066/Vbat": "4020000",
            "sys/bus/i2c/drivers/rt9759/5-0066/Ibat": "6100000",
            "sys/bus/i2c/drivers/rt9759/5-0066/rfc_dcp_ta": "1",
            # Config
            "data/adb/modules/Lynx/config.json": json.dumps({
                "charging": {
                    "charging_mode": "standard",
                    "extreme_charging_enabled": False,
                    "bypass_enabled": False,
                    "limit_current_ma": 3000,
                    "max_battery_percent": 100,
                    "high_current_target_percent": 80,
                    "temp_cutoff_c": 45,
                    "thermal_lockout_bypass_enabled": False,
                    "smart_tapering_enabled": True,
                    "emergency_temp_guard_enabled": True,
                    "night_sleep_guard_enabled": False
                }
            }, indent=2)
        }
        for rel_path, val in defaults.items():
            self.write_node(rel_path, val)

    def _clean_key(self, path: str) -> str:
        return path.replace("\\", "/").lstrip("/")

    def _resolve(self, path: str) -> Path:
        clean = self._clean_key(path)
        # On Windows host (os.name == 'nt'), replace colon in directory/file names (e.g. 'odm:tran_battery')
        if os.name == 'nt' and ':' in clean:
            clean = clean.replace(':', '__colon__')
        return self.root / clean

    def read_node(self, path: str) -> Optional[str]:
        target = self._resolve(path)
        if not target.exists() or target.is_dir():
            return None
        try:
            with open(target, "r", encoding="utf-8", errors="replace") as f:
                return f.read().strip()
        except Exception:
            return None

    def write_node(self, path: str, value: str) -> bool:
        target = self._resolve(path)
        target.parent.mkdir(parents=True, exist_ok=True)
        try:
            with open(target, "w", encoding="utf-8") as f:
                f.write(str(value).strip() + "\n")
            return True
        except Exception:
            return False

    def node_exists(self, path: str) -> bool:
        return self._resolve(path).exists()

    def get_permission(self, path: str) -> int:
        clean = self._clean_key(path)
        if clean in self._permissions:
            return self._permissions[clean]
        target = self._resolve(path)
        if not target.exists():
            return 0
        return target.stat().st_mode & 0o777

    def set_permission(self, path: str, mode: int) -> bool:
        clean = self._clean_key(path)
        self._permissions[clean] = mode
        target = self._resolve(path)
        if not target.exists():
            return False
        try:
            target.chmod(mode)
            return True
        except Exception:
            return True

    def execute_command(self, cmd: str) -> subprocess.CompletedProcess:
        """
        Executes internal simulation state changes or external CLI tools.
        For sandbox, we provide a deterministic simulation dispatcher for `lynxd charging`.
        """
        if "lynxd charging mode hyper" in cmd or "apply_hyper_charging" in cmd:
            return self._sim_apply_hyper()
        elif "lynxd charging mode standard" in cmd:
            return self._sim_apply_standard()
        elif "lynxd charging mode cool" in cmd:
            return self._sim_apply_cool()
        elif "lynxd charging disconnect" in cmd or "handle_disconnect" in cmd:
            return self._sim_disconnect()
        elif "lynxd charging status" in cmd:
            return self._sim_status()
        
        # Generic shell execution
        return subprocess.run(cmd, shell=True, capture_output=True, text=True)

    def _sim_apply_hyper(self) -> subprocess.CompletedProcess:
        """Simulate execution of `lynxd charging mode hyper` within the sandbox."""
        # 1. State marker
        self.write_node("dev/lynx_extreme_charging", "hyper-12000")
        self.write_node("dev/lynx_charging_state", "state=hyper cap=45\n")
        
        # 2. Clear battery suspends and enable fastcharge
        self.write_node("sys/class/power_supply/battery/input_suspend", "0")
        self.write_node("sys/class/power_supply/battery/charging_enabled", "1")
        self.write_node("sys/class/power_supply/battery/fastcharge_mode", "1")
        self.write_node("sys/class/power_supply/battery/fast_charge", "1")
        self.write_node("sys/class/power_supply/battery/boost_current", "1")
        self.write_node("sys/class/power_supply/battery/constant_charge_current_max", "12000000")
        self.write_node("sys/class/power_supply/battery/constant_charge_current", "12000000")
        self.write_node("sys/class/power_supply/battery/input_current_limit", "12000000")

        # 3. MediaTek Platform Enforcements
        self.write_node("sys/devices/platform/charger/pe20", "1")
        self.write_node("sys/devices/platform/charger/pe40", "1")
        self.write_node("sys/devices/platform/charger/enable_sc", "1")
        self.write_node("sys/devices/platform/charger/BN_TestMode", "1")
        self.write_node("sys/devices/platform/charger/BatteryNotify", "0")
        self.write_node("sys/devices/platform/charger/sw_jeita", "0")
        self.write_node("sys/devices/platform/charger/input_current", "33170")
        self.write_node("sys/devices/platform/charger/sc_ibat_limit", "12000")
        self.write_node("sys/devices/platform/charger/chg1_current", "33170")
        self.write_node("sys/devices/platform/charger/chg2_current", "33170")
        self.write_node("sys/devices/platform/charger/pdc_max_watt", "120")

        # 4. Thermal overrides
        self.write_node("sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug", "[95,6000,100,6000,6000]")
        self.write_node("sys/devices/platform/pca_dv2_algo/dv2_debug", "101 95 98 100 0 0 0")
        self.write_node("sys/devices/platform/battery/Battery_Temperature", "28")
        self.set_permission("sys/devices/platform/battery/Battery_Temperature", 0o444)

        # 5. MediaTek ABCCT & Cooling Devices
        self.write_node("proc/driver/thermal/clabcct", "0")
        self.write_node("sys/class/thermal/cooling_device56/cur_state", "0")

        # 6. RT9759 2:1 pump active state calculation
        # Net current = 2 * 3317mA * 0.97 - 350mA (sys) = ~6085mA (> 6,000,000 uA)
        self.write_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibus", "3300000")
        self.write_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibat", "6400000")
        self.write_node("sys/class/power_supply/battery/current_now", "6050000")
        self.write_node("sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger", "330")

        return subprocess.CompletedProcess(
            args=["lynxd", "charging", "mode", "hyper"],
            returncode=0,
            stdout="[OK] Charging Mode set to: HYPER (Unconstrained Peak)\n",
            stderr=""
        )

    def _sim_apply_standard(self) -> subprocess.CompletedProcess:
        """Simulate execution of `lynxd charging mode standard`."""
        # Remove hyper nodes
        target_extreme = self._resolve("dev/lynx_extreme_charging")
        if target_extreme.exists():
            target_extreme.unlink()
        self.write_node("dev/lynx_charging_state", "state=standard cap=45\n")

        self.write_node("sys/devices/platform/charger/enable_sc", "0")
        self.write_node("sys/devices/platform/charger/BN_TestMode", "0")
        self.write_node("sys/devices/platform/charger/BatteryNotify", "1")
        self.write_node("sys/devices/platform/charger/sw_jeita", "1")
        self.write_node("sys/devices/platform/charger/input_current", "24576")
        self.write_node("sys/devices/platform/charger/sc_ibat_limit", "2000")
        self.write_node("sys/devices/platform/charger/pdc_max_watt", "33")
        self.write_node("sys/class/power_supply/battery/fastcharge_mode", "0")
        self.write_node("sys/class/power_supply/battery/fast_charge", "0")

        # Restore thermal defaults
        self.write_node("sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug", "[45,3000,50,2000,1000]")
        self.write_node("sys/devices/platform/pca_dv2_algo/dv2_debug", "101 43 45 50 500 1000 1500")
        self.set_permission("sys/devices/platform/battery/Battery_Temperature", 0o666)
        self.write_node("sys/devices/platform/battery/Battery_Temperature", "31")
        self.write_node("proc/driver/thermal/clabcct", "1")
        self.write_node("sys/class/thermal/cooling_device56/cur_state", "1")

        self.write_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibus", "1500000")
        self.write_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibat", "2400000")
        self.write_node("sys/class/power_supply/battery/current_now", "2100000")

        return subprocess.CompletedProcess(
            args=["lynxd", "charging", "mode", "standard"],
            returncode=0,
            stdout="[OK] Charging Mode set to: STANDARD (OEM Adaptive)\n",
            stderr=""
        )

    def _sim_apply_cool(self) -> subprocess.CompletedProcess:
        """Simulate cool mode (1000-1200 mA strict limit)."""
        target_extreme = self._resolve("dev/lynx_extreme_charging")
        if target_extreme.exists():
            target_extreme.unlink()
        self.write_node("dev/lynx_charging_state", "state=cool cap=45\n")
        self.write_node("sys/devices/platform/charger/enable_sc", "0")
        self.write_node("sys/devices/platform/charger/BN_TestMode", "0")
        self.write_node("sys/devices/platform/charger/pdc_max_watt", "5")
        self.write_node("sys/devices/platform/charger/input_current", "1000")
        self.write_node("sys/devices/platform/charger/sc_ibat_limit", "1000")
        self.write_node("sys/class/power_supply/battery/current_now", "950000")

        return subprocess.CompletedProcess(
            args=["lynxd", "charging", "mode", "cool"],
            returncode=0,
            stdout="[OK] Charging Mode set to: COOL (1000 mA)\n",
            stderr=""
        )

    def _sim_disconnect(self) -> subprocess.CompletedProcess:
        """Simulate unplug event and immediate fail-safe reversion."""
        # Unset online rails
        self.write_node("sys/class/power_supply/usb/online", "0")
        self.write_node("sys/class/power_supply/ac/online", "0")
        self.write_node("sys/class/power_supply/charger/online", "0")
        self.write_node("sys/class/power_supply/battery/status", "Discharging")
        self.write_node("sys/devices/platform/charger/Charger_Type", "0")
        self.write_node("sys/devices/platform/charger/ADC_Charger_Voltage", "0")

        # Cleanup control files
        for p in ["dev/lynx_extreme_charging", "dev/lynx_standard_charging", "dev/lynx_bypass_active", "dev/lynx_charging_guard"]:
            target = self._resolve(p)
            if target.exists():
                target.unlink()

        # Revert all hardware registers to OEM defaults
        self.write_node("sys/devices/platform/charger/enable_sc", "0")
        self.write_node("sys/devices/platform/charger/BN_TestMode", "0")
        self.write_node("sys/devices/platform/charger/BatteryNotify", "1")
        self.write_node("sys/devices/platform/charger/sw_jeita", "1")
        self.write_node("sys/devices/platform/charger/pe20", "0")
        self.write_node("sys/devices/platform/charger/pe40", "0")
        self.write_node("sys/devices/platform/charger/bypass_charger", "0")
        self.write_node("sys/devices/platform/charger/tran_charger_full", "0")
        self.write_node("sys/devices/platform/charger/input_current", "24576")
        self.write_node("sys/devices/platform/charger/sc_ibat_limit", "2000")
        self.write_node("sys/devices/platform/charger/pdc_max_watt", "33")
        self.write_node("sys/class/power_supply/battery/fastcharge_mode", "0")
        self.write_node("sys/class/power_supply/battery/fast_charge", "0")

        # Revert thermal & daemons
        self.write_node("sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug", "[45,3000,50,2000,1000]")
        self.write_node("sys/devices/platform/pca_dv2_algo/dv2_debug", "101 43 45 50 500 1000 1500")
        self.set_permission("sys/devices/platform/battery/Battery_Temperature", 0o666)
        self.write_node("sys/devices/platform/battery/Battery_Temperature", "65535")
        self.write_node("proc/driver/thermal/clabcct", "1")
        self.write_node("sys/class/thermal/cooling_device56/cur_state", "1")

        # Battery discharging current (negative, -350 mA)
        self.write_node("sys/class/power_supply/battery/current_now", "-350000")
        self.write_node("dev/lynx_charging_state", "state=discharging cap=45\n")

        return subprocess.CompletedProcess(
            args=["lynxd", "charging", "disconnect"],
            returncode=0,
            stdout="[OK] Disconnect fail-safe executed successfully: all nodes restored to OEM baseline.\n",
            stderr=""
        )

    def _sim_status(self) -> subprocess.CompletedProcess:
        """Simulate `lynxd charging status` JSON output."""
        cap = int(self.read_node("sys/class/power_supply/battery/capacity") or "45")
        stat = self.read_node("sys/class/power_supply/battery/status") or "Charging"
        cur_ua = int(self.read_node("sys/class/power_supply/battery/current_now") or "0")
        volt_uv = int(self.read_node("sys/class/power_supply/battery/voltage_now") or "0")
        adpv = int(self.read_node("sys/devices/platform/charger/ADC_Charger_Voltage") or "0")
        ibus = int(self.read_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibus") or "0") // 1000
        cur_ma = abs(cur_ua) // 1000 if abs(cur_ua) >= 10000 else abs(cur_ua)
        volt_mv = volt_uv // 1000 if abs(volt_uv) >= 10000 else volt_uv
        enable_sc = self.read_node("sys/devices/platform/charger/enable_sc") == "1"

        data = {
            "capacity": cap,
            "status": stat,
            "health": "Good",
            "temperature_c": 31.0,
            "real_physical_temp_c": 31.0,
            "voltage_mv": volt_mv,
            "current_ma": cur_ma,
            "adapter_voltage_mv": adpv,
            "adapter_current_ma": ibus,
            "active_ic": "Direct Charge Pump (RT9759 2:1)" if enable_sc else "Switching Buck Converter (RT9471/Universal)",
            "fast_charge_protocol": "Transsion Super Charge (33W/45W/68W RFC)" if adpv > 8500 else "Standard Charging",
            "rfc_authenticated": True,
            "emergency_guard_active": False,
            "overnight_bypass_latched": False,
            "smart_limit_latched": False,
            "bypass_active": False,
            "smart_tapering_active": False,
            "charging_mode": "hyper" if enable_sc else "standard",
            "bypass_enabled": False,
            "extreme_charging_enabled": enable_sc,
            "limit_current_ma": 3000,
            "max_battery_percent": 100,
            "high_current_target_percent": 80,
            "temp_cutoff_c": 45,
            "thermal_lockout_bypass_enabled": enable_sc,
            "smart_tapering_enabled": True,
            "emergency_temp_guard_enabled": True,
            "night_sleep_guard_enabled": False
        }
        return subprocess.CompletedProcess(
            args=["lynxd", "charging", "status"],
            returncode=0,
            stdout=json.dumps(data, indent=2),
            stderr=""
        )

    def cleanup(self):
        if not self.is_custom_root and self.root.exists():
            shutil.rmtree(self.root, ignore_errors=True)


class LiveAdbTarget(BaseTarget):
    """
    Live ADB target for physical Android test device (Infinix-X698 MT6781).
    Validates on-device sysfs nodes, dmesg, and binary daemon execution.
    """
    def __init__(self, serial: Optional[str] = None):
        self.serial = serial
        self._verify_connection()

    def _adb_cmd(self, shell_args: str) -> List[str]:
        base = ["adb"]
        if self.serial:
            base.extend(["-s", self.serial])
        base.extend(["shell", shell_args])
        return base

    def _verify_connection(self):
        cmd = ["adb"]
        if self.serial:
            cmd.extend(["-s", self.serial])
        cmd.extend(["get-state"])
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0 or "device" not in res.stdout:
            raise RuntimeError(f"ADB device not connected or unauthorized: {res.stderr.strip()}")

    def read_node(self, path: str) -> Optional[str]:
        cmd = self._adb_cmd(f"su -c 'cat \"{path}\" 2>/dev/null'")
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        if res.returncode != 0:
            return None
        return res.stdout.strip()

    def write_node(self, path: str, value: str) -> bool:
        cmd = self._adb_cmd(f"su -c 'echo \"{value}\" > \"{path}\" 2>/dev/null'")
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        return res.returncode == 0

    def node_exists(self, path: str) -> bool:
        cmd = self._adb_cmd(f"su -c '[ -e \"{path}\" ] && echo 1 || echo 0'")
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        return res.stdout.strip() == "1"

    def get_permission(self, path: str) -> int:
        cmd = self._adb_cmd(f"su -c 'stat -c \"%a\" \"{path}\" 2>/dev/null'")
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        try:
            return int(res.stdout.strip(), 8)
        except Exception:
            return 0

    def set_permission(self, path: str, mode: int) -> bool:
        cmd = self._adb_cmd(f"su -c 'chmod {mode:o} \"{path}\" 2>/dev/null'")
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        return res.returncode == 0

    def execute_command(self, cmd: str) -> subprocess.CompletedProcess:
        adb_exec = self._adb_cmd(f"su -c '{cmd}'")
        return subprocess.run(adb_exec, capture_output=True, text=True, timeout=15)


# -----------------------------------------------------------------------------
# Base Test Case Class
# -----------------------------------------------------------------------------

class E2ETestCase:
    """Base class for all E2E automated test cases."""
    def __init__(
        self,
        test_id: str,
        tier: int,
        feature: str,
        title: str,
        authoritative_source: str,
        description: str,
    ):
        self.test_id = test_id
        self.tier = tier
        self.feature = feature
        self.title = title
        self.authoritative_source = authoritative_source
        self.description = description

    def run(self, target: BaseTarget) -> TestCaseResult:
        start_time = time.perf_counter()
        try:
            self.setup(target)
            msg, details = self.execute(target)
            duration = time.perf_counter() - start_time
            return TestCaseResult(
                test_id=self.test_id,
                tier=self.tier,
                feature=self.feature,
                title=self.title,
                authoritative_source=self.authoritative_source,
                status="PASS",
                duration_s=duration,
                message=msg,
                details=details
            )
        except AssertionError as e:
            duration = time.perf_counter() - start_time
            return TestCaseResult(
                test_id=self.test_id,
                tier=self.tier,
                feature=self.feature,
                title=self.title,
                authoritative_source=self.authoritative_source,
                status="FAIL",
                duration_s=duration,
                message=str(e),
                details={"error_type": "AssertionError"}
            )
        except Exception as e:
            duration = time.perf_counter() - start_time
            return TestCaseResult(
                test_id=self.test_id,
                tier=self.tier,
                feature=self.feature,
                title=self.title,
                authoritative_source=self.authoritative_source,
                status="ERROR",
                duration_s=duration,
                message=f"Exception: {str(e)}",
                details={"error_type": type(e).__name__}
            )
        finally:
            try:
                self.teardown(target)
            except Exception:
                pass

    def setup(self, target: BaseTarget):
        """Prepare target state prior to test execution."""
        pass

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        """Main test logic. Must return (message, details) or raise AssertionError."""
        raise NotImplementedError

    def teardown(self, target: BaseTarget):
        """Clean up target state after test execution."""
        pass
