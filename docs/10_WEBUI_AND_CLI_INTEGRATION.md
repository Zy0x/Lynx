# 💻 Module 10: WebUI & CLI Integration Architecture
> **Subsystem**: User Interface Layer (`webroot/`, `system/bin/lynx`, `system/bin/Lxcore`)  
> **Environments**: KernelSU Manager WebUI, APatch WebUI, Termux CLI  

---

## 1. WebUI Dashboard Architecture (`webroot/`)

The WebUI serves as the primary dashboard for user interaction on KernelSU and APatch. It is built as a single-page application (SPA) adhering to strict professional standards:

### 1.1 Touch-First & Responsive Geometry
- **Touch Target Bounds**: Every interactive button and switch has a minimum touch footprint of **48 × 48 px**, preventing accidental taps during one-handed mobile use.
- **Aspect Ratio Stability**: Tested across regular and non-regular mobile viewports (1080×2400, 1080×2460, 20:9, 21:9) and tablet displays without layout distortions.
- **Deep OLED Dark Theme**: Background `#0a0a0a`, card containers `#161616`, with high-contrast Cyan (`#00ffaa`) and Electric Blue (`#0099ff`) accents.

### 1.2 KernelSU / APatch Bridge (`ksu.exec`)
WebUI communicates with the underlying Android root shell via the asynchronous `ksu.exec(cmd)` interface:

```javascript
// Asynchronous command execution helper with timeout
async function executeRoot(cmd) {
    if (typeof ksu === 'undefined' || !ksu.exec) {
        throw new Error("KSU_UNAVAILABLE");
    }
    const result = await ksu.exec(cmd);
    if (result.errno !== 0 && result.errno !== undefined) {
        throw new Error(result.stderr || `Exit code ${result.errno}`);
    }
    return result.stdout ? result.stdout.trim() : "";
}
```

### 1.3 Standalone Browser & Magisk Fallback Detection
When a user opens the WebUI inside a standard mobile browser (Chrome/Firefox via HTTP server) or on Magisk without a WebUI container:
- `window.ksu` is `undefined`.
- Instead of crashing silently or logging console errors, the WebUI displays a prominent, informative status card:

```javascript
window.addEventListener('DOMContentLoaded', () => {
    if (typeof ksu === 'undefined' || !ksu.exec) {
        const warningCard = document.getElementById('ksu-warning');
        if (warningCard) {
            warningCard.style.display = 'block';
            warningCard.innerHTML = `
                <div class="banner-warning">
                    ⚠️ <b>Running in Standalone Mode</b><br>
                    KernelSU/APatch WebUI bridge not detected.<br>
                    To configure Lynx on Magisk, run <code>su -c lynx</code> in Termux.
                </div>
            `;
        }
    }
});
```

---

## 2. CLI Tooling Alignment (`Lxcore` & `lynx`)

### 2.1 Directory Reorganization & Backward Compatibility
Because `script/` is reorganized into `core/` and `platforms/`:
1. `system/bin/Lxcore` will have its library path updated from `$MODPATH/script/lib` to `$MODPATH/core/lib`.
2. To prevent breaking any user scripts or legacy addons, `customize.sh` creates a symlink:
   ```bash
   ln -sfn "$MODPATH/core" "$MODPATH/script"
   ```

### 2.2 Case-Sensitivity Bugfix in `system/bin/lynx`
Linux filesystems on Android are strictly case-sensitive. The legacy `system/bin/lynx` script contained several lowercase references:
```bash
# BUGGY LEGACY CODE (Causes file not found on standard Android)
sed -i 's/lynx.cc=.*/lynx.cc=1/' /data/adb/modules/lynx/system.prop
```
**Fix**: All occurrences are normalized to the official module ID `/data/adb/modules/Lynx/system.prop`.

---

## 3. WebUI & CLI IPC via System Properties

All interfaces communicate with the active AI Core daemon through standardized Android system properties:

| Property Name | Allowed Values | Description |
| :--- | :--- | :--- |
| `lynx.mode` | `auto`, `high`, `balance`, `aggresive`, `powersave` | Current active performance profile. |
| `lynx.thermal` | `0` (disabled), `1` (enabled) | Thermal mitigation status. |
| `lynx.ac` | `0` (disabled), `1` (enabled) | AutoCut charging controller. |
| `lynx.max.ac` | Integer (e.g. `85`) | AutoCut upper charge limit percentage. |
| `lynx.min.ac` | Integer (e.g. `80`) | AutoCut lower charge resumption percentage. |
| `lynx.fcc` | `1` to `5` | Fast charge current level (1.5A to 4.0A). |
