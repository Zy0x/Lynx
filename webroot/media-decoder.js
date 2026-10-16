/* Explicit media compatibility controls; no periodic root queries. */
(() => {
    const modes = ['system', 'omx_compat', 'prefer_hardware', 'prefer_software'];
    const titles = { system: 'media_mode_system', omx_compat: 'media_mode_omx', prefer_hardware: 'media_mode_hardware', prefer_software: 'media_mode_software' };
    const descriptions = { system: 'media_desc_system', omx_compat: 'media_desc_omx', prefer_hardware: 'media_desc_hardware', prefer_software: 'media_desc_software' };
    const tr = (key, values) => window.LynxI18n.token(key, values);
    let status = null;
    let selected = 'system';
    let boot = false;
    let busy = false;
    let feedback = '';

    function node(tag, key, values) {
        const element = document.createElement(tag);
        if (key) element.textContent = tr(key, values);
        return element;
    }

    function render() {
        const card = document.getElementById('media-decoder-card');
        if (!card) return;
        card.replaceChildren();
        const title = node('div', 'media_decoder_title'); title.className = 'card-title'; card.append(title);
        card.append(node('p', 'media_decoder_description'));
        if (!status) card.append(node('p', 'media_native_unavailable'));
        const label = node('label');
        const select = node('select'); select.id = 'media-decoder-mode'; select.className = 'input-text';
        select.style.minHeight = '48px';
        modes.forEach(mode => {
            const option = node('option', titles[mode]); option.value = mode;
            option.disabled = !status?.supported_modes?.includes(mode);
            select.append(option);
        });
        select.value = selected; select.disabled = busy || !status;
        label.append(node('span', 'media_decoder_title'), select); card.append(label);
        select.onchange = () => { selected = select.value; if (selected === 'system') boot = false; feedback = ''; render(); };
        card.append(node('p', descriptions[selected]));
        const toggleLabel = node('label'); toggleLabel.className = 'switch-row';
        toggleLabel.style.minHeight = '48px';
        const toggle = node('input'); toggle.type = 'checkbox'; toggle.checked = boot;
        toggle.disabled = busy || !status || selected === 'system';
        toggle.onchange = () => { boot = toggle.checked; };
        toggleLabel.append(node('span', 'media_apply_boot'), toggle); card.append(toggleLabel);
        card.append(node('p', 'media_boot_note'));
        if (status?.pending_restart) card.append(node('p', 'media_pending_restart'));
        if (status) card.append(node('p', 'media_saved_profile', { mode: tr(titles[status.configured_mode] || titles.system) }));
        if (status) card.append(node('p', status.session_property_verified ? 'media_properties_verified' : 'media_properties_unmanaged'));
        if (feedback) card.append(node('p', feedback.startsWith('error:') ? 'media_action_failed' : feedback,
            { code: feedback.slice(6) }));
        const apply = node('button', busy ? 'media_busy' : 'media_apply'); apply.className = 'btn btn-accent';
        apply.style.minHeight = '48px'; apply.disabled = busy || !status;
        apply.onclick = async () => {
            if (!modes.includes(selected) || !window.confirm(tr('media_confirm_message'))) return;
            busy = true; feedback = ''; render();
            try {
                const response = await execCmd(`${LYNXD} media set '${selected}' '${boot}' --restart-player`);
                const result = JSON.parse(response.stdout);
                feedback = response.errno === 0 && result.ok ? 'media_applied' : `error:${result.error || 'apply_failed'}`;
            } catch (_) { feedback = 'error:native_unavailable'; }
            busy = false; await refresh();
        };
        const refreshButton = node('button', 'media_refresh'); refreshButton.className = 'btn';
        refreshButton.style.minHeight = '48px'; refreshButton.disabled = busy; refreshButton.onclick = refresh;
        card.append(apply, refreshButton, node('h4', 'media_active_decoders'));
        const active = status?.active_decoders || [];
        if (!active.length) card.append(node('p', 'media_no_active'));
        active.forEach(name => { const item = node('p'); item.textContent = name; card.append(item); });
        const details = node('details'); details.append(node('summary', 'media_declared_title'), node('p', 'media_declared_note'));
        (status?.declared_codecs || []).forEach(codec => {
            details.append(node('p', 'media_decoder_entry', { name: codec.name, mime: codec.mime,
                kind: tr(codec.software ? 'media_kind_software' : 'media_kind_vendor') }));
        });
        card.append(details);
    }

    async function refresh() {
        if (busy) return;
        busy = true; render();
        try {
            const response = await execCmd(`${LYNXD} media status`);
            const result = JSON.parse(response.stdout);
            status = response.errno === 0 && result.ok ? result : null;
            if (status) { selected = modes.includes(status.configured_mode) ? status.configured_mode : 'system'; boot = !!status.apply_on_boot; }
        } catch (_) { status = null; }
        busy = false; render();
    }
    window.addEventListener('lynx:locale-changed', render);
    window.addEventListener('DOMContentLoaded', refresh);
})();
