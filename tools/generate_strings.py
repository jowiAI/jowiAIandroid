#!/usr/bin/env python3
"""Generate Android string resources from the iOS String Catalog.

Single source of truth: jowiAIs/Shared/Localizable.xcstrings (Xcode String
Catalog, plain JSON). This script writes, per language found in the catalog:

    app/src/main/res/values/strings_catalog.xml            (source language)
    app/src/main/res/values-<lang>/strings_catalog.xml     (other languages)
    app/src/main/res/xml/locales_config.xml                (Android 13+ per-app languages)

Key naming: camelCase catalog keys become snake_case resource names
(signInTitle -> sign_in_title). Android-only strings stay in the hand-kept
strings.xml files; never put a key there that the catalog also provides.

Usage:  python3 tools/generate_strings.py [path/to/Localizable.xcstrings]
"""

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_CATALOG = (
    REPO.parent.parent
    / "swiftUIProjects" / "jowiAIs" / "jowiAIs" / "Shared" / "Localizable.xcstrings"
)
RES = REPO / "app" / "src" / "main" / "res"

HEADER = (
    "<!-- GENERATED from Localizable.xcstrings — DO NOT EDIT.\n"
    "     Regenerate with: python3 tools/generate_strings.py -->\n"
)


def snake(key: str) -> str:
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", key)
    s = re.sub(r"[^A-Za-z0-9_]", "_", s)
    return s.lower()


def escape(value: str) -> str:
    v = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    v = v.replace("'", "\\'").replace('"', '\\"')
    v = v.replace("\n", "\\n")
    # iOS format specifiers -> Android (%@ becomes %s; positionalize if several)
    specs = re.findall(r"%(?:@|d|lld|ld|f|s)", v)
    if specs:
        def repl(m, counter=[0]):
            counter[0] += 1
            kind = "s" if m.group(0) == "%@" else m.group(0)[1:]
            kind = {"lld": "d", "ld": "d"}.get(kind, kind)
            return f"%{counter[0]}${kind}" if len(specs) > 1 else f"%{kind}"
        v = re.sub(r"%(?:@|d|lld|ld|f|s)", repl, v)
    if v.startswith("@") or v.startswith("?"):
        v = "\\" + v
    return v


def main() -> None:
    catalog_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_CATALOG
    data = json.loads(catalog_path.read_text(encoding="utf-8"))
    source_lang = data.get("sourceLanguage", "en")
    strings = data.get("strings", {})

    languages: set[str] = set()
    for entry in strings.values():
        languages.update((entry.get("localizations") or {}).keys())
    languages.add(source_lang)

    for lang in sorted(languages):
        lines = ['<?xml version="1.0" encoding="utf-8"?>', HEADER.rstrip(), "<resources>"]
        count = 0
        for key in sorted(strings.keys()):
            loc = (strings[key].get("localizations") or {}).get(lang)
            unit = (loc or {}).get("stringUnit") or {}
            value = unit.get("value")
            if value is None:
                continue  # missing -> falls back to the default resource
            lines.append(f'    <string name="{snake(key)}">{escape(value)}</string>')
            count += 1
        lines.append("</resources>")

        folder = RES / ("values" if lang == source_lang else f"values-{lang}")
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "strings_catalog.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"{folder.name}/strings_catalog.xml: {count} strings")

    xml_dir = RES / "xml"
    xml_dir.mkdir(parents=True, exist_ok=True)
    locales = "\n".join(
        f'    <locale android:name="{lang}" />' for lang in sorted(languages)
    )
    (xml_dir / "locales_config.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n' + HEADER +
        '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f"{locales}\n</locale-config>\n",
        encoding="utf-8",
    )
    print(f"xml/locales_config.xml: {sorted(languages)}")


if __name__ == "__main__":
    main()
