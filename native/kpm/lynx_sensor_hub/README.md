# 🧭 Lynx Universal — MediaTek SCP Sensor Hub Auto-Sync KPM
> **Platform**: Infinix Note 11S / 11 Pro (`X698`) — MediaTek Helio G96 (`mt6781`)  
> **Kernel**: Linux 4.14.186 (aarch64)  
> **Framework**: APatch / KernelPatch (Ring-0)  
> **Author**: ɴᴏɪʀ  
> **License**: GPL v2  

---

## 📌 Problem & Root Cause
When booting with APatch, a 1-millisecond scheduling race condition occurs at second 20:
Core 1 finishes `sensor_send_dram_info_to_hub`, and 1ms later Core 7 dispatches `scp_sensorHub_req_send`.
Because SCP coprocessor firmware requires 15-40ms to complete cold-boot, the request fails, and MediaTek's driver permanently registers sensors as dormant.

## 🚀 KPM Solution
`lynx_sensor_hub.kpm` hooks `scp_sensorHub_req_send` directly inside the Linux kernel:
- Forces blocking IPI wait mode (`arg2 = 1`).
- Paces early boot packets by 45ms, granting SCP the required window to settle.
- Sensors register naturally on first try before Android Sensor HAL starts at second 22.
- Completely eliminates user-space pokes, HAL restarts, and subsequent system_server soft-reboots!
