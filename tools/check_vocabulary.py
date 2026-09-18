#!/usr/bin/env python3
"""Binding vocabulary guard for the generated string catalogue (mirrors the iOS
and Django guards).

Rule (docs/API_CONTRACT.md "Binding vocabulary"): the partner-to-partner written
channel is "yazışma" — never "sohbet" (TR), never "chat" (EN). What is written
there counts as evidence under the order contract; "sohbet" reads like small
talk. Jowi's own screen is not a yazışma: its record is the "geçmiş".

Key NAMES, API fields (chatUrl, chatInvites) and URL paths (/sohbet/) are not
user-visible text and are left alone — only values are checked.

Run after `python3 tools/generate_strings.py`, before committing.
"""
import re
import sys
import pathlib
import xml.etree.ElementTree as ET

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"
FILES = {
    "tr": [RES / "values-tr/strings_catalog.xml", RES / "values-tr/strings.xml"],
    "en": [RES / "values/strings_catalog.xml", RES / "values/strings.xml"],
}
BANNED = {"tr": [r"sohbet"], "en": [r"\bchats?\b"]}

checked, problems = 0, []
for lang, paths in FILES.items():
    for path in paths:
        if not path.exists():
            continue
        for node in ET.parse(path).getroot().iter("string"):
            checked += 1
            value = "".join(node.itertext())
            for pat in BANNED[lang]:
                if re.search(pat, value, re.I):
                    problems.append(f"{path.name}:{node.get('name')} [{lang}]: {value!r} matches /{pat}/")

print(f"{checked} values checked, {len(problems)} problem(s)")
for p in problems:
    print("  " + p)
sys.exit(1 if problems else 0)
