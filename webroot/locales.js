/* Indonesian/English UI locale support for the Lynx WebUI. */
(function () {
    const normalize = value => String(value).replace(/\s+/g, ' ').trim();
    const localeData = window.LYNX_LOCALE_DATA || { sources: {}, id: {}, en: {} };
    const sourceToKey = Object.fromEntries(Object.entries(localeData.sources).map(([key, value]) => [normalize(value), key]));
    const catalogs = { id: localeData.id, en: localeData.en };
    const originals = new WeakMap();
    const attributeOriginals = new WeakMap();
    let locale = 'id';



    function systemLocale() {
        const language = (navigator.languages && navigator.languages[0]) || navigator.language || 'id';
        return language.toLowerCase().startsWith('en') ? 'en' : 'id';
    }

    function selectedLocale() {
        const choice = localStorage.getItem('lynx.locale') || 'system';
        return choice === 'system' ? systemLocale() : (choice === 'en' ? 'en' : 'id');
    }

    function text(value) {
        const source = normalize(value);
        const key = sourceToKey[source];
        if (key && catalogs[locale][key]) return catalogs[locale][key];
        const dynamic = [
            [/^Laporan berhasil dibuat: (.+)$/, 'Report created: $1'],
            [/^Export gagal: (.+)$/, 'Export failed: $1'],
            [/^Membuat arsip diagnostik lengkap\.\.\.$/, 'Creating complete diagnostic archive…'],
            [/^Menjalankan SQLite VACUUM & Storage TRIM\.\.\.$/, 'Running SQLite VACUUM and storage TRIM…'],
            [/^Proses backup selesai\.$/, 'Backup process completed.'],
            [/^Restore selesai\.$/, 'Restore completed.'],
            [/^Refresh rate disetel$/, 'Refresh rate set'],
            [/^Meluncurkan aplikasi Lynx Companion\.\.\.$/, 'Launching Lynx Companion…'],
            [/^Mencadangkan partisi boot\/init_boot\.\.\.$/, 'Backing up boot/init_boot partitions…'],
            [/^Memulai flashing AnyKernel3: (.+)\.\.\.$/, 'Starting AnyKernel3 flash: $1…'],
            [/^Memulihkan partisi dari (.+)\.\.\.$/, 'Restoring partition from $1…'],
            [/^Mengubah refresh rate ke (.+) Hz\.\.\.$/, 'Changing refresh rate to $1 Hz…'],
            [/^Membebaskan Cache RAM \(Drop Caches\)\.\.\.$/, 'Freeing RAM cache (drop caches)…'],
            [/^Cache RAM dibebaskan\.$/, 'RAM cache freed.'],
            [/^Menyetel Aggressive Doze ke (.+)\.\.\.$/, 'Setting Aggressive Doze to $1…'],
            [/^Aggressive Doze diperbarui$/, 'Aggressive Doze updated'],
            [/^Menambahkan (.+) ke whitelist game$/, 'Added $1 to the game whitelist'],
            [/^Menghapus (.+) dari whitelist game$/, 'Removed $1 from the game whitelist'],
            [/^Menyetel governor GPU ke (.+)\.\.\.$/, 'Setting GPU governor to $1…'],
            [/^Menyetel tingkat GPU Boost ke Level (.+)\.\.\.$/, 'Setting GPU Boost to Level $1…'],
            [/^Menerapkan preset governor schedutil: (.+)\.\.\.$/, 'Applying schedutil governor preset: $1…'],
            [/^Menyetel (.+) -> (.+)\.\.\.$/, 'Setting $1 -> $2…'],
            [/^Berhasil menyetel (.+) = (.+)$/, 'Successfully set $1 = $2'],
            [/^Gagal menyetel (.+): (.+)$/, 'Failed to set $1: $2'],
            [/^Mengatur printk ke (.+)$/, 'Setting printk to $1'],
            [/^Menyetel scheduler (.+) ke (.+)\.\.\.$/, 'Setting scheduler for $1 to $2…'],
            [/^Scheduler (.+) diterapkan pada (.+)$/, 'Scheduler $1 applied to $2'],
            [/^Menerapkan preset VM '(.+)'\.\.\.$/, "Applying VM preset '$1'…"],
            [/^Preset VM '(.+)' diterapkan$/, 'VM preset $1 applied'],
            [/^Menyetel (.+) -> (.+)\.\.\.$/, 'Setting $1 -> $2…'],
            [/^Berhasil menyetel (.+) = (.+)$/, 'Successfully set $1 = $2'],
            [/^Gagal menyetel (.+): (.+)$/, 'Failed to set $1: $2'],
            [/^Menyetel TCP Congestion Control ke (.+)\.\.\.$/, 'Setting TCP congestion control to $1…'],
            [/^TCP disetel ke (.+)$/, 'TCP set to $1'],
            [/^Menyetel scheduler (.+) ke (.+)\.\.\.$/, 'Setting scheduler for $1 to $2…'],
            [/^Scheduler (.+) diterapkan pada (.+)$/, 'Scheduler $1 applied to $2'],
            [/^VM (.+) disetel ke (.+)$/, 'VM $1 set to $2'],
            [/^Preset VM '(.+)' diterapkan$/, 'VM preset $1 applied'],
            [/^Menyetel refresh rate ke (.+) Hz\.\.\.$/, 'Setting refresh rate to $1 Hz…'],
            [/^Governor GPU (.+) diterapkan$/, 'GPU governor $1 applied'],
            [/^Menyetel governor GPU ke (.+)\.\.\.$/, 'Setting GPU governor to $1…'],
            [/^Menyetel tingkat GPU Boost ke Level (.+)\.\.\.$/, 'Setting GPU Boost to Level $1…'],
            [/^GPU Boost level (.+) diterapkan$/, 'GPU Boost level $1 applied'],
            [/^Menerapkan preset governor schedutil: (.+)\.\.\.$/, 'Applying schedutil governor preset: $1…'],
            [/^Governor preset (.+) diterapkan$/, 'Governor preset $1 applied'],
            [/^Deep Scan: Menemukan (.+) parameter hardware\/kernel yang dapat diatur!$/, 'Deep Scan: Found $1 configurable hardware/kernel parameters!']
            ,[/^\+(.+) game lainnya$/, '+$1 more games']
            ,[/^Laporan Diagnostik Berhasil Diekspor!\s+Lokasi file: (.+)$/, 'Diagnostic report exported successfully!\nFile location: $1']
            ,[/^PERINGATAN FLASHING KERNEL:\s+Apakah Anda yakin ingin mem-flash kernel dari:\s+(.+)\?\s+Boot image saat ini akan otomatis dicadangkan terlebih dahulu\.$/, 'KERNEL FLASH WARNING:\nAre you sure you want to flash the kernel from:\n$1?\n\nThe current boot image will be backed up first.']
            ,[/^PERINGATAN RESTORE BOOT:\s+Apakah Anda yakin ingin memulihkan partisi dari backup:\s+(.+)\?$/, 'BOOT RESTORE WARNING:\nAre you sure you want to restore the partition from this backup:\n$1?']
            ,[/^Tidak ada parameter dalam kategori "(.+)"\.$/, 'No parameters in category "$1".']
        ];
        if (locale === 'en') {
            for (const [pattern, replacement] of dynamic) {
                if (pattern.test(source)) return source.replace(pattern, replacement);
            }
        } else {
            const dynamicIndonesian = [
                [/^Updating (.+) -> (.+)\.\.\.$/, 'Memperbarui $1 -> $2…'],
                [/^Set (.+) = (.+) success$/, 'Berhasil mengatur $1 = $2'],
                [/^Failed setting (.+): (.+)$/, 'Gagal mengatur $1: $2'],
                [/^ADB Keep-Alive locked on port (.+)$/, 'ADB Keep-Alive dikunci pada port $1'],
                [/^ADB Keep-Alive disabled$/, 'ADB Keep-Alive dinonaktifkan'],
                [/^Port tidak valid! Masukkan port (.+)$/, 'Port tidak valid! Masukkan port $1'],
                [/^ADB Custom Port locked to (.+)$/, 'Port kustom ADB dikunci ke $1'],
                [/^Cluster (.+) locked\.$/, 'Cluster $1 dikunci.'],
                [/^Cluster (.+) unlocked\.$/, 'Cluster $1 dibuka kuncinya.'],
                [/^Failed to change cluster lock: (.+)$/, 'Gagal mengubah kunci cluster: $1'],
                [/^Setting Policy (.+) Freq: Min=(.+)MHz Max=(.+)MHz\.\.\.$/, 'Mengatur frekuensi Policy $1: Min=$2 MHz, Max=$3 MHz…'],
                [/^Policy (.+) frequency applied\.$/, 'Frekuensi Policy $1 diterapkan.'],
                [/^Failed setting policy frequency: (.+)$/, 'Gagal mengatur frekuensi policy: $1'],
                [/^Setting Policy (.+) Governor: (.+)\.\.\.$/, 'Mengatur governor Policy $1: $2…'],
                [/^Policy (.+) governor set to (.+)\.$/, 'Governor Policy $1 diatur ke $2.'],
                [/^Failed setting governor: (.+)$/, 'Gagal mengatur governor: $1'],
                [/^Running CCleaner & Memory Compaction\.\.\.$/, 'Menjalankan CCleaner dan pemadatan memori…'],
                [/^Memory compaction complete\.$/, 'Pemadatan memori selesai.'],
                [/^SELinux mode updated$/, 'Mode SELinux diperbarui'],
                [/^Printk set to (.+)$/, 'Printk diatur ke $1'],
                [/^Set printk to (.+)$/, 'Mengatur printk ke $1'],
                [/^Failed setting printk: (.+)$/, 'Gagal mengatur printk: $1'],
                [/^Setting VM (.+) to (.+)\.\.\.$/, 'Mengatur VM $1 ke $2…'],
                [/^Setting TCP Congestion Control to (.+)\.\.\.$/, 'Mengatur TCP Congestion Control ke $1…'],
                [/^Applying VM preset '(.+)'\.\.\.$/, "Menerapkan preset VM '$1'…"],
                [/^Setting GPU governor to (.+)\.\.\.$/, 'Mengatur governor GPU ke $1…'],
                [/^Setting GPU Boost Level (.+)\.\.\.$/, 'Mengatur tingkat GPU Boost ke $1…'],
                [/^Setting scheduler (.+) to (.+)\.\.\.$/, 'Mengatur scheduler $1 ke $2…'],
                [/^Setting refresh rate to (.+) Hz\.\.\.$/, 'Mengatur refresh rate ke $1 Hz…'],
                [/^Setting sysfs node (.+) to (.+)\.\.\.$/, 'Mengatur node sysfs $1 ke $2…'],
                [/^Failed to set sysfs node (.+): (.+)$/, 'Gagal mengatur node sysfs $1: $2'],
            ];
            for (const [pattern, replacement] of dynamicIndonesian) {
                if (pattern.test(source)) return source.replace(pattern, replacement);
            }
        }
        return value;
    }

    function translateNode(node) {
        if (!originals.has(node)) originals.set(node, node.nodeValue);
        const source = originals.get(node);
        const clean = normalize(source);
        const key = sourceToKey[clean];
        if (!clean) return;
        const match = String(source).match(/^(\s*)([\s\S]*?)(\s*)$/);
        const replacement = (key && catalogs[locale][key]) || text(clean);
        if (replacement === clean) return;
        const next = `${match[1]}${replacement}${match[3]}`;
        if (node.nodeValue !== next) node.nodeValue = next;
    }

    function translateAttribute(element, name) {
        let values = attributeOriginals.get(element);
        if (!values) {
            values = new Map();
            attributeOriginals.set(element, values);
        }
        if (!values.has(name)) values.set(name, element.getAttribute(name));
        const source = values.get(name);
        if (!source) return;
        const key = sourceToKey[normalize(source)];
        const translated = (key && catalogs[locale][key]) || text(source);
        if (translated) {
            const next = translated;
            if (element.getAttribute(name) !== next) element.setAttribute(name, next);
        }
    }

    function apply(root = document.body) {
        if (!root) return;
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        let node;
        while ((node = walker.nextNode())) {
            const parent = node.parentElement;
            if (parent && !parent.closest('script,style,code,pre,svg')) translateNode(node);
        }
        const elements = [];
        if (root.nodeType === Node.ELEMENT_NODE && root.matches?.('[placeholder],[title],[aria-label]')) elements.push(root);
        root.querySelectorAll?.('[placeholder],[title],[aria-label]').forEach(element => elements.push(element));
        elements.forEach(element => {
            ['placeholder', 'title', 'aria-label'].forEach(name => {
                if (element.hasAttribute(name)) translateAttribute(element, name);
            });
        });
        document.documentElement.lang = locale;
        const picker = document.getElementById('language-picker');
        if (picker && !picker.matches(':focus')) picker.value = localStorage.getItem('lynx.locale') || 'system';
        document.title = text('LYNX KERNEL MANAGER — Dashboard');
    }

    function setLocale(choice) {
        if (choice === 'system') localStorage.removeItem('lynx.locale');
        else localStorage.setItem('lynx.locale', choice === 'en' ? 'en' : 'id');
        locale = selectedLocale();
        apply();
    }

    window.LynxI18n = { text, apply, setLocale, get locale() { return locale; } };
    window.setWebLocale = setLocale;
    const nativeAlert = window.alert.bind(window);
    const nativeConfirm = window.confirm.bind(window);
    window.alert = message => nativeAlert(text(message));
    window.confirm = message => nativeConfirm(text(message));

    window.addEventListener('DOMContentLoaded', () => {
        locale = selectedLocale();
        apply();
        const observer = new MutationObserver(records => {
            for (const record of records) {
                if (record.type === 'characterData') translateNode(record.target);
                record.addedNodes?.forEach(node => {
                    if (node.nodeType === Node.TEXT_NODE) translateNode(node);
                    else if (node.nodeType === Node.ELEMENT_NODE) apply(node);
                });
                if (record.type === 'attributes') translateAttribute(record.target, record.attributeName);
            }
        });
        observer.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ['placeholder', 'title', 'aria-label'] });
    });
})();
