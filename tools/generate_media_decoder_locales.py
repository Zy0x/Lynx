#!/usr/bin/env python3
"""Update the media feature's Android and WebUI catalogs from reviewed ID/EN text."""
import json
import re
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
ENTRIES = json.loads((ROOT / "tools/media_decoder_translations.json").read_text(encoding="utf-8"))


def android_text(entry, locale):
    value = entry[locale]
    for index, name in enumerate(entry.get("parameters", []), 1):
        value = value.replace("{" + name + "}", f"%{index}${'d' if name == 'count' else 's'}")
    return escape(value).replace("'", "\\'")


for folder, locale in [("values", "id"), ("values-id", "id"), ("values-en", "en")]:
    path = ROOT / "LynxCompanion/app/src/main/res" / folder / "strings.xml"
    content = path.read_text(encoding="utf-8")
    additions = []
    for key, entry in ENTRIES.items():
        if entry.get("web_only"):
            continue
        pattern = rf'\s*<(string|plurals) name="{re.escape(key)}"[^>]*>.*?</\1>'
        content = re.sub(pattern, "", content, flags=re.S)
        value = android_text(entry, locale)
        if entry.get("plural"):
            quantities = ["one", "other"]
            items = "\n".join(f'        <item quantity="{quantity}">{android_text(entry, "en_one") if locale == "en" and quantity == "one" else value}</item>' for quantity in quantities)
            additions.append(f'    <plurals name="{key}">\n{items}\n    </plurals>')
        else:
            additions.append(f'    <string name="{key}">{value}</string>')
    content = content.replace("</resources>", "\n".join(additions) + "\n</resources>")
    path.write_text(content, encoding="utf-8")

path = ROOT / "webroot/locale-data.js"
raw = path.read_text(encoding="utf-8")
data = json.loads(raw.split("=", 1)[1].strip().removesuffix(";"))
for key, entry in ENTRIES.items():
    data["sources"][key] = entry["id"]
    for locale in ["id", "en"]:
        data[locale][key] = entry[locale]
path.write_text("/* Stable token catalogs for Indonesian and English WebUI. */\nwindow.LYNX_LOCALE_DATA = "
    + json.dumps(data, ensure_ascii=False, separators=(",", ":")) + ";\n", encoding="utf-8")
print(f"Updated {len(ENTRIES)} reviewed media locale entries.")
