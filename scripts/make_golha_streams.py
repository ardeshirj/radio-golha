"""Build app/src/main/assets/golhaprogram.json: the Golha programmes streamed from golhaprogram.com.

golhaprogram.com is a community site (source: github.com/golhaprogram/golhaprogram-website) that offers every
programme as an MP3 to play or download. This reads the programme list from a checkout of that repository (the
front matter of content/programs/<series>/<number>.md) and writes one stream per programme, with the names in
Persian as the site gives them.

    python3 -I scripts/make_golha_streams.py <repo checkout> [--available urls.txt] > app/src/main/assets/golhaprogram.json

--available: a file of URLs known to exist (one per line); programmes not in it are left out. The site lists a few
programmes whose MP3 isn't on its server (yet).
"""
import json
import os
import sys

AUDIO_HOST = "https://music.golhaprogram.com"
SERIES = {  # folder -> (file prefix on the audio host, program name); from layouts/partials/audio-url.html
    "golhaye-rangarang": ("GR", "گل‌های رنگارنگ"),
    "golhaye-tazeh": ("GT", "گل‌های تازه"),
    "golhaye-javidan": ("GJ", "گل‌های جاویدان"),
    "yek-shakhe-gol": ("YSG", "یک شاخه گل"),
    "golhaye-sahraee": ("GS", "گل‌های صحرایی"),
}

# Mokhalef is a part of Segah, not a dastgah of its own; one programme lists it next to Segah.
NOT_A_DASTGAH = {"مخالف"}


def persian(text):
    """Spell with Persian letters: the site sometimes uses the Arabic yeh and kaf (e.g. شجريان)."""
    return text.replace("ي", "ی").replace("ك", "ک").strip()


def front_matter(path):
    """The simple `key: value` / `key: [a, b]` lines between the first two `---` lines."""
    lines = [line.rstrip() for line in open(path, encoding="utf-8").read().lstrip("﻿").splitlines()]
    if not lines or lines[0] != "---":
        return {}
    fields = {}
    for line in lines[1:lines.index("---", 1)]:
        key, _, value = line.partition(":")
        value = value.strip()
        if value.startswith("["):
            fields[key.strip()] = [v.strip().strip("'\"") for v in value.strip("[]").split(",") if v.strip().strip("'\"")]
        else:
            fields[key.strip()] = value.strip("'\"")
    return fields


def as_list(value):
    """A tag that may be one value, a list, or several values separated by a Persian or Latin comma."""
    items = value if isinstance(value, list) else [value or ""]
    return [part.strip() for item in items for part in item.replace("،", ",").split(",") if part.strip()]


def persian_names(value, leave_out=()):
    out = []
    for name in map(persian, as_list(value)):
        if name and name not in leave_out and name not in out:
            out.append(name)
    return out


def main():
    args = sys.argv[1:]
    available = None
    if "--available" in args:
        i = args.index("--available")
        available = set(open(args[i + 1]).read().split())
        del args[i:i + 2]
    root = os.path.join(args[0], "content", "programs")
    streams = []
    for folder, (prefix, program) in SERIES.items():
        directory = os.path.join(root, folder)
        names = sorted(os.listdir(directory)) if os.path.isdir(directory) else []
        for name in names:
            if name == "_index.md" or not name.endswith(".md"):
                continue
            path = os.path.join(directory, name)
            fields = front_matter(path)
            if not fields or fields.get("draft") == "true":
                continue
            number = name[:-3]  # in Latin letters, as in the file names: 200B
            url = f"{AUDIO_HOST}/{prefix}_{number}.mp3"
            if available is not None and url not in available:
                continue
            streams.append({
                "url": url,
                "title": f"{program} {persian(fields.get('program_number') or number)}",
                "singer": persian_names(fields.get("singers")),
                "dastgah": persian_names(fields.get("dastgah"), leave_out=NOT_A_DASTGAH),
                "program": program,
                "page": f"https://golhaprogram.com/programs/{folder}/{number}/",
            })
    streams.sort(key=lambda s: (s["program"], int("".join(c for c in s["url"].rsplit("_", 1)[-1] if c.isdigit()) or 0), s["url"]))
    json.dump(streams, sys.stdout, ensure_ascii=False, indent=1)
    print()
    print(f"{len(streams)} programmes", file=sys.stderr)


if __name__ == "__main__":
    main()
