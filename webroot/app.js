// Lynx Universal - WebUI Frontend Logic & 2-Way Sync Engine
let currentState = null;
let isUpdating = false;

// 1. Root Execution Bridge (KernelSU, APatch, or Local HTTP Daemon)
async function execCmd(cmd) {
    if (typeof ksu !== 'undefined' && ksu.exec) {
        return new Promise((resolve) => {
            ksu.exec(cmd, "{}", (errno, stdout, stderr) => {
                resolve({ errno, stdout, stderr });
            });
        });
    } else {
        // Fallback to local HTTP micro-daemon endpoint
        try {
            const res = await fetch(`/api/exec?cmd=${encodeURIComponent(cmd)}`);
            const data = await res.json();
            return { errno: data.errno || 0, stdout: data.stdout || '', stderr: data.stderr || '' };
        } catch (e) {
            console.warn("Stand-alone browser mode: root command unavailable.", cmd);
            return { errno: -1, stdout: '', stderr: 'Root bridge unavailable in standalone browser mode' };
        }
    }
}

function logToConsole(text, type = 'info') {
    const consoleEl = document.getElementById('console');
    if (!consoleEl) return;
    const entry = document.createElement('div');
    entry.className = type;
    const time = new Date().toLocaleTimeString();
    entry.textContent = `[${time}] ${text}`;
    consoleEl.appendChild(entry);
    consoleEl.scrollTop = consoleEl.scrollHeight;
}

// 2. Fetch and Sync State from config.json
async function syncState() {
    if (isUpdating) return;
    try {
        let jsonStr = '';
        if (typeof ksu !== 'undefined' && ksu.exec) {
            const res = await execCmd("cat /data/adb/modules/Lynx/config.json 2>/dev/null");
            jsonStr = res.stdout;
        } else {
            const res = await fetch('/config.json');
            jsonStr = await res.text();
        }

        if (jsonStr) {
            const state = JSON.parse(jsonStr);
            currentState = state;
            renderUI(state);
        }
    } catch (e) {
        // Ignore JSON parse errors during file rewrite
    }
}

// 3. Render UI Components from State
function renderUI(state) {
    // Status Badge & Info
    const badge = document.getElementById('status-badge');
    const profile = state.active_profile || 'dormant';
    if (badge) {
        badge.textContent = profile.toUpperCase();
        badge.className = `badge ${profile}`;
    }

    // Dormant First-Setup Banner
    const dormantBanner = document.getElementById('dormant-banner');
    if (dormantBanner) {
        dormantBanner.style.display = (profile === 'dormant' || state.setup_pending) ? 'block' : 'none';
    }

    // Profile Buttons
    document.querySelectorAll('.btn-profile').forEach(btn => {
        const p = btn.dataset.profile;
        if (p === profile) {
            btn.classList.add('active');
            if (p === 'extreme') btn.classList.add('extreme');
        } else {
            btn.classList.remove('active', 'extreme');
        }
    });

    // Overclock Settings
    const ocToggle = document.getElementById('oc-toggle');
    if (ocToggle && !ocToggle.matches(':focus')) {
        ocToggle.checked = !!(state.overclock && state.overclock.enabled);
    }
    const ocFloor = document.getElementById('oc-floor-slider');
    const ocFloorVal = document.getElementById('oc-floor-val');
    if (ocFloor && !ocFloor.matches(':active')) {
        const val = (state.overclock && state.overclock.cpu_floor_ratio) || 85;
        ocFloor.value = val;
        if (ocFloorVal) ocFloorVal.textContent = `${val}%`;
    }

    // Memory Settings
    const zramSlider = document.getElementById('zram-slider');
    const zramVal = document.getElementById('zram-val');
    if (zramSlider && !zramSlider.matches(':active')) {
        const mb = (state.memory && state.memory.zram_size_mb) || 2048;
        zramSlider.value = mb;
        if (zramVal) zramVal.textContent = `${mb} MB`;
    }

    const swapSlider = document.getElementById('swap-slider');
    const swapVal = document.getElementById('swap-val');
    if (swapSlider && !swapSlider.matches(':active')) {
        const sw = (state.memory && state.memory.swappiness) || 80;
        swapSlider.value = sw;
        if (swapVal) swapVal.textContent = sw;
    }

    // Charging Settings
    const bypassToggle = document.getElementById('bypass-toggle');
    if (bypassToggle && !bypassToggle.matches(':focus')) {
        bypassToggle.checked = !!(state.charging && state.charging.bypass_enabled);
    }

    const tempSlider = document.getElementById('temp-cutoff-slider');
    const tempVal = document.getElementById('temp-cutoff-val');
    if (tempSlider && !tempSlider.matches(':active')) {
        const c = (state.charging && state.charging.temp_cutoff_c) || 45;
        tempSlider.value = c;
        if (tempVal) tempVal.textContent = `${c}°C`;
    }

    const currentSlider = document.getElementById('current-limit-slider');
    const currentVal = document.getElementById('current-limit-val');
    if (currentSlider && !currentSlider.matches(':active')) {
        const ma = (state.charging && state.charging.limit_current_ma) || 1500;
        currentSlider.value = ma;
        if (currentVal) currentVal.textContent = `${ma} mA`;
    }

    // Advanced Engine Settings
    const wifiToggle = document.getElementById('wifi-ping-toggle');
    if (wifiToggle && !wifiToggle.matches(':focus')) {
        wifiToggle.checked = !!(state.network && state.network.wifi_ping_stabilizer);
    }

    const touchToggle = document.getElementById('touchboost-toggle');
    if (touchToggle && !touchToggle.matches(':focus')) {
        touchToggle.checked = !!(state.display_touch && state.display_touch.touchboost);
    }

    const audioToggle = document.getElementById('audio-mmap-toggle');
    if (audioToggle && !audioToggle.matches(':focus')) {
        audioToggle.checked = !!(state.audio && state.audio.low_latency_mmap);
    }

    const joyoseToggle = document.getElementById('joyose-toggle');
    if (joyoseToggle && !joyoseToggle.matches(':focus')) {
        joyoseToggle.checked = !!(state.oem_neutralizer && state.oem_neutralizer.joyose_neutralize);
    }

    const thermalToggle = document.getElementById('thermal-bypass-toggle');
    if (thermalToggle && !thermalToggle.matches(':focus')) {
        thermalToggle.checked = !!(state.thermal && state.thermal.full_bypass);
    }

    const uclampSlider = document.getElementById('uclamp-slider');
    const uclampVal = document.getElementById('uclamp-val');
    if (uclampSlider && !uclampSlider.matches(':active')) {
        const u = (state.uclamp && state.uclamp.game_min_ratio) || 70;
        uclampSlider.value = u;
        if (uclampVal) uclampVal.textContent = `${u}%`;
    }

    const customTempSlider = document.getElementById('custom-temp-slider');
    const customTempVal = document.getElementById('custom-temp-val');
    if (customTempSlider && !customTempSlider.matches(':active')) {
        const ct = (state.thermal && state.thermal.custom_temp_limit_c) || 50;
        customTempSlider.value = ct;
        if (customTempVal) customTempVal.textContent = `${ct}°C`;
    }

    // TCP Congestion Active Button
    const tcp = (state.network && state.network.tcp_congestion) || 'bbr';
    document.querySelectorAll('#tcp-container button').forEach(b => b.classList.remove('active'));
    const tcpBtn = document.getElementById(`tcp-${tcp}`);
    if (tcpBtn) tcpBtn.classList.add('active');

    // Display Refresh Rate Active Button
    const rr = (state.display_touch && state.display_touch.refresh_rate_lock) || 120;
    document.querySelectorAll('#refresh-rate-container button').forEach(b => b.classList.remove('active'));
    const rrBtn = document.getElementById(`rr-${rr}`);
    if (rrBtn) rrBtn.classList.add('active');

    // Hardware / SoC Info
    const socEl = document.getElementById('soc-info');
    if (socEl) {
        if (state.hardware && state.hardware.soc_name) {
            const spoofTag = state.is_spoofed ? ' [Spoofed]' : '';
            socEl.textContent = `${state.hardware.soc_name} (${state.hardware.soc_type || 'unknown'})${spoofTag}`;
        } else if (state.target_soc) {
            const socLabel = state.target_soc === 'qcom' ? 'Qualcomm' :
                             state.target_soc === 'mtk'  ? 'MediaTek'  : 'Generic';
            const spoofTag = state.is_spoofed ? ' [Spoofed]' : '';
            socEl.textContent = `${socLabel}${spoofTag}`;
        }
    }
}

// 4. Save and Mutate State Atomically
const LYNXD = "/data/adb/modules/Lynx/system/bin/lynxd";

async function updateStateKey(key, value, isString = false) {
    isUpdating = true;
    logToConsole(`Updating ${key} -> ${value}...`);
    const cmd = `${LYNXD} state set "${key}" "${value}" ${isString ? 'str' : 'val'}`;
    const res = await execCmd(cmd);
    if (res.errno === 0) {
        logToConsole(`Set ${key} = ${value} success`, 'success');
    } else {
        logToConsole(`Failed setting ${key}: ${res.stderr}`, 'error');
    }
    isUpdating = false;
    await syncState();
}

// 5. User Interaction Handlers
async function setProfile(profile) {
    if (profile === 'extreme') {
        const confirmExtreme = confirm("PERINGATAN MODE EXTREME:\nMode ini membuka batas termal dan mengunci frekuensi CPU/GPU 100%. Disarankan menggunakan cooler pendingin eksternal untuk menjaga suhu silikon SoC. Lanjutkan?");
        if (!confirmExtreme) return;
    }
    if (profile === 'auto') {
        await execCmd(`${LYNXD} daemon status >/dev/null 2>&1 || nohup ${LYNXD} daemon run >/dev/null 2>&1 &`);
    } else if (profile === 'dormant') {
        await execCmd(`${LYNXD} daemon stop >/dev/null 2>&1; ${LYNXD} profile revert`);
    } else {
        const mapped = profile === 'high' ? 'performance' : profile === 'aggressive' ? 'extreme' : profile;
        await execCmd(`${LYNXD} profile apply "${mapped}"`);
    }
    await updateStateKey('active_profile', profile, true);
    await updateStateKey('setup_pending', 'false');
    pollNativeStatus();
}

async function exportBugReport() {
    logToConsole("Membuat arsip diagnostik lengkap...", "warn");
    const res = await execCmd(`${LYNXD} system log-export`);
    if (res.stdout && !res.stdout.startsWith("Error")) {
        const path = res.stdout.trim();
        logToConsole(`Laporan berhasil dibuat: ${path}`, 'success');
        alert(`Laporan Diagnostik Berhasil Diekspor!\nLokasi file: ${path}`);
    } else {
        logToConsole(`Export gagal: ${res.stderr || res.stdout}`, 'error');
    }
}

async function runMaintenance() {
    logToConsole("Menjalankan SQLite VACUUM & Storage TRIM...", "warn");
    const res = await execCmd(`${LYNXD} maintenance run`);
    const output = res.stdout || res.stderr || "Pemeliharaan & optimasi storage selesai.";
    logToConsole(output, res.errno === 0 ? "success" : "error");
}

async function runCCleaner() {
    logToConsole("Running CCleaner & Memory Compaction...", "warn");
    const res = await execCmd(`${LYNXD} memory clean`);
    logToConsole(res.stdout || "Memory compaction complete.", "success");
}

// 6. Lynx Kernel Manager - High-Frequency Telemetry & Dynamic CPU Clusters
let clusterMaxFreqs = {};
let clusterTopology = [];

let telemetryPollTick = 0;

async function pollTelemetry() {
    telemetryPollTick++;
    if (telemetryPollTick % 4 === 0) {
        loadClusterTopology(false);
    }
    try {
        const res = await execCmd(`cat /dev/lynxd_telemetry.json 2>/dev/null || ${LYNXD} telemetry`);
        if (res.errno === 0 && res.stdout) {
            const data = JSON.parse(res.stdout);
            
            // 1. GPU Telemetry
            const gpuFreqEl = document.getElementById('tel-gpu-freq');
            if (gpuFreqEl && data.gpu_freq !== undefined) gpuFreqEl.textContent = data.gpu_freq;
            const clkEl = document.getElementById('web-gpu-clock');
            if (clkEl && data.gpu_freq !== undefined) clkEl.textContent = `${data.gpu_freq} MHz`;

            const gpuBusyEl = document.getElementById('tel-gpu-busy');
            if (gpuBusyEl && data.gpu_busy !== undefined) gpuBusyEl.textContent = data.gpu_busy;
            const loadEl = document.getElementById('web-gpu-load');
            if (loadEl && data.gpu_busy !== undefined) loadEl.textContent = `${data.gpu_busy}%`;

            const vendorEl = document.getElementById('web-gpu-vendor');
            if (vendorEl && data.gpu_vendor) vendorEl.textContent = data.gpu_vendor;

            if (data.gpu_boost !== undefined) {
                const boostLvl = parseInt(data.gpu_boost, 10);
                [0, 1, 2].forEach(b => {
                    const btn = document.getElementById(`web-btn-gpu-${b}`);
                    if (btn) {
                        if (b === boostLvl) btn.classList.add('active');
                        else btn.classList.remove('active');
                    }
                });
            }

            const govSel = document.getElementById('web-gpu-gov-select');
            if (govSel && data.gpu_avail_govs && data.gpu_avail_govs.length > 0) {
                if (govSel.options.length <= 1 || govSel.dataset.loaded !== 'true') {
                    govSel.innerHTML = '';
                    data.gpu_avail_govs.forEach(g => {
                        const opt = document.createElement('option');
                        opt.value = g;
                        opt.textContent = g;
                        if (g === data.gpu_cur_gov) opt.selected = true;
                        govSel.appendChild(opt);
                    });
                    govSel.dataset.loaded = 'true';
                } else if (document.activeElement !== govSel && data.gpu_cur_gov) {
                    govSel.value = data.gpu_cur_gov;
                }
            }
            
            // 2. Battery & Temp
            const tempEl = document.getElementById('tel-temp');
            if (tempEl && data.temp !== undefined) tempEl.textContent = data.temp;
            const battLvlEl = document.getElementById('tel-batt-lvl');
            if (battLvlEl && data.batt_level !== undefined) battLvlEl.textContent = data.batt_level;
            const battCurEl = document.getElementById('tel-batt-cur');
            if (battCurEl && data.batt_current_ma !== undefined) {
                const prefix = data.batt_current_ma > 0 ? "+" : "";
                battCurEl.textContent = prefix + data.batt_current_ma;
            }

            // 3. RAM & zRAM
            const ramUsedEl = document.getElementById('tel-ram-used');
            if (ramUsedEl && data.ram_used_mb !== undefined) ramUsedEl.textContent = data.ram_used_mb;
            const ramTotEl = document.getElementById('tel-ram-total');
            if (ramTotEl && data.ram_total_mb !== undefined) ramTotEl.textContent = data.ram_total_mb;

            // 4. CPU Cores Grid
            const coresGrid = document.getElementById('cores-grid');
            if (coresGrid && Array.isArray(data.cpu)) {
                if (coresGrid.children.length !== data.cpu.length) {
                    coresGrid.innerHTML = '';
                    data.cpu.forEach((_, idx) => {
                        const div = document.createElement('div');
                        div.className = 'core-meter';
                        div.id = `core-meter-${idx}`;
                        div.innerHTML = `
                            <div class="core-label">CPU ${idx}</div>
                            <div class="core-freq" id="core-freq-${idx}">-- MHz</div>
                            <div class="core-bar-track">
                                <div class="core-bar-fill" id="core-bar-${idx}"></div>
                            </div>
                        `;
                        coresGrid.appendChild(div);
                    });
                }

                data.cpu.forEach((rawFreq, idx) => {
                    const freqEl = document.getElementById(`core-freq-${idx}`);
                    const barEl = document.getElementById(`core-bar-${idx}`);
                    const freq = parseInt(rawFreq, 10);
                    if (freq > 0) {
                        const mhz = Math.round(freq / 1000);
                        if (freqEl) freqEl.textContent = `${mhz} MHz`;
                        const maxF = clusterMaxFreqs[idx] || 2500000;
                        const pct = Math.min(100, Math.max(5, Math.round((freq / maxF) * 100)));
                        if (barEl) barEl.style.width = `${pct}%`;
                    } else {
                        if (freqEl) freqEl.textContent = 'Offline';
                        if (barEl) barEl.style.width = '0%';
                    }
                });
            }

            // 5. Thermal Zones Grid (Unified)
            if (Array.isArray(data.thermal_zones)) {
                renderThermalZones(data.thermal_zones);
            }

            // 6. Engine / Daemon Status Badge & Runtime Modifier
            if (data.daemon) {
                const engineBadge = document.getElementById('engine-status-badge');
                if (engineBadge) {
                    if (data.daemon.running) {
                        engineBadge.innerHTML = `<span style="color:var(--accent-cyan);">● lynxd (${data.daemon.pid || ''})</span>`;
                    } else {
                        engineBadge.innerHTML = `<span style="color:var(--text-secondary);">○ lynxd</span>`;
                    }
                }
            }
            if (data.runtime) {
                const modTag = document.getElementById('runtime-modifier-tag');
                if (modTag) {
                    if (data.runtime.modifier && data.runtime.modifier !== "None") {
                        modTag.textContent = `${data.runtime.modifier}`;
                        modTag.title = data.runtime.modifier_desc || '';
                        modTag.style.display = 'block';
                    } else {
                        modTag.style.display = 'none';
                    }
                }
            }
        }
    } catch (e) {
        // Silently handle parse errors during polling
    }
}

async function pollNativeStatus() {
    try {
        const res = await execCmd(`cat /dev/lynxd_status.json 2>/dev/null || ${LYNXD} status --json`);
        if (res.errno === 0 && res.stdout) {
            const status = JSON.parse(res.stdout);
            const engineBadge = document.getElementById('engine-status-badge');
            if (engineBadge) {
                if (status.daemon && status.daemon.running) {
                    engineBadge.innerHTML = `<span style="color:var(--accent-cyan);">● lynxd (${status.daemon.pid})</span>`;
                } else {
                    engineBadge.innerHTML = `<span style="color:var(--text-secondary);">○ lynxd</span>`;
                }
            }
            const modTag = document.getElementById('runtime-modifier-tag');
            if (modTag && status.runtime) {
                if (status.runtime.modifier && status.runtime.modifier !== "None") {
                    modTag.textContent = `${status.runtime.modifier}`;
                    modTag.title = status.runtime.modifier_desc || '';
                    modTag.style.display = 'block';
                } else {
                    modTag.style.display = 'none';
                }
            }
        }
    } catch (e) {
        // Silently handle parse errors
    }
}

async function loadClusterTopology(forceRebuild = false) {
    const container = document.getElementById('clusters-container');
    if (!container) return;
    try {
        const res = await execCmd(`${LYNXD} cluster topology`);
        if (res.errno === 0 && res.stdout) {
            const data = JSON.parse(res.stdout);
            if (data.clusters && data.clusters.length > 0) {
                clusterTopology = data.clusters;

                // Cache max freqs for core meter bar calculations
                data.clusters.forEach(c => {
                    if (c.cpus) {
                        const parts = c.cpus.split('-');
                        const start = parseInt(parts[0], 10);
                        const end = parts.length > 1 ? parseInt(parts[1], 10) : start;
                        for (let i = start; i <= end; i++) {
                            clusterMaxFreqs[i] = c.cur_max || 2500000;
                        }
                    }
                });

                // In-place 2-way update if already rendered and not forced
                if (container.children.length > 0 && !forceRebuild) {
                    data.clusters.forEach(c => {
                        const item = document.getElementById(`cluster-item-${c.id}`);
                        if (!item) return;
                        const lockBtn = item.querySelector('.btn-lock-cluster');
                        if (lockBtn) {
                            lockBtn.innerHTML = c.is_locked 
                                ? '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg>'
                                : '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm0 12H6V10h12v10z"/></svg>';
                            lockBtn.style.color = c.is_locked ? '#00e676' : 'var(--text-secondary)';
                            lockBtn.style.background = c.is_locked ? 'rgba(0,230,118,0.2)' : 'rgba(255,255,255,0.08)';
                            lockBtn.style.borderColor = c.is_locked ? '#00e676' : 'var(--border-subtle)';
                            lockBtn.onclick = () => toggleClusterLock(c.id, !c.is_locked);
                        }
                        const minSelect = item.querySelector('.select-min-freq');
                        if (minSelect && document.activeElement !== minSelect) {
                            minSelect.value = c.cur_min;
                        }
                        const maxSelect = item.querySelector('.select-max-freq');
                        if (maxSelect && document.activeElement !== maxSelect) {
                            maxSelect.value = c.cur_max;
                        }
                        const govSelect = item.querySelector('.select-gov-name');
                        if (govSelect && document.activeElement !== govSelect) {
                            govSelect.value = c.cur_gov;
                        }
                    });
                    return;
                }

                container.innerHTML = '';
                data.clusters.forEach(c => {
                    const item = document.createElement('div');
                    item.className = 'cluster-item';
                    item.id = `cluster-item-${c.id}`;

                    // Governor dropdown options
                    const govOptions = (c.avail_govs || []).map(g => 
                        `<option value="${g}" ${g === c.cur_gov ? 'selected' : ''}>${g}</option>`
                    ).join('');

                    // Frequency options for min & max dropdowns
                    const minOptions = (c.avail_freqs || []).map(f => {
                        const mhz = Math.round(f / 1000);
                        return `<option value="${f}" ${f === c.cur_min ? 'selected' : ''}>${mhz} MHz</option>`;
                    }).join('');

                    const maxOptions = (c.avail_freqs || []).map(f => {
                        const mhz = Math.round(f / 1000);
                        return `<option value="${f}" ${f === c.cur_max ? 'selected' : ''}>${mhz} MHz</option>`;
                    }).join('');

                    const lockSvg = c.is_locked 
                        ? '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg>'
                        : '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm0 12H6V10h12v10z"/></svg>';

                    item.innerHTML = `
                        <div class="cluster-header">
                            <div>
                                <span class="cluster-name">Policy ${c.id}: ${c.role}</span>
                                <div style="font-size: 11px; color: var(--text-secondary); margin-top:2px;">Cores: ${c.cpus}</div>
                            </div>
                        </div>
                        <div style="display: flex; gap: 8px; align-items: flex-end; margin-top: 10px;">
                            <div style="flex: 1;">
                                <label style="font-size:11px; color:var(--text-secondary); font-weight:600;">Batas Bawah</label>
                                <select class="select-gov select-min-freq" onchange="setClusterFreq(${c.id}, this.value, null)">
                                    ${minOptions}
                                </select>
                            </div>
                            <div style="flex: 1;">
                                <label style="font-size:11px; color:var(--text-secondary); font-weight:600;">Batas Puncak</label>
                                <select class="select-gov select-max-freq" onchange="setClusterFreq(${c.id}, null, this.value)">
                                    ${maxOptions}
                                </select>
                            </div>
                            <button class="btn-lock-cluster" 
                                style="cursor:pointer; width:44px; height:44px; display:flex; align-items:center; justify-content:center; background:${c.is_locked ? 'rgba(0,230,118,0.2)' : 'rgba(255,255,255,0.08)'}; color:${c.is_locked ? '#00e676' : 'var(--text-secondary)'}; border: 1px solid ${c.is_locked ? '#00e676' : 'var(--border-subtle)'}; border-radius:8px; font-size:16px;"
                                title="${c.is_locked ? 'Terkunci (Proteksi Aktif)' : 'Buka Kunci'}"
                                onclick="toggleClusterLock(${c.id}, ${!c.is_locked})">
                                ${lockSvg}
                            </button>
                        </div>
                        <div style="margin-top: 10px;">
                            <label style="font-size:11px; color:var(--text-secondary); font-weight:600;">Governor</label>
                            <select class="select-gov select-gov-name" onchange="setClusterGov(${c.id}, this.value)">
                                ${govOptions}
                            </select>
                        </div>
                    `;
                    container.appendChild(item);
                });
            }
        }
    } catch (e) {
        console.warn("Failed loading cluster topology", e);
    }
}

async function toggleClusterLock(policyId, shouldLock) {
    const cluster = clusterTopology.find(c => c.id === policyId);
    const min = cluster ? cluster.cur_min : 0;
    const max = cluster ? cluster.cur_max : 0;
    const cmd = shouldLock 
        ? `${LYNXD} cluster lock ${policyId} ${min} ${max}`
        : `${LYNXD} cluster unlock ${policyId}`;
    const res = await execCmd(cmd);
    if (res.errno === 0) {
        logToConsole(`Cluster ${policyId} ${shouldLock ? 'locked' : 'unlocked'}.`, 'success');
        await loadClusterTopology(true);
    } else {
        logToConsole(`Failed to change cluster lock: ${res.stderr}`, 'error');
    }
}

async function setClusterFreq(policyId, minFreq, maxFreq) {
    const cluster = clusterTopology.find(c => c.id === policyId);
    const targetMin = minFreq !== null ? minFreq : (cluster ? cluster.cur_min : 0);
    const targetMax = maxFreq !== null ? maxFreq : (cluster ? cluster.cur_max : 0);

    logToConsole(`Setting Policy ${policyId} Freq: Min=${Math.round(targetMin/1000)}MHz Max=${Math.round(targetMax/1000)}MHz...`);
    const cmd = `${LYNXD} cluster set_freq ${policyId} ${targetMin} ${targetMax}`;
    const res = await execCmd(cmd);
    if (res.errno === 0) {
        logToConsole(`Policy ${policyId} frequency applied.`, 'success');
        if (cluster) {
            if (minFreq !== null) cluster.cur_min = parseInt(minFreq, 10);
            if (maxFreq !== null) cluster.cur_max = parseInt(maxFreq, 10);
        }
        await loadClusterTopology(false);
    } else {
        logToConsole(`Failed setting policy ${policyId} frequency: ${res.stderr}`, 'error');
    }
}

async function setClusterGov(policyId, gov) {
    logToConsole(`Setting Policy ${policyId} Governor: ${gov}...`);
    const cmd = `${LYNXD} cluster set_gov ${policyId} ${gov}`;
    const res = await execCmd(cmd);
    if (res.errno === 0) {
        logToConsole(`Policy ${policyId} governor set to ${gov}.`, 'success');
        await loadClusterTopology(false);
    } else {
        logToConsole(`Failed setting governor: ${res.stderr}`, 'error');
    }
}

// 7. Navigation Tab Switching (4-Tab Material 3 Mirror)
function switchTab(tabName) {
    document.querySelectorAll('.tab-pane').forEach(el => el.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));

    const targetTab = document.getElementById(`tab-${tabName}`);
    const targetNav = document.getElementById(`nav-${tabName}`);

    if (targetTab) targetTab.classList.add('active');
    if (targetNav) targetNav.classList.add('active');

    window.scrollTo({ top: 0, behavior: 'smooth' });

    if (tabName === 'tools') {
        loadBackups();
        pollWebWakelocks();
    } else if (tabName === 'engine') {
        loadIoDevices();
    }
}

// 8. AnyKernel3 Flasher & Boot Partition Backup/Restore
async function flashAnyKernel() {
    const input = document.getElementById('flasher-zip-path');
    if (!input || !input.value.trim()) {
        alert("Silakan masukkan path lengkap ke berkas ZIP kernel Anda (misal: /sdcard/Download/kernel.zip).");
        return;
    }
    const zipPath = input.value.trim();
    const confirmFlash = confirm(`PERINGATAN FLASHING KERNEL:\nApakah Anda yakin ingin mem-flash kernel dari:\n${zipPath}?\n\nBoot image saat ini akan otomatis dicadangkan terlebih dahulu.`);
    if (!confirmFlash) return;

    logToConsole(`Memulai flashing AnyKernel3: ${zipPath}...`, 'warn');
    const cmd = `${LYNXD} flasher flash "${zipPath}"`;
    const res = await execCmd(cmd);
    const output = res.stdout || res.stderr || "";
    logToConsole(output, res.errno === 0 ? 'success' : 'error');

    if (res.errno === 0) {
        alert("Flashing kernel selesai dengan sukses!\nSilakan reboot perangkat untuk menerapkan kernel baru.");
    } else {
        alert("Flashing kernel gagal. Periksa konsol log untuk detail error.");
    }
    loadBackups();
}

async function backupBootPartition() {
    logToConsole("Mencadangkan partisi boot/init_boot...", "warn");
    const res = await execCmd(`${LYNXD} flasher backup`);
    logToConsole(res.stdout || res.stderr || "Proses backup selesai.", res.errno === 0 ? 'success' : 'error');
    loadBackups();
}

async function loadBackups() {
    const container = document.getElementById('backup-list');
    if (!container) return;

    try {
        const res = await execCmd(`${LYNXD} flasher list_backups`);
        if (res.errno === 0 && res.stdout) {
            const data = JSON.parse(res.stdout);
            if (data.backups && data.backups.length > 0) {
                container.innerHTML = '';
                data.backups.forEach(b => {
                    const mb = (b.size / (1024 * 1024)).toFixed(1);
                    const item = document.createElement('div');
                    item.className = 'backup-item';
                    item.innerHTML = `
                        <div>
                            <div class="backup-name">${b.name}</div>
                            <div class="backup-sub">${mb} MB • ${b.date || ''}</div>
                        </div>
                        <button class="btn-small" onclick="restoreBoot('${b.path}')">Restore</button>
                    `;
                    container.appendChild(item);
                });
            } else {
                container.innerHTML = '<div style="font-size: 12px; color: var(--text-muted);">Belum ada backup partisi tersimpan.</div>';
            }
        }
    } catch (e) {
        container.innerHTML = '<div style="font-size: 12px; color: var(--text-muted);">Gagal memuat daftar backup.</div>';
    }
}

async function restoreBoot(path) {
    const confirmRestore = confirm(`PERINGATAN RESTORE BOOT:\nApakah Anda yakin ingin memulihkan partisi dari backup:\n${path}?`);
    if (!confirmRestore) return;

    logToConsole(`Memulihkan partisi dari ${path}...`, 'warn');
    const res = await execCmd(`${LYNXD} flasher restore "${path}"`);
    logToConsole(res.stdout || res.stderr || "Restore selesai.", res.errno === 0 ? 'success' : 'error');
}

async function openNativeApp() {
    logToConsole("Meluncurkan aplikasi Lynx Companion...", "info");
    const res = await execCmd("am start -n com.noir.lynx.debug/com.noir.lynx.ui.MainActivity 2>/dev/null || am start -n com.noir.lynx/.ui.MainActivity 2>/dev/null");
    if (res.errno !== 0) {
        alert("Aplikasi Lynx Companion belum terpasang atau tidak dapat diluncurkan.");
    }
}

// ----------------------------------------------------------------
//  Dynamic Refresh Rate, VM Cache, SELinux, and Applist
// ----------------------------------------------------------------

async function setRefreshRate(hz) {
    logToConsole(`Mengubah refresh rate ke ${hz} Hz...`);
    const res = await execCmd(`${LYNXD} display set-refresh-rate ${hz}`);
    logToConsole(res.stdout || "Refresh rate disetel", 'success');
    document.querySelectorAll('#refresh-rate-container button').forEach(b => b.classList.remove('active'));
    const btn = document.getElementById(`rr-${hz}`);
    if (btn) btn.classList.add('active');
}

async function dropCaches() {
    logToConsole("Membebaskan Cache RAM (Drop Caches)...", "warn");
    const res = await execCmd(`${LYNXD} memory clean`);
    logToConsole(res.stdout || "Cache RAM dibebaskan.", 'success');
}

async function loadWebVmTunables() {
    try {
        const res = await execCmd(`${LYNXD} memory vm-info`);
        if (res.errno === 0 && res.stdout) {
            const data = JSON.parse(res.stdout);
            const setSlider = (id, labelId, val, suffix = '') => {
                const slider = document.getElementById(id);
                const label = document.getElementById(labelId);
                if (slider) slider.value = val;
                if (label) label.textContent = val + suffix;
            };
            if (data.dirty_ratio !== undefined) setSlider('dirty-ratio-slider', 'dirty-ratio-val', data.dirty_ratio, '%');
            if (data.dirty_background_ratio !== undefined) setSlider('dirty-bg-ratio-slider', 'dirty-bg-ratio-val', data.dirty_background_ratio, '%');
            if (data.vfs_cache_pressure !== undefined) setSlider('vfs-pressure-slider', 'vfs-pressure-val', data.vfs_cache_pressure);
            if (data.swappiness !== undefined) setSlider('swappiness-slider', 'swappiness-val', data.swappiness);
            if (data.dirty_expire_centisecs !== undefined) setSlider('dirty-expire-slider', 'dirty-expire-val', (data.dirty_expire_centisecs / 100), 's');
            if (data.dirty_writeback_centisecs !== undefined) setSlider('dirty-wb-slider', 'dirty-wb-val', (data.dirty_writeback_centisecs / 100), 's');
            if (data.stat_interval !== undefined) setSlider('stat-interval-slider', 'stat-interval-val', data.stat_interval, 's');
        }
    } catch (e) {
        console.error("loadWebVmTunables error:", e);
    }
}

async function setWebVmTunable(param, val) {
    const res = await execCmd(`${LYNXD} memory vm-set ${param} ${val}`);
    logToConsole(res.stdout || `VM ${param} disetel ke ${val}`, 'info');
}

async function applyWebVmPreset(preset) {
    logToConsole(`Menerapkan preset VM '${preset}'...`);
    const res = await execCmd(`${LYNXD} memory vm-preset ${preset}`);
    logToConsole(res.stdout || `Preset VM '${preset}' diterapkan`, 'success');
    await loadWebVmTunables();
    document.querySelectorAll('#btn-vm-gaming, #btn-vm-balanced, #btn-vm-battery').forEach(b => b.classList.remove('active'));
    const btn = document.getElementById(`btn-vm-${preset}`);
    if (btn) btn.classList.add('active');
}

async function loadTcpCongestion() {
    const container = document.getElementById('tcp-container');
    if (!container) return;
    try {
        const res = await execCmd(`${LYNXD} network info`);
        if (res.errno === 0 && res.stdout) {
            const data = JSON.parse(res.stdout);
            const cur = (data.current || "").trim();
            const avail = (data.available || "cubic reno").trim().split(/\s+/).filter(Boolean);
            container.innerHTML = '';
            avail.forEach(alg => {
                const btn = document.createElement('button');
                btn.className = `btn btn-primary ${alg === cur ? 'active' : ''}`;
                btn.id = `tcp-${alg}`;
                btn.style.cssText = 'flex: 1; min-width: 70px;';
                btn.textContent = alg.toUpperCase();
                btn.onclick = () => setTcpCongestion(alg);
                container.appendChild(btn);
            });
        }
    } catch (e) {
        console.error("loadTcpCongestion error:", e);
    }
}

async function setTcpCongestion(alg) {
    logToConsole(`Menyetel TCP Congestion Control ke ${alg}...`);
    const res = await execCmd(`${LYNXD} network set_algo ${alg}`);
    logToConsole(res.stdout || `TCP disetel ke ${alg}`, 'success');
    await updateStateKey('network.tcp_congestion', alg, true);
    document.querySelectorAll('#tcp-container button').forEach(b => b.classList.remove('active'));
    const btn = document.getElementById(`tcp-${alg}`);
    if (btn) btn.classList.add('active');
}

async function loadWebBoefflaAndDoze() {
    try {
        const bRes = await execCmd(`${LYNXD} system boeffla-status`);
        const badge = document.getElementById('boeffla-status-badge');
        if (badge && bRes.errno === 0 && bRes.stdout) {
            const bData = JSON.parse(bRes.stdout);
            if (bData.supported) {
                badge.textContent = "DRIVER: ACTIVE";
                badge.style.color = "var(--accent-green)";
                badge.style.borderColor = "var(--accent-green)";
            } else {
                badge.textContent = "DRIVER: UNSUPPORTED";
                badge.style.color = "var(--text-muted)";
                badge.style.borderColor = "var(--border-glass)";
            }
        }

        const dRes = await execCmd(`${LYNXD} system doze-info`);
        const toggle = document.getElementById('doze-toggle');
        if (toggle && dRes.errno === 0 && dRes.stdout) {
            const dData = JSON.parse(dRes.stdout);
            toggle.checked = (dData.state === "IDLE");
        }
    } catch (e) {
        console.error("loadWebBoefflaAndDoze error:", e);
    }
}

async function toggleWebAggressiveDoze(enabled) {
    const p = enabled ? '1' : '0';
    logToConsole(`Menyetel Aggressive Doze ke ${enabled ? 'Aktif' : 'Nonaktif'}...`);
    const res = await execCmd(`${LYNXD} system set-doze ${p}`);
    logToConsole(res.stdout || `Aggressive Doze diperbarui`, 'success');
}

async function setSelinux(val) {
    const res = await execCmd(`${LYNXD} system set-selinux ${val}`);
    logToConsole(res.stdout || `SELinux mode updated`, 'info');
    const label = document.getElementById('selinux-mode-label');
    if (label) {
        label.textContent = val === '1' ? 'Enforcing' : 'Permissive';
        label.style.color = val === '1' ? 'var(--accent-green)' : 'var(--accent-red)';
    }
}

async function setPrintkSilent(silent) {
    const param = silent ? 'silent' : 'verbose';
    const res = await execCmd(`${LYNXD} system set-printk ${param}`);
    logToConsole(res.stdout || `Printk set to ${param}`, 'info');
}

async function loadApplist() {
    try {
        const res = await execCmd(`${LYNXD} applist list`);
        const container = document.getElementById('applist-chips');
        if (!container) return;
        container.innerHTML = '';
        if (res.stdout) {
            const apps = res.stdout.trim().split('\n').filter(Boolean);
            apps.slice(0, 15).forEach(pkg => {
                const chip = document.createElement('div');
                chip.style.cssText = 'background:var(--bg-elevated); border:1px solid var(--border-glass); border-radius:8px; padding:4px 8px; font-size:11px; display:flex; align-items:center; gap:6px; color:var(--accent-green);';
                const label = pkg.split('.').pop() || pkg;
                chip.innerHTML = `<span>${label}</span><span style="cursor:pointer; color:var(--text-secondary); font-size:14px; line-height:1;" onclick="removeAppFromWhitelist('${pkg}')">&times;</span>`;
                container.appendChild(chip);
            });
            if (apps.length > 15) {
                const more = document.createElement('div');
                more.style.cssText = 'font-size:10px; color:var(--text-secondary); align-self:center;';
                more.textContent = `+${apps.length - 15} game lainnya`;
                container.appendChild(more);
            }
        }
    } catch (e) {}
}

async function addAppToWhitelist() {
    const input = document.getElementById('applist-input');
    if (!input || !input.value.trim()) return;
    const pkg = input.value.trim();
    await execCmd(`${LYNXD} applist add "${pkg}"`);
    input.value = '';
    logToConsole(`Menambahkan ${pkg} ke whitelist game`, 'success');
    loadApplist();
}

async function removeAppFromWhitelist(pkg) {
    await execCmd(`${LYNXD} applist remove "${pkg}"`);
    logToConsole(`Menghapus ${pkg} dari whitelist game`, 'info');
    loadApplist();
}

// ----------------------------------------------------------------
// Universal GPU, Thermal Zones, Wakelock, and I/O Scheduler
// ----------------------------------------------------------------

async function pollGpuInfo() {
    try {
        const res = await execCmd(`${LYNXD} gpu info`);
        if (res.errno === 0 && res.stdout) {
            const data = JSON.parse(res.stdout);
            const clkEl = document.getElementById('web-gpu-clock');
            const loadEl = document.getElementById('web-gpu-load');
            const vendorEl = document.getElementById('web-gpu-vendor');
            const curMhz = data.cur_mhz !== undefined ? data.cur_mhz : (data.cur_freq !== undefined ? data.cur_freq : '--');
            const loadVal = data.load !== undefined ? data.load : (data.busy !== undefined ? data.busy : 0);
            if (clkEl) clkEl.textContent = `${curMhz} MHz`;
            if (loadEl) loadEl.textContent = `${loadVal}%`;
            if (vendorEl && data.vendor) vendorEl.textContent = data.vendor;

            const boostLvl = data.boost !== undefined ? parseInt(data.boost, 10) : 0;
            [0, 1, 2].forEach(b => {
                const btn = document.getElementById(`web-btn-gpu-${b}`);
                if (btn) {
                    if (b === boostLvl) {
                        btn.classList.add('active');
                    } else {
                        btn.classList.remove('active');
                    }
                }
            });

            // Populate GPU governor selector
            const govSel = document.getElementById('web-gpu-gov-select');
            if (govSel && data.avail_govs && data.avail_govs.length > 0) {
                if (govSel.options.length <= 1 || govSel.dataset.loaded !== 'true') {
                    govSel.innerHTML = '';
                    data.avail_govs.forEach(g => {
                        const opt = document.createElement('option');
                        opt.value = g;
                        opt.textContent = g;
                        if (g === data.cur_gov) opt.selected = true;
                        govSel.appendChild(opt);
                    });
                    govSel.dataset.loaded = 'true';
                } else if (document.activeElement !== govSel && data.cur_gov) {
                    govSel.value = data.cur_gov;
                }
            }
        }
    } catch (e) {}
}

async function setWebGpuGov(gov) {
    if (!gov) return;
    logToConsole(`Menyetel governor GPU ke ${gov}...`);
    const res = await execCmd(`${LYNXD} gpu set_gov ${gov}`);
    logToConsole(res.stdout || `Governor GPU ${gov} diterapkan`, 'success');
}

async function setWebGpuBoost(lvl) {
    logToConsole(`Menyetel tingkat GPU Boost ke Level ${lvl}...`);
    const res = await execCmd(`${LYNXD} gpu set_boost ${lvl}`);
    logToConsole(res.stdout || `GPU Boost level ${lvl} diterapkan`, 'success');
    [0, 1, 2].forEach(b => {
        const btn = document.getElementById(`web-btn-gpu-${b}`);
        if (btn) {
            if (b === lvl) btn.classList.add('active');
            else btn.classList.remove('active');
        }
    });
}

async function setWebGovPreset(preset) {
    logToConsole(`Menerapkan preset governor schedutil: ${preset}...`);
    const res = await execCmd(`${LYNXD} cluster set-schedutil-preset ${preset}`);
    logToConsole(res.stdout || `Governor preset ${preset} diterapkan`, 'success');
    ['resp', 'bal', 'power'].forEach(p => {
        const btn = document.getElementById(`web-btn-gov-${p}`);
        if (btn) btn.classList.remove('active');
    });
    const activeMap = { 'responsive': 'resp', 'balanced': 'bal', 'powersave': 'power' };
    const activeBtn = document.getElementById(`web-btn-gov-${activeMap[preset] || 'bal'}`);
    if (activeBtn) activeBtn.classList.add('active');
}

function renderThermalZones(zones) {
    const container = document.getElementById('thermal-zones-grid');
    if (!container || !Array.isArray(zones) || zones.length === 0) return;
    container.innerHTML = '';
    zones.forEach(z => {
        const type = z.type || 'unknown';
        const temp = Math.round(z.temp_c !== undefined ? z.temp_c : (z.temp > 1000 ? z.temp / 1000 : z.temp));
        let color = 'var(--accent-green)';
        if (temp >= 65) color = 'var(--accent-red)';
        else if (temp >= 55) color = 'var(--accent-yellow)';
        else if (temp >= 45) color = 'var(--accent-cyan)';

        const item = document.createElement('div');
        item.className = 'thermal-pill';
        item.innerHTML = `
            <span class="thermal-type">${type.length > 14 ? type.substring(0, 12) + '..' : type}</span>
            <span class="thermal-val" style="color: ${color};">${temp}°C</span>
        `;
        container.appendChild(item);
    });
}

async function pollThermalZones() {
    try {
        const res = await execCmd(`${LYNXD} thermal status`);
        if (res.errno === 0 && res.stdout) {
            const raw = res.stdout.trim();
            if (raw.startsWith('{')) {
                const data = JSON.parse(raw);
                if (Array.isArray(data.thermal_zones)) {
                    renderThermalZones(data.thermal_zones);
                    return;
                }
            }
            const container = document.getElementById('thermal-zones-grid');
            if (!container) return;
            const lines = raw.split('\n').filter(Boolean);
            if (lines.length > 0) {
                container.innerHTML = '';
                lines.forEach(line => {
                    const parts = line.split('|');
                    if (parts.length >= 2) {
                        const type = parts[0].trim();
                        const temp = parseInt(parts[1].trim(), 10);
                        let color = 'var(--accent-green)';
                        if (temp >= 65) color = 'var(--accent-red)';
                        else if (temp >= 55) color = 'var(--accent-yellow)';
                        else if (temp >= 45) color = 'var(--accent-cyan)';

                        const item = document.createElement('div');
                        item.className = 'thermal-pill';
                        item.innerHTML = `
                            <span class="thermal-type">${type.length > 14 ? type.substring(0, 12) + '..' : type}</span>
                            <span class="thermal-val" style="color: ${color};">${temp}°C</span>
                        `;
                        container.appendChild(item);
                    }
                });
            }
        }
    } catch (e) {}
}

async function pollWebWakelocks() {
    const list = document.getElementById('wakelock-list');
    if (!list) return;
    list.innerHTML = '<div style="font-size: 11px; color: var(--text-muted);">Memindai sensor wakelock kernel...</div>';
    try {
        const res = await execCmd(`${LYNXD} system top-wakelocks`);
        if (res.errno === 0 && res.stdout) {
            const lines = res.stdout.trim().split('\n').filter(Boolean);
            if (lines.length > 0) {
                list.innerHTML = '';
                lines.forEach(line => {
                    const parts = line.split('|');
                    if (parts.length >= 2) {
                        const name = parts[0].trim();
                        const count = parts[1].trim();
                        const ms = parts[2] ? parts[2].trim() : '0';
                        const sec = Math.round(parseInt(ms, 10) / 1000);
                        const item = document.createElement('div');
                        item.className = 'wakelock-item';
                        item.innerHTML = `
                            <span class="wakelock-name">${name}</span>
                            <span class="wakelock-stat">${count}x wake • ${sec}s</span>
                        `;
                        list.appendChild(item);
                    }
                });
            } else {
                list.innerHTML = '<div style="font-size: 11px; color: var(--accent-green);">Tidak ada wakelock aktif saat ini (Deep Sleep optimal).</div>';
            }
        } else {
            list.innerHTML = '<div style="font-size: 11px; color: var(--text-muted);">Tidak ada data wakelock kernel.</div>';
        }
    } catch (e) {
        list.innerHTML = '<div style="font-size: 11px; color: var(--accent-red);">Gagal membaca wakelock.</div>';
    }
}

async function loadIoDevices() {
    const container = document.getElementById('io-devices-container');
    if (!container) return;
    try {
        const res = await execCmd(`${LYNXD} io info`);
        if (res.errno === 0 && res.stdout) {
            const data = JSON.parse(res.stdout);
            if (data.devices && data.devices.length > 0) {
                container.innerHTML = '';
                data.devices.forEach(d => {
                    const scheds = d.avail.trim().split(/\s+/).filter(Boolean);
                    const chipsHtml = scheds.map(s => {
                        const isActive = s === d.cur;
                        return `<button class="io-chip ${isActive ? 'active' : ''}" onclick="setWebIoSched('${d.device}', '${s}')">${s}</button>`;
                    }).join('');

                    const card = document.createElement('div');
                    card.className = 'io-device-card';
                    card.innerHTML = `
                        <div class="io-device-header">
                            <span class="io-device-name">/dev/block/${d.device}</span>
                            <span class="io-device-ra">Read-Ahead: ${d.ra_kb} KB</span>
                        </div>
                        <div class="io-sched-chips">
                            ${chipsHtml}
                        </div>
                    `;
                    container.appendChild(card);
                });
            } else {
                container.innerHTML = '<div style="font-size: 11px; color: var(--text-muted);">Tidak ditemukan block device storage.</div>';
            }
        }
    } catch (e) {
        container.innerHTML = '<div style="font-size: 11px; color: var(--text-muted);">Gagal memuat scheduler storage.</div>';
    }
}

async function setWebIoSched(dev, sched) {
    logToConsole(`Menyetel scheduler ${dev} ke ${sched}...`);
    const res = await execCmd(`${LYNXD} io set_scheduler "${dev}" "${sched}"`);
    logToConsole(res.stdout || `Scheduler ${sched} diterapkan pada ${dev}`, 'success');
    loadIoDevices();
}

// ----------------------------------------------------------------
//  Deep Kernel & System Tunables (Auto-Discovery Engine)
// ----------------------------------------------------------------

let discoveredTunables = [];
let activeTunableFilter = 'all';

async function runDeepScan() {
    const btn = document.getElementById('btn-deep-scan');
    const container = document.getElementById('deep-tunables-container');
    if (!container) return;

    if (btn) {
        btn.textContent = 'Memindai Kernel & Hardware...';
        btn.disabled = true;
    }
    container.innerHTML = '<div style="font-size:12px; color:var(--accent-cyan); text-align:center; padding:20px 0;">Sedang memindai subsistem kernel, vendor OEM, dan sysfs/procfs...</div>';

    try {
        const res = await execCmd(`${LYNXD} inspect scan`);
        if (res.errno === 0 && res.stdout) {
            try {
                discoveredTunables = JSON.parse(res.stdout);
            } catch (err) {
                discoveredTunables = [];
            }

            if (Array.isArray(discoveredTunables) && discoveredTunables.length > 0) {
                logToConsole(`Deep Scan: Menemukan ${discoveredTunables.length} parameter hardware/kernel yang dapat diatur!`, 'success');
                renderDeepTunables();
            } else {
                container.innerHTML = '<div style="font-size:12px; color:var(--text-muted); text-align:center; padding:16px 0;">Tidak ditemukan node sysfs aktif yang cocok dengan profil sistem ini.</div>';
            }
        } else {
            container.innerHTML = '<div style="font-size:12px; color:var(--accent-red); text-align:center; padding:16px 0;">Gagal menjalankan pemindaian sysfs.</div>';
        }
    } catch (e) {
        container.innerHTML = `<div style="font-size:12px; color:var(--accent-red); text-align:center; padding:16px 0;">Error: ${e.message}</div>`;
    } finally {
        if (btn) {
            btn.textContent = 'Pindai Seluruh Sistem & Hardware (Deep Scan)';
            btn.disabled = false;
        }
    }
}

function filterDeepTunables(cat) {
    activeTunableFilter = cat;
    document.querySelectorAll('#deep-filters .filter-chip').forEach(c => {
        if (c.textContent.toLowerCase() === cat.toLowerCase() || (cat === 'all' && c.textContent === 'Semua')) {
            c.classList.add('active');
        } else {
            c.classList.remove('active');
        }
    });
    renderDeepTunables();
}

function renderDeepTunables() {
    const container = document.getElementById('deep-tunables-container');
    if (!container) return;

    const filtered = activeTunableFilter === 'all' ? discoveredTunables :
        discoveredTunables.filter(t => t.category && t.category.toLowerCase().includes(activeTunableFilter.toLowerCase().replace('&', '')));

    if (filtered.length === 0) {
        container.innerHTML = `<div style="font-size:12px; color:var(--text-muted); text-align:center; padding:16px 0;">Tidak ada parameter dalam kategori "${activeTunableFilter}".</div>`;
        return;
    }

    container.innerHTML = '';
    filtered.forEach((t, idx) => {
        const card = document.createElement('div');
        card.className = 'tunable-card';

        // Description & Tips
        let descHtml = '';
        if (t.desc && t.desc.trim()) {
            descHtml = `<div style="font-size:11px; color:var(--text-secondary); margin-bottom:6px; line-height:1.4;">${t.desc}</div>`;
        }
        let recHtml = '';
        if (t.recommendation && t.recommendation.trim()) {
            recHtml = `<div style="font-size:10px; color:var(--accent-green); background:rgba(16,185,129,0.1); border:1px solid rgba(16,185,129,0.25); border-radius:6px; padding:4px 8px; margin-bottom:8px;"><b>Tips:</b> ${t.recommendation}</div>`;
        }

        // Help text banner (extracting '#' comment lines)
        let helpHtml = '';
        if (t.help && t.help.trim()) {
            helpHtml = `<div class="tunable-help"><b># Petunjuk Kernel:</b> ${t.help}</div>`;
        }

        // Control element based on auto-discovered type
        let ctrlHtml = '';
        if (t.type === 'bool') {
            const isChecked = t.value === '1' || t.value === 'Y' || t.value === 'true' || t.value === 'enabled';
            ctrlHtml = `
                <div style="display:flex; justify-content:space-between; align-items:center; width:100%;">
                    <span style="font-size:11.5px; color:var(--text-secondary);">Status: <b style="color:${isChecked ? 'var(--accent-green)' : 'var(--text-muted)'}">${isChecked ? 'Aktif (1)' : 'Mati (0)'}</b></span>
                    <label class="switch">
                        <input type="checkbox" ${isChecked ? 'checked' : ''} onchange="applyDeepTunable('${t.path}', this.checked ? '1' : '0')">
                        <span class="slider"></span>
                    </label>
                </div>
            `;
        } else if (t.type === 'choice' && Array.isArray(t.options) && t.options.length > 0) {
            const optsHtml = t.options.map(opt => {
                const val = typeof opt === 'object' ? opt.value : opt;
                const lbl = typeof opt === 'object' ? (opt.label || opt.value) : opt;
                return `<option value="${val}" ${val === t.value ? 'selected' : ''}>${lbl}</option>`;
            }).join('');
            ctrlHtml = `
                <div style="display:flex; gap:8px; width:100%;">
                    <select class="tunable-input" id="tunable-input-${idx}" onchange="applyDeepTunable('${t.path}', this.value)" style="flex:1;">
                        ${optsHtml}
                    </select>
                </div>
            `;
        } else {
            ctrlHtml = `
                <div class="tunable-ctrl-row">
                    <input type="text" class="tunable-input" id="tunable-input-${idx}" value="${t.value || ''}">
                    <button class="btn-tunable-apply" onclick="applyDeepTunable('${t.path}', document.getElementById('tunable-input-${idx}').value)">
                        Terapkan
                    </button>
                </div>
            `;
        }

        card.innerHTML = `
            <div class="tunable-header">
                <div>
                    <div class="tunable-title">${t.name}</div>
                    <div class="tunable-path">${t.path}</div>
                </div>
                <span class="tunable-cat-badge">${t.category || 'General'}</span>
            </div>
            ${descHtml}
            ${recHtml}
            ${helpHtml}
            ${ctrlHtml}
        `;
        container.appendChild(card);
    });
}

async function applyDeepTunable(path, value) {
    logToConsole(`Menyetel ${path} -> ${value}...`);
    const res = await execCmd(`${LYNXD} tunable set "${path}" "${value}"`);
    if (res.errno === 0) {
        logToConsole(`Berhasil menyetel ${path} = ${value}`, 'success');
    } else {
        logToConsole(`Gagal menyetel ${path}: ${res.stderr || res.stdout}`, 'error');
    }
}

async function inspectManualNode() {
    const input = document.getElementById('manual-node-input');
    const resultBox = document.getElementById('manual-node-result');
    if (!input || !input.value.trim() || !resultBox) return;

    const path = input.value.trim();
    resultBox.innerHTML = '<div style="font-size:11px; color:var(--text-muted);">Memeriksa node sysfs...</div>';

    const res = await execCmd(`${LYNXD} inspect node "${path}"`);
    if (res.errno === 0 && res.stdout && res.stdout.startsWith('{')) {
        try {
            const data = JSON.parse(res.stdout);
            let helpHtml = data.help ? `<div class="tunable-help" style="margin-top:6px;"><b># Petunjuk Kernel:</b> ${data.help}</div>` : '';
            resultBox.innerHTML = `
                <div class="tunable-card" style="margin-top:6px;">
                    <div class="tunable-header">
                        <div>
                            <div class="tunable-title">${data.name || path}</div>
                            <div class="tunable-path">${data.path}</div>
                        </div>
                        <span class="tunable-cat-badge">${data.writable ? 'Writable' : 'Read-Only'}</span>
                    </div>
                    ${helpHtml}
                    <div class="tunable-ctrl-row">
                        <input type="text" class="tunable-input" id="manual-val-input" value="${data.value || ''}">
                        <button class="btn-tunable-apply" onclick="applyDeepTunable('${data.path}', document.getElementById('manual-val-input').value)">
                            Terapkan
                        </button>
                    </div>
                </div>
            `;
        } catch (e) {
            resultBox.innerHTML = '<div style="font-size:11px; color:var(--accent-red);">Gagal mem-parsing data node.</div>';
        }
    } else {
        resultBox.innerHTML = '<div style="font-size:11px; color:var(--accent-red);">Node tidak ditemukan atau tidak dapat dibaca.</div>';
    }
}

// Initialization & Periodic Sync Polling
window.addEventListener('DOMContentLoaded', () => {
    syncState();
    loadClusterTopology();
    pollTelemetry();
    pollNativeStatus();
    loadApplist();
    pollGpuInfo();
    pollThermalZones();
    loadIoDevices();
    pollWebWakelocks();
    loadTcpCongestion();
    loadWebVmTunables();
    loadWebBoefflaAndDoze();

    setInterval(syncState, 3000);
    setInterval(pollTelemetry, 1500);

    // Check Standalone Warning
    if (typeof ksu === 'undefined' || !ksu.exec) {
        const warn = document.getElementById('standalone-banner');
        if (warn) warn.style.display = 'block';
    }
});


