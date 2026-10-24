#!/usr/bin/env python3
"""Validate Android locale key and format-argument parity for Lynx Companion."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
import json
from validate_media_decoder_locales import check_media_decoder_locales
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "LynxCompanion" / "app" / "src" / "main" / "res"
ANDROID_LOCALES = {
    "id": RES / "values-id" / "strings.xml",
    "en": RES / "values-en" / "strings.xml",
}
ANDROID_DEFAULT = RES / "values" / "strings.xml"
PLACEHOLDER = re.compile(r"%(\d+)\$([a-zA-Z])")
WEB_DATA = ROOT / "webroot" / "locale-data.js"
WEB_HTML = ROOT / "webroot" / "index.html"
SHELL_LOCALE = ROOT / "core" / "locale.sh"
INSTALLER = ROOT / "customize.sh"
WEB_JS = ROOT / "webroot" / "app.js"
COMMENT_ROOTS = (
    ROOT / "LynxCompanion" / "app" / "src" / "main",
    ROOT / "LynxCompanion" / "freeform" / "src" / "main",
    ROOT / "core",
    ROOT / "native",
    ROOT / "platforms",
    ROOT / "webroot",
    ROOT / "system" / "bin",
    ROOT / "tools",
)
ROOT_SCRIPT_SOURCES = tuple(ROOT / name for name in (
    "customize.sh", "library.sh", "post-fs-data.sh", "service.sh", "uninstall.sh",
))
COMMENT_EXTENSIONS = {
    ".kt", ".kts", ".java", ".rs", ".sh", ".js", ".html", ".css", ".xml", ".py",
    ".ps1", ".cmd", ".bat", ".toml", ".gradle", ".properties", ".ini", ".cfg",
    ".conf", ".prop", ".yaml", ".yml", ".sql",
}
INDONESIAN_COMMENT_MARKERS = re.compile(
    r"\b(?:dari|jika|yang|untuk|saat|tidak|pada|dengan|agar|contoh|selesai|semua|nyata|asli|pabrikan|kapasitas|dinamis|tersedia|perangkat|dimatikan|pengisian|sejuk|batas|otomatis|kustom|menolak|ditemukan|berikut|dijalankan|pengguna|mengubah|ambil|murni|tanpa|angka|ditulis|dibaca|memastikan|menyimpan|menjadi|diperlukan|gunakan|mencegah|aktif|malam|kondisi|masuk|keluar|mengambil|seluruh|beralih|mengunci|bawaan|dipulihkan|ditampilkan|berjalan|berhasil|gagal|bisa|sudah|belum|hanya|masih|akan|wajib|harus|dapat|tersebut|lainnya|tingkat|bawah|atas|depan|belakang|penuh|rendah|tinggi|kecil|besar|cepat|lambat|aman|panas|buka|tutup|kunci|pilih|hapus|simpan|terapkan|pulihkan|pengaturan|perubahan|penggunaannya|pemulihan|pengujian|pembacaan|penulisan|pencarian|pengukuran|perhitungan|pemantauan|pengontrol|pengendali|penggantian|penerapan|pemeriksaan|penyimpanan|pengaktifan|penonaktifan|pembatasan|penyesuaian|pemisahan|penggabungan|pembuatan|perangkatnya|terdeteksi|terkunci|terbaca|tertulis|diterapkan|disimpan|dihapus|diubah|diambil|diperiksa|dihentikan|digunakan|menggunakan|menjalankan|menghentikan|memilih|menghapus|membaca|menulis|menampilkan|menerapkan|mengembalikan|mengatur|menjaga|menghindari|menangani|menyediakan|mendukung|memungkinkan|membuat|menambahkan|menghasilkan|menerima|mengirim|mencari|memuat|memulai|berhenti|berada|berisi|menunggu|setelah|sebelum|selama|karena|supaya|sehingga|meskipun|apabila|ketika|dimana|yakni|yaitu)\b",
    re.IGNORECASE,
)
INDONESIAN_COMMENT_EXTRA = re.compile(
    r"\b(?:hemat|seimbang|maksimal|daya|fungsi|nilai|kecepatan|frekuensi|layar|inti|tanpa|"
    r"pendingin|pengguna|tetap|mati|otomatis|kustom|suhu|periksa|pastikan|jalankan|mengubah|"
    r"sesuai|sumber|laporan|kebijakan|perangkat|batas|prosesor|masing-masing|mengizinkan|"
    r"melarang|dipakai|berikutnya|menekan|tekan|tombol|sistem|profil|bagian|utama|rata-rata|"
    r"tertinggi|terendah|lengkap|daftar|riwayat|grafik|sampel|pembersihan|pengisian|lebih|"
    r"kurang|berdasarkan|menentukan|menyimpan|mengaktifkan|menonaktifkan|membuka|menahan|"
    r"mendeteksi|memeriksa|menemukan|ditemukan|dijalankan|dilakukan|diperbarui|tersedia|"
    r"terpasang|terbuka|tertutup|sebagian|langsung|sementara|tambahan|berulang|terpisah|"
    r"bersama|berurutan|konfigurasi|setelan|perintah|kelompok|rentang|kurangi|"
    r"naikkan|turunkan|aktifkan|nonaktifkan|performa|aturan|aplikasi|penggunaan|relokasi|"
    r"manajemen|pembersih|ringkas|isolasi|penyebab|antar|kinerja|tekanan|perubahan|tingkat|"
    r"pemilih|penerima|pengembali|pemulihan|menjaga|melakukan|menurunkan|menaikkan|"
    r"dapatkan|menyesuaikan|disesuaikan|menampilkan|menyertakan|mengetahui|dibandingkan|"
    r"milik|dalam|luar|kartu|lembar|kolom|ruang|bernilai|kalkulasi|perhitungan|"
    r"dipicu|terakhir|bawaan|beralih|berpindah|berubah|aktifnya|nonaktif|dihindari|"
    r"dihasilkan|memerlukan|butuhkan|diperlukan|menyertai|contohnya|misalnya|catat|catatan)\b",
    re.IGNORECASE,
)
COMMENT_LINE = re.compile(r"^\s*(?://+|#|/\*+|\*+|<!--|-->|--|;|!)\s?(.*)$")


def read_strings(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise ValueError(f"Missing catalog: {path.relative_to(ROOT)}")
    root = ET.parse(path).getroot()
    items = root.findall("string")
    names = [item.attrib["name"] for item in items]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        raise ValueError(f"Duplicate Android string key(s) in {path.relative_to(ROOT)}: {duplicates}")
    return {item.attrib["name"]: "".join(item.itertext()) for item in items}


def read_plurals(path: Path) -> dict[str, dict[str, str]]:
    root = ET.parse(path).getroot()
    items = root.findall("plurals")
    names = [item.attrib["name"] for item in items]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        raise ValueError(f"Duplicate Android plural key(s) in {path.relative_to(ROOT)}: {duplicates}")
    return {
        item.attrib["name"]: {
            child.attrib["quantity"]: "".join(child.itertext())
            for child in item.findall("item")
        }
        for item in root.findall("plurals")
    }


def check_android(res: Path = RES) -> list[str]:
    errors: list[str] = []
    paths = {locale: res / f"values-{locale}" / "strings.xml" for locale in ("id", "en")}
    catalogs = {locale: read_strings(path) for locale, path in paths.items()}
    default = read_strings(res / "values" / "strings.xml")
    baseline = catalogs["id"]
    translated = catalogs["en"]
    for locale, catalog in catalogs.items():
        if set(default) != set(catalog):
            errors.append(
                f"Android {locale} and default catalogs differ: "
                f"missing={sorted(set(default) - set(catalog))}, extra={sorted(set(catalog) - set(default))}"
            )
    for key in sorted(baseline.keys() - translated.keys()):
        errors.append(f"Android en is missing string key: {key}")
    for key in sorted(translated.keys() - baseline.keys()):
        errors.append(f"Android en has an extra string key: {key}")
    for key in sorted(baseline.keys() & translated.keys()):
        expected = sorted(PLACEHOLDER.findall(baseline[key]))
        actual = sorted(PLACEHOLDER.findall(translated[key]))
        if expected != actual:
            errors.append(f"Android placeholder mismatch for {key}: id={expected}, en={actual}")
    percent_tokens = re.compile(r"%%|%\d+\$[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]|%")
    for locale, catalog in {"default": default, **catalogs}.items():
        for key, template in catalog.items():
            if PLACEHOLDER.search(template) and any(match.group() == "%" for match in percent_tokens.finditer(template)):
                errors.append(f"Unescaped literal percent in formatted Android resource {key}/{locale}")
    plural_catalogs = {locale: read_plurals(path) for locale, path in paths.items()}
    id_plurals = plural_catalogs["id"]
    en_plurals = plural_catalogs["en"]
    for key in sorted(id_plurals.keys() - en_plurals.keys()):
        errors.append(f"Android en is missing plural key: {key}")
    for key in sorted(en_plurals.keys() - id_plurals.keys()):
        errors.append(f"Android en has an extra plural key: {key}")
    for key in sorted(id_plurals.keys() & en_plurals.keys()):
        id_quantities = id_plurals[key]
        en_quantities = en_plurals[key]
        if id_quantities.keys() != en_quantities.keys():
            errors.append(f"Android plural quantities mismatch for {key}: id={sorted(id_quantities)}, en={sorted(en_quantities)}")
            continue
        for quantity in sorted(id_quantities):
            expected = sorted(PLACEHOLDER.findall(id_quantities[quantity]))
            actual = sorted(PLACEHOLDER.findall(en_quantities[quantity]))
            if expected != actual:
                errors.append(f"Android plural placeholder mismatch for {key}/{quantity}: id={expected}, en={actual}")
    return errors


def check_freeform_android() -> list[str]:
    return check_android(ROOT / "LynxCompanion" / "freeform" / "src" / "main" / "res")


class VisibleTextParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.stack: list[str] = []
        self.texts: set[str] = set()
        self.event_handlers: list[str] = []

    def handle_starttag(self, tag: str, attrs) -> None:
        self.stack.append(tag.lower())
        if tag.lower() not in {"script", "style", "code", "pre", "svg"}:
            for name, value in attrs:
                if name.lower() in {"placeholder", "title", "aria-label"} and value:
                    text = " ".join(value.split())
                    if text:
                        self.texts.add(text)
                if name.lower().startswith("on") and value:
                    self.event_handlers.append(value)
                if name.lower() == "value" and value and tag.lower() == "input":
                    input_type = dict(attrs).get("type", "").lower()
                    if input_type in {"button", "submit", "reset"}:
                        self.texts.add(" ".join(value.split()))

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        for index in range(len(self.stack) - 1, -1, -1):
            if self.stack[index] == tag:
                del self.stack[index:]
                break

    def handle_data(self, data: str) -> None:
        if any(tag in {"script", "style"} for tag in self.stack):
            return
        value = " ".join(data.split())
        if value:
            self.texts.add(value)


def check_web() -> list[str]:
    errors: list[str] = []
    raw = WEB_DATA.read_text(encoding="utf-8").split("=", 1)[1].strip().removesuffix(";")
    data = json.loads(raw)
    sources = data.get("sources", {})
    id_catalog = data.get("id", {})
    en_catalog = data.get("en", {})
    id_keys = set(id_catalog)
    en_keys = set(en_catalog)
    if id_keys != en_keys:
        errors.append(f"WebUI key mismatch: missing-en={sorted(id_keys - en_keys)}, extra-en={sorted(en_keys - id_keys)}")
    if set(sources) != id_keys:
        errors.append("WebUI source map and locale catalogs have different token keys")
    for key in sorted(id_keys & en_keys):
        if not id_catalog[key] or not en_catalog[key]:
            errors.append(f"WebUI locale text is empty for {key}")
        for marker, expression in (("ID", id_catalog[key]), ("EN", en_catalog[key])):
            placeholders = sorted(re.findall(r"\$\{[^}]+\}|\$[A-Za-z_][A-Za-z0-9_]*|%\d+\$[a-zA-Z]|\{[A-Za-z_][A-Za-z0-9_]*\}", expression))
            if marker == "ID":
                expected = placeholders
            elif placeholders != expected:
                errors.append(f"WebUI placeholder mismatch for {key}: id={expected}, en={placeholders}")

    parser = VisibleTextParser()
    parser.feed(WEB_HTML.read_text(encoding="utf-8"))
    catalog_sources = {" ".join(value.split()) for value in sources.values()}
    metadata_catalog = json.loads((ROOT / "tools" / "tunable_metadata_translations.json").read_text(encoding="utf-8"))
    for phrase, english in metadata_catalog.items():
        normalized = " ".join(phrase.split())
        key = next((token for token, source in sources.items() if " ".join(source.split()) == normalized), None)
        if key is None:
            errors.append(f"WebUI tunable metadata is not catalogued: {phrase[:100]}")
            continue
        if en_catalog.get(key) != english:
            errors.append(f"WebUI tunable metadata English text differs from its metadata catalog: {phrase[:100]}")
    for text in sorted(parser.texts - catalog_sources):
        errors.append(f"WebUI static text is not catalogued: {text}")
    for handler in parser.event_handlers:
        for match in re.finditer(r"(['\"])(.*?)\1", handler, re.S):
            phrase = " ".join(match.group(2).split())
            if len(phrase) > 2 and re.search(r"[A-Za-zÀ-ÿ]{3,}", phrase) and phrase not in catalog_sources:
                if re.fullmatch(r"[A-Za-z0-9_.-]+", phrase):
                    continue
                if any(token in phrase for token in catalog_sources if len(token) > 4):
                    continue
                errors.append(f"WebUI inline event text is not catalogued: {phrase}")

    js = "\n".join(path.read_text(encoding="utf-8") for path in sorted((ROOT / "webroot").glob("*.js"))
                   if path.name not in {"locales.js", "locale-data.js"})
    for match in re.finditer(r"(?:textContent|innerHTML|innerText|outerHTML|document\.title)\s*=\s*([^;\n]+)", js):
        expression = match.group(1)
        for literal in re.finditer(r"(['\"])(.*?)\1", expression, re.S):
            phrase = " ".join(literal.group(2).split())
            if not phrase or not re.search(r"[A-Za-zÀ-ÿ]{3,}", phrase):
                continue
            if phrase in catalog_sources:
                continue
            # Numeric templates are covered by their surrounding unit; localized labels need a catalog token.
            if re.fullmatch(r"(?:%|°C|mA|MB|MHz|s|unknown|lynxd|DYNAMIC|\$\{[^}]+\})+", phrase, re.I):
                continue
            if phrase.startswith("${") or phrase.endswith("${}"):
                continue
            errors.append(f"WebUI DOM assignment text is not catalogued: {phrase}")

    locale_js = (ROOT / "webroot" / "locales.js").read_text(encoding="utf-8")
    required_dynamic_web_patterns = (
        ("Bypass Langsung", "Direct Bypass"),
    )
    for source_text, translated_text in required_dynamic_web_patterns:
        if source_text not in locale_js or translated_text not in locale_js:
            errors.append(f"WebUI dynamic localization rule is missing for: {source_text}")
    if "template.matchAll" not in locale_js or not any("$tzName" in phrase for phrase in metadata_catalog):
        errors.append("WebUI dynamic tunable metadata template localization is missing")
    dynamic_marker = "__LYNX_DYNAMIC_CONTENT__"
    for match in re.finditer(r"(?:textContent|innerHTML|innerText|outerHTML|document\.title)\s*=\s*(['`])(.*?)\1", js, re.S):
        raw_value = match.group(2)
        if "${" in raw_value:
            if match.group(0).lstrip().startswith("innerHTML") and "`" == match.group(1):
                static_template = re.sub(r"\$\{.*?\}", dynamic_marker, raw_value, flags=re.S)
                fragment = VisibleTextParser()
                fragment.feed(static_template)
                for value in sorted(fragment.texts):
                    if dynamic_marker not in value and re.search(r"[A-Za-zÀ-ÿ]{3,}", value) and value not in catalog_sources:
                        errors.append(f"WebUI generated HTML text is not catalogued: {value}")
            continue
        values: set[str] = set()
        if match.group(0).lstrip().startswith("innerHTML") and "<" in raw_value:
            fragment = VisibleTextParser()
            fragment.feed(raw_value)
            values.update(fragment.texts)
        else:
            value = " ".join(raw_value.split())
            if value:
                values.add(value)
        for value in sorted(values - catalog_sources):
            errors.append(f"WebUI JavaScript text is not catalogued: {value}")
    for match in re.finditer(r"\b(?:alert|confirm)\(\s*(['\"])(.*?)\1", js, re.S):
        value = " ".join(match.group(2).replace(r"\n", "\n").split())
        if value and value not in catalog_sources:
            errors.append(f"WebUI dialog text is not catalogued: {value}")
    for match in re.finditer(r"\.(?:title|placeholder|ariaLabel)\s*=\s*(['\"])(.*?)\1", js, re.S):
        value = " ".join(match.group(2).split())
        if value and re.search(r"[A-Za-zÀ-ÿ]{3,}", value) and value not in catalog_sources:
            errors.append(f"WebUI generated accessibility text is not catalogued: {value}")
    for match in re.finditer(r"\.setAttribute\(\s*(['\"])(?:title|placeholder|aria-label)\1\s*,\s*(['\"])(.*?)\2", js, re.S):
        value = " ".join(match.group(3).split())
        if value and re.search(r"[A-Za-zÀ-ÿ]{3,}", value) and value not in catalog_sources:
            errors.append(f"WebUI setAttribute accessibility text is not catalogued: {value}")
    for match in re.finditer(r"(?:document\.createTextNode|insertAdjacentText)\(\s*(['\"])(.*?)\1", js, re.S):
        value = " ".join(match.group(2).split())
        if value and re.search(r"[A-Za-zÀ-ÿ]{3,}", value) and value not in catalog_sources:
            errors.append(f"WebUI generated text node is not catalogued: {value}")
    return errors


def check_android_accessibility() -> list[str]:
    errors: list[str] = []
    source_root = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx"
    for path in source_root.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        for match in re.finditer(r'contentDescription\s*=\s*"([^"\\]*(?:\\.[^"\\]*)*)"', source):
            line = source.count("\n", 0, match.start()) + 1
            errors.append(f"Unlocalized hard-coded accessibility label at {path.relative_to(ROOT)}:{line}")
        passthrough_labels = re.compile(r"contentDescription\s*=\s*([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)\s*[,)]")
        for match in passthrough_labels.finditer(source):
            value = match.group(1)
            if value in {"null", "processName", "appName"}:
                continue
            line = source.count("\n", 0, match.start()) + 1
            errors.append(f"Accessibility label bypasses localization at {path.relative_to(ROOT)}:{line}: {value}")
    return errors


def check_android_notification_text() -> list[str]:
    """Reject notification labels and content that bypass Android locale resources."""
    errors: list[str] = []
    source_root = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx"
    patterns = (
        re.compile(r'NotificationChannel\(\s*[^,]+,\s*"([^"\\]*(?:\\.[^"\\]*)*)"', re.S),
        re.compile(r'\.(?:setContentTitle|setContentText|setTicker)\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"'),
        re.compile(r'\.addAction\(\s*[^,]+,\s*"([^"\\]*(?:\\.[^"\\]*)*)"', re.S),
    )
    for path in source_root.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        for pattern in patterns:
            for match in pattern.finditer(source):
                label = match.group(1)
                line = source.count("\n", 0, match.start()) + 1
                if label and re.search(r"[A-Za-zÀ-ÿ]{3,}", label) and label != "Lynx":
                    errors.append(f"Hard-coded notification UI text at {path.relative_to(ROOT)}:{line}: {label}")
    return errors


def check_android_notification_helpers() -> list[str]:
    """Ensure dynamically assembled notification labels use locale resources."""
    errors: list[str] = []
    service_path = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx" / "service" / "LynxAppAutomationService.kt"
    source = service_path.read_text(encoding="utf-8")
    match = re.search(r"private fun formatModeTitle\(profile: String\): String\s*\{([\s\S]*?)\n    \}", source)
    if not match:
        return [f"Missing localized notification title helper: {service_path.relative_to(ROOT)}"]
    body = match.group(1)
    required_resources = (
        "profile_name_balance",
        "profile_name_performance",
        "profile_name_extreme",
        "profile_name_powersave",
        "profile_name_auto",
        "automation_notification_title",
    )
    for resource in required_resources:
        if f"R.string.{resource}" not in body:
            errors.append(f"Dynamic notification title does not use locale resource {resource}")
    technical_profile_ids = {"extreme", "performance", "powersave", "auto"}
    for literal in re.findall(r'"([^"\n]*)"', body):
        if re.search(r"[A-Za-zÀ-ÿ]{3,}", literal) and literal not in technical_profile_ids:
            errors.append("Dynamic notification title contains hard-coded user-facing text")
            break
    return errors


def check_custom_script_output_localization() -> list[str]:
    """Require generated script result labels to remain separate from shell output."""
    errors: list[str] = []
    repository_path = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx" / "data" / "LynxCustomScriptRepository.kt"
    ui_path = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx" / "feature" / "system" / "ui" / "CustomScriptManagerCard.kt"
    repository = repository_path.read_text(encoding="utf-8")
    ui = ui_path.read_text(encoding="utf-8")
    execution = repository.split("suspend fun executeCustomScript", 1)
    execution_body = execution[1].split("suspend fun readCustomRules", 1)[0] if len(execution) == 2 else repository
    for legacy_label in ("[STDERR]", "Selesai dalam ${durationMs}", "Gagal mengeksekusi skrip:"):
        if legacy_label in execution_body:
            errors.append(f"Custom script result still creates hard-coded UI label: {legacy_label}")
    for resource in ("script_output_stderr_label", "script_output_summary", "script_execution_failed"):
        if f"R.string.{resource}" not in ui:
            errors.append(f"Custom script output is missing localized resource {resource}")
    if "localizedUiText(item.name)" not in ui:
        errors.append("Built-in custom script names are not localized when rendered")
    if "lastErrorOutput" not in ui or "lastDurationMs" not in ui or "lastRunFailure" not in ui:
        errors.append("Custom script output UI does not render all structured result fields")
    return errors


def check_compose_raw_text_imports() -> list[str]:
    """Reject hard-coded Compose UI text that bypasses the localization bridge."""
    errors: list[str] = []
    source_root = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx"
    raw_text_import = re.compile(r"^\s*import\s+androidx\.compose\.material3\.Text\s*$", re.M)
    literal_text = re.compile(r'(?<![.\w])Text\(\s*(?:text\s*=\s*)?"((?:\\.|[^"\\])*)"')
    for path in source_root.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        if not raw_text_import.search(source) or path.name == "LynxText.kt":
            continue
        for match in literal_text.finditer(source):
            line = source.count("\n", 0, match.start()) + 1
            errors.append(f"Hard-coded text bypasses LynxText at {path.relative_to(ROOT)}:{line}")
    return errors


def check_compose_catalog_mappings() -> list[str]:
    errors: list[str] = []
    bridge = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx" / "core" / "locale" / "LynxText.kt"
    source = bridge.read_text(encoding="utf-8")
    for prefix in ("dynamic_", "recommendation_"):
        referenced = set(re.findall(rf"R\.string\.({prefix}[a-z0-9_]+)", source))
        catalogued = {key for key in read_strings(ANDROID_LOCALES["id"]) if key.startswith(prefix)}
        if referenced != catalogued:
            errors.append(
                f"Compose {prefix} locale mapping mismatch: "
                f"missing resources={sorted(referenced - catalogued)}, "
                f"unreferenced resources={sorted(catalogued - referenced)}"
            )
    id_strings = read_strings(ANDROID_LOCALES["id"])
    en_strings = read_strings(ANDROID_LOCALES["en"])
    for match in re.finditer(r'DynamicUiText\(Regex\("((?:\\.|[^"\\])*)"\),\s*R\.string\.([a-z0-9_]+)\)', source):
        try:
            pattern = re.compile(json.loads('"' + match.group(1) + '"'))
        except (json.JSONDecodeError, re.error):
            continue
        key = match.group(2)
        expected = set(range(1, pattern.groups + 1))
        for locale, strings in (("id", id_strings), ("en", en_strings)):
            actual = {int(index) for index in re.findall(r"%(\d+)\$[a-zA-Z]", strings.get(key, ""))}
            if actual != expected:
                errors.append(f"Compose dynamic placeholder mismatch for {key}/{locale}: pattern groups={sorted(expected)}, resource placeholders={sorted(actual)}")
    return errors


def check_compose_visible_text_coverage() -> list[str]:
    """Require Compose literals and interpolated UI copy to resolve through a locale catalog."""
    errors: list[str] = []
    source_root = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx"
    mapping_path = source_root / "core" / "locale" / "LegacyUiTextResources.kt"
    mapping_source = mapping_path.read_text(encoding="utf-8")
    mapped: set[str] = set()
    for match in re.finditer(r'^\s*"((?:\\.|[^"\\])*)"\s+to\s+R\.string\.', mapping_source, re.M):
        try:
            mapped.add(json.loads('"' + match.group(1) + '"'))
        except json.JSONDecodeError:
            continue

    bridge = (source_root / "core" / "locale" / "LynxText.kt").read_text(encoding="utf-8")
    patterns: list[re.Pattern[str]] = []
    for literal in re.findall(r'DynamicUiText\(Regex\("((?:\\.|[^"\\])*)"\),\s*R\.string\.', bridge):
        try:
            pattern = json.loads('"' + literal + '"')
            patterns.append(re.compile(pattern))
        except (json.JSONDecodeError, re.error):
            continue

    text_call = re.compile(r'(?<![.\w])Text\(\s*(?:text\s*=\s*)?"((?:\\.|[^"\\])*)"')
    ui_arg = re.compile(r'\b(?:title|subtitle|description|label|name|displayName|text|contentDescription|placeholder|hint|message)\s*=\s*"((?:\\.|[^"\\])*)"')
    conditional_ui = re.compile(
        r'(?:\bText\(\s*(?:text\s*=\s*)?|\b(?:contentDescription|title|subtitle|description|label|text|message|placeholder|hint|'
        r'subLabel|onStatusText|offStatusText|onEffect|offEffect|gamingRecommendation|balancedRecommendation|'
        r'batteryRecommendation|name|category|rec|help)\s*=\s*(?:\{\s*Text\(\s*)?)'
        r'(?P<if>if)\s*\(', re.S,
    )
    conditional_expression = re.compile(
        r'if\s*\([^)]*\)\s*(?:\{\s*)?"((?:\\.|[^"\\])*)"\s*(?:\}\s*)?else\s*'
        r'(?:\{\s*)?"((?:\\.|[^"\\])*)"', re.S,
    )
    technical_dynamic_words = {
        "mhz", "mv", "ma", "mb", "gb", "g", "s", "ms", "us", "µs", "mah", "hz", "fps", "gpu", "cpu",
        "ram", "opp", "core", "cores", "thread", "threads", "db", "w", "v", "a", "latency", "frame",
        "frames", "node", "nodes", "usb", "ssid", "pid", "rss", "dpi", "on", "off", "auto", "bal", "perf",
        "pwr", "ext", "game", "read", "only", "readonly", "kg", "°c", "°f", "%", "x",
    }
    localized_dynamic_markers = re.compile(
        r"(?:Skor Kompatibilitas|Sensor Thermal Hardware Aktif|Sumber Wakeup Tertinggi|"
        r"bangun$|Pustaka Skrip Kernel|Tips:|Aktif:|Min:|Max:|Rentang:|"
        r"Tidak ada parameter yang cocok|Aturan Profil Dikonfigurasi|Zone |Thermal Throttling:|"
        r"Saturation:|KCAL Saturation:|Piksel$|tahan sleep|Stab |"
        r"DISTRIBUSI FRAMETIME|Headphone Gain|Speaker Gain|Microphone Gain|Legacy Minfree Levels|"
        r"Read Wakeup Threshold|Write Wakeup Threshold|Fitur Kernel Aktif|step frekuensi clock OPP|"
        r"Edit Tunable:|STATUS INTI CPU|TABEL LENGKAP STEPPING FREKUENSI|Driver Fisik|Kondisi:|"
        r"Muat Lebih Banyak )", re.I
    )
    for path in source_root.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        seen: set[tuple[int, str]] = set()
        for context in conditional_ui.finditer(source):
            expression = conditional_expression.match(source, context.start("if"))
            if not expression:
                continue
            line = source.count("\n", 0, context.start()) + 1
            for raw_phrase in expression.groups():
                try:
                    phrase = json.loads('"' + raw_phrase + '"')
                except json.JSONDecodeError:
                    phrase = raw_phrase.replace(r'\"', '"').replace(r'\\', '\\')
                if "String.format" in phrase:
                    dynamic_catalog = json.loads((ROOT / "tools" / "compose_dynamic_translations.json").read_text(encoding="utf-8"))
                    if not any(entry["key"] == "dynamic_gpu_client_composition" for entry in dynamic_catalog):
                        errors.append(f"Interpolated conditional Compose text lacks a locale pattern at {path.relative_to(ROOT)}:{line}")
                    continue
                if "$" in phrase:
                    sample = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*", "1", phrase)
                    if any(pattern.fullmatch(sample) for pattern in patterns) or re.fullmatch(r"(?:\d+(?:\.\d+)?|%|°C|MHz|mA|MB|GB|G|s|us|µs|mAh|Threads|Core|/|\(|\)|\+|-|\s)+", sample, re.I):
                        continue
                elif len(phrase) <= 2 or not re.search(r"[A-Za-zÀ-ÿ]{3,}", phrase):
                    continue
                if phrase not in mapped:
                    errors.append(f"Conditional Compose UI text is not mapped to locale resources at {path.relative_to(ROOT)}:{line}: {phrase[:100]}")
        for match in (*text_call.finditer(source), *ui_arg.finditer(source)):
            try:
                value = json.loads('"' + match.group(1) + '"')
            except json.JSONDecodeError:
                value = match.group(1).replace(r'\"', '"').replace(r'\\', '\\')
            line = source.count("\n", 0, match.start()) + 1
            if not value or len(value) <= 2:
                continue
            if "$" not in value:
                if re.search(r"[A-Za-zÀ-ÿ]{3,}", value) and value not in mapped and "R.string." not in source[max(0, match.start() - 120):match.start()]:
                    errors.append(f"Compose visible text is not mapped to locale resources at {path.relative_to(ROOT)}:{line}: {value[:100]}")
                continue
            if not localized_dynamic_markers.search(value):
                continue
            sample = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*", "1", value)
            if any(pattern.fullmatch(sample) for pattern in patterns):
                continue
            words = {word.lower() for word in re.findall(r"[A-Za-zÀ-ÿµ°]+", sample)}
            if not words or words <= technical_dynamic_words:
                continue
            errors.append(f"Interpolated Compose text has no locale pattern at {path.relative_to(ROOT)}:{line}: {value[:100]}")

        declaration = re.compile(r'\bval\s+([A-Za-z_]\w*)\s*=\s*when\b')
        literal = re.compile(r'"((?:\\.|[^"\\])*)"')
        for variable_match in declaration.finditer(source):
            variable = variable_match.group(1)
            usage = re.compile(
                rf'(?:\bText\(\s*(?:text\s*=\s*)?{re.escape(variable)}\b|'
                rf'\b(?:title|subtitle|description|label|text|message)\s*=\s*{re.escape(variable)}\b)'
            )
            if not usage.search(source):
                continue
            opening = source.find("{", variable_match.end())
            if opening < 0:
                continue
            depth = 1
            in_string = False
            escaped = False
            closing = opening + 1
            while closing < len(source) and depth:
                char = source[closing]
                if in_string:
                    if escaped:
                        escaped = False
                    elif char == "\\":
                        escaped = True
                    elif char == '"':
                        in_string = False
                elif char == '"':
                    in_string = True
                elif char == "{":
                    depth += 1
                elif char == "}":
                    depth -= 1
                closing += 1
            block = source[opening + 1:closing - 1]
            for phrase_match in literal.finditer(block):
                try:
                    phrase = json.loads('"' + phrase_match.group(1) + '"')
                except json.JSONDecodeError:
                    phrase = phrase_match.group(1)
                line = source.count("\n", 0, opening + phrase_match.start()) + 1
                if "$" not in phrase:
                    if len(phrase) > 2 and re.search(r"[A-Za-zÀ-ÿ]{3,}", phrase) and phrase not in mapped:
                        errors.append(f"Compose displayed state text is not mapped at {path.relative_to(ROOT)}:{line}: {phrase[:100]}")
                    continue
                sample = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*", "1", phrase)
                if any(pattern.fullmatch(sample) for pattern in patterns):
                    continue
                errors.append(f"Interpolated Compose state text has no locale pattern at {path.relative_to(ROOT)}:{line}: {phrase[:100]}")

    metadata_catalog = json.loads((ROOT / "tools" / "tunable_metadata_translations.json").read_text(encoding="utf-8"))
    metadata_paths = (
        source_root / "data" / "LynxDeepTunableMetadata.kt",
        source_root / "data" / "LynxDeepTunableRepository.kt",
    )
    metadata_fields = re.compile(r'\b(?:name|desc|category|rec|help)\s*=\s*"((?:\\.|[^"\\])*)"')
    option_label = re.compile(r'TunableOption\(\s*"(?:\\.|[^"\\])*"\s*,\s*"((?:\\.|[^"\\])*)"')
    for path in metadata_paths:
        source = path.read_text(encoding="utf-8")
        if path.name == "LynxDeepTunableMetadata.kt":
            metadata_literals = [*metadata_fields.finditer(source)]
            for block in re.finditer(r'\b(?:name|desc|rec)\s*=\s*when\s*\([^)]*\)\s*\{([\s\S]*?)\n\s*\}', source):
                metadata_literals.extend(re.finditer(r'->\s*"((?:\\.|[^"\\])*)"', block.group(1)))
            for line in re.finditer(r'^\s*(?:name|desc|rec)\s*=\s*if\b[^\n]*', source, re.M):
                metadata_literals.extend(re.finditer(r'"((?:\\.|[^"\\])*)"', line.group(0)))
        else:
            metadata_literals = [
                *re.finditer(r'name\s*=\s*"(PPM Policy (?:\\.|[^"\\])*)"', source),
                *re.finditer(r'help\s*=\s*"(Kebijakan PPM MediaTek(?:\\.|[^"\\])*)"', source),
            ]
        metadata_literals.extend(option_label.finditer(source))
        for match in metadata_literals:
            try:
                value = json.loads('"' + match.group(1) + '"')
            except json.JSONDecodeError:
                value = match.group(1).replace(r'\"', '"').replace(r'\\', '\\')
            line = source.count("\n", 0, match.start()) + 1
            if "$" in value:
                if '"' in value or "split(" in value:
                    continue
                sample = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*", "1", value)
                if not any(pattern.fullmatch(sample) for pattern in patterns):
                    errors.append(f"Dynamic tunable metadata has no locale pattern at {path.relative_to(ROOT)}:{line}: {value[:100]}")
            elif value not in mapped:
                errors.append(f"Tunable metadata text is not mapped to locale resources at {path.relative_to(ROOT)}:{line}: {value[:100]}")
    for phrase in metadata_catalog:
        if "$" not in phrase and phrase not in mapped:
            errors.append(f"Tunable metadata catalog phrase has no Compose resource mapping: {phrase[:100]}")
    return errors


def check_feedback_catalog_coverage() -> list[str]:
    """Ensure static ViewModel feedback strings are mapped to a locale resource."""
    errors: list[str] = []
    source_root = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx" / "ui"
    mapping_path = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx" / "core" / "locale" / "LegacyUiTextResources.kt"
    mapping_source = mapping_path.read_text(encoding="utf-8")
    mapped: set[str] = set()
    for match in re.finditer(r'^\s*"((?:\\.|[^"\\])*)"\s+to\s+R\.string\.', mapping_source, re.M):
        try:
            mapped.add(json.loads('"' + match.group(1) + '"'))
        except json.JSONDecodeError:
            continue

    literal_pattern = re.compile(r'"((?:\\.|[^"\\])*)"')
    for path in source_root.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        for line_number, line in enumerate(source.splitlines(), start=1):
            if "successMessage" not in line and "errorMessage" not in line:
                continue
            for match in literal_pattern.finditer(line):
                try:
                    phrase = json.loads('"' + match.group(1) + '"').strip()
                except json.JSONDecodeError:
                    continue
                if "$" in phrase or len(phrase) <= 2 or phrase in {"else", "Error", "apply_on_boot"}:
                    continue
                if phrase not in mapped:
                    errors.append(f"Static ViewModel feedback is not mapped to locale resources at {path.relative_to(ROOT)}:{line_number}: {phrase}")
    return errors


def check_dynamic_feedback_coverage() -> list[str]:
    """Require each interpolated toast message to have a runtime translation pattern."""
    errors: list[str] = []
    source_root = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx" / "ui"
    bridge = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx" / "core" / "locale" / "LynxText.kt"
    bridge_source = bridge.read_text(encoding="utf-8")
    regex_literals = re.findall(r'DynamicUiText\(Regex\("((?:\\.|[^"\\])*)"\),\s*R\.string\.', bridge_source)
    patterns: list[str] = []
    for literal in regex_literals:
        try:
            patterns.append(re.sub(r"\\(.)", r"\1", json.loads('"' + literal + '"')))
        except json.JSONDecodeError:
            continue

    kotlin_string = re.compile(r'"((?:\\.|[^"\\])*)"')
    for path in source_root.rglob("*.kt"):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if ("successMessage" not in line and "errorMessage" not in line) or "$" not in line or "R.string." in line:
                continue
            for match in kotlin_string.finditer(line):
                raw = match.group(1)
                if "$" not in raw:
                    continue
                try:
                    template = json.loads('"' + raw + '"')
                except json.JSONDecodeError:
                    template = raw.replace(r"\"", '"').replace(r"\\", "\\")
                prefix = template.split("$", 1)[0].strip()
                if not prefix:
                    continue
                if not any(pattern.startswith("^" + prefix) or ("|" in pattern and any(part.startswith("^" + prefix) for part in pattern.split("|"))) for pattern in patterns):
                    errors.append(f"Dynamic ViewModel feedback has no locale pattern at {path.relative_to(ROOT)}:{line_number}: {prefix}")
    return errors


def check_english_comments() -> list[str]:
    """Catch common Indonesian words in full-line source comments and script templates."""
    errors: list[str] = []
    for source_root in (*COMMENT_ROOTS, *ROOT_SCRIPT_SOURCES):
        if not source_root.is_dir():
            continue
        for path in source_root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in COMMENT_EXTENSIONS:
                continue
            try:
                lines = path.read_text(encoding="utf-8").splitlines()
            except (OSError, UnicodeDecodeError):
                continue
            for line_number, line in enumerate(lines, start=1):
                match = COMMENT_LINE.match(line)
                if match:
                    if path.suffix.lower() == ".py" and line.lstrip().startswith("*"):
                        continue
                    # Rust attributes such as #[derive(...)] are code, not comments.
                    if line.lstrip().startswith("#["):
                        continue
                    comment_text = re.sub(r"(['\"]).*?\1", "", match.group(1))
                else:
                    # Also inspect trailing comments, while ignoring marker-like text in literals.
                    code = re.sub(r"\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`", "\"\"", line)
                    inline_markers = r"//+|#+|/\*+|<!--"
                    if path.suffix.lower() in {".sql"}:
                        inline_markers += r"|--"
                    if path.suffix.lower() in {".ini", ".cfg", ".conf"}:
                        inline_markers += r"|;"
                    inline = re.search(rf"\s({inline_markers})\s?(.*)$", code)
                    if not inline:
                        continue
                    comment_text = inline.group(2).split("*/", 1)[0].split("-->", 1)[0]
                if INDONESIAN_COMMENT_MARKERS.search(comment_text) or INDONESIAN_COMMENT_EXTRA.search(comment_text):
                    errors.append(f"Non-English source comment at {path.relative_to(ROOT)}:{line_number}")
    return errors


def check_shell() -> list[str]:
    errors: list[str] = []
    source = SHELL_LOCALE.read_text(encoding="utf-8")
    branches = source.split('if [ "$locale" = "en" ]; then', 1)
    if len(branches) != 2:
        return ["Shell locale helper does not contain ID/EN branches"]
    english, indonesian = branches[1].split("\n    else\n", 1)
    key_pattern = re.compile(r'^\s*([a-z][a-z0-9_]*)\)\s', re.M)
    en_keys = set(key_pattern.findall(english))
    id_keys = set(key_pattern.findall(indonesian))
    if en_keys != id_keys:
        errors.append(f"Shell locale key mismatch: missing-en={sorted(id_keys - en_keys)}, extra-en={sorted(en_keys - id_keys)}")
    calls = set(re.findall(r'lynx_t\s+([a-z][a-z0-9_]*)', INSTALLER.read_text(encoding="utf-8")))
    for key in sorted(calls - en_keys):
        errors.append(f"Installer uses an untranslated shell locale key: {key}")
    return errors


def check_cli_locale_precedence() -> list[str]:
    """Require the native CLI to prefer Android's active system locale."""
    source = (ROOT / "native" / "lynxd" / "src" / "subsystems" / "tui.rs").read_text(encoding="utf-8")
    function = source.split("pub(crate) fn is_english()", 1)
    if len(function) != 2:
        return ["Native CLI locale helper is missing"]
    body = function[1].split("\npub(crate) fn ui", 1)[0]
    system_locale = body.find('"persist.sys.locale"')
    product_locale = body.find('"ro.product.locale"')
    environment_locale = body.find('std::env::var("LANG")')
    if min(system_locale, product_locale, environment_locale) < 0 or not system_locale < product_locale < environment_locale:
        return ["Native CLI locale must prefer persist.sys.locale, then ro.product.locale, then LANG"]
    return []


def main() -> int:
    try:
        errors = check_android() + check_freeform_android() + check_web() + check_media_decoder_locales() + check_shell() + check_cli_locale_precedence() + check_android_accessibility() + check_android_notification_text() + check_android_notification_helpers() + check_custom_script_output_localization() + check_compose_raw_text_imports() + check_compose_catalog_mappings() + check_compose_visible_text_coverage() + check_feedback_catalog_coverage() + check_dynamic_feedback_coverage() + check_english_comments()
    except (OSError, ET.ParseError, ValueError) as exc:
        print(f"Locale validation error: {exc}", file=sys.stderr)
        return 2
    if errors:
        print("Locale validation failed:")
        for error in errors:
            print(f" - {error}")
        return 1
    print("Android/WebUI catalogs, Compose and WebUI parameter metadata, generated HTML, accessibility labels, shell keys, and English comments: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
