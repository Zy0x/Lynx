#!/usr/bin/env python3
"""Validate the shared media feature catalog, generated text, and referenced keys."""
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def check_media_decoder_locales():
    entries = json.loads((ROOT / "tools/media_decoder_translations.json").read_text(encoding="utf-8"))
    raw = (ROOT / "webroot/locale-data.js").read_text(encoding="utf-8")
    web = json.loads(raw.split("=", 1)[1].strip().removesuffix(";"))
    errors = []
    for key, entry in entries.items():
        expected = sorted(entry.get("parameters", []))
        for locale in ["id", "en"]:
            if sorted(re.findall(r"\{(\w+)\}", entry[locale])) != expected:
                errors.append(f"Media named placeholders differ for {key}/{locale}")
            if web[locale].get(key) != entry[locale]:
                errors.append(f"Media WebUI catalog differs from reviewed text: {key}/{locale}")
    for folder, locale in [("values", "id"), ("values-id", "id"), ("values-en", "en")]:
        catalog = ET.parse(ROOT / "LynxCompanion/app/src/main/res" / folder / "strings.xml").getroot()
        by_name = {item.attrib.get("name"): item for item in catalog}
        for key, entry in entries.items():
            if entry.get("web_only"):
                continue
            item = by_name.get(key)
            if item is None:
                errors.append(f"Missing media Android key {key}/{locale}")
                continue
            variants = list(item) if entry.get("plural") else [item]
            for variant in variants:
                source = entry.get("en_one", entry[locale]) if locale == "en" and variant.attrib.get("quantity") == "one" else entry[locale]
                for index, name in enumerate(entry.get("parameters", []), 1):
                    source = source.replace("{" + name + "}", f"%{index}${'d' if name == 'count' else 's'}")
                actual = "".join(variant.itertext()).replace("\\'", "'")
                if actual != source:
                    errors.append(f"Media Android catalog differs from reviewed text: {key}/{locale}")
    paths = list((ROOT / "LynxCompanion/app/src/main/java/com/noir/lynx/feature/media").rglob("*.kt"))
    paths.append(ROOT / "webroot/media-decoder.js")
    for path in paths:
        for key in re.findall(r"\bmedia_[a-z_]+\b", path.read_text(encoding="utf-8")):
            if key not in entries:
                errors.append(f"Unknown media locale key {key} in {path.relative_to(ROOT)}")
    return sorted(set(errors))


if __name__ == "__main__":
    problems = check_media_decoder_locales()
    if problems:
        print("\n".join(problems))
        raise SystemExit(1)
    print("Media decoder ID/EN catalogs, placeholders, plural text, and references: OK")
