#!/usr/bin/env python3
"""Downloads the built-in blocker extensions from addons.mozilla.org, unpacks them into
app/src/main/assets/extensions/<dir>/ and writes extensions.json (read by the app at startup).

Edit EXTENSIONS to add/remove built-ins. Run from anywhere:  python3 scripts/fetch_extensions.py
Exit code is 1 if a required extension failed to download (so CI stops).
"""
import io, json, os, shutil, sys, urllib.request, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "extensions")

# dir name in assets  ->  slug on addons.mozilla.org     (order = order shown in the app)
EXTENSIONS = {
    "ublock-origin": "ublock-origin",                 # ad + tracker blocking (the main engine)
    "clearurls": "clearurls",                         # strips tracking parameters, redirect wrappers
    "skip-redirect": "skip-redirect",                 # skips intermediate redirect/short-link pages
    "cookies-dont-care": "istilldontcareaboutcookies",# hides cookie banners
    # "privacy-badger": "privacy-badger17",           # optional, overlaps with uBlock Origin
}
ALWAYS = ["automation"]  # shipped inside the repo, never downloaded


def download(slug):
    url = f"https://addons.mozilla.org/firefox/downloads/latest/{slug}/latest.xpi"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (SwiftGecko build)"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def gecko_id(manifest):
    for key in ("browser_specific_settings", "applications"):
        gid = manifest.get(key, {}).get("gecko", {}).get("id")
        if gid:
            return gid
    return None


def main():
    os.makedirs(OUT, exist_ok=True)
    failed = []
    for d, slug in EXTENSIONS.items():
        target = os.path.join(OUT, d)
        try:
            data = download(slug)
            if os.path.isdir(target):
                shutil.rmtree(target)
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                z.extractall(target)
            mpath = os.path.join(target, "manifest.json")
            with open(mpath, encoding="utf-8") as f:
                manifest = json.load(f)
            if not gecko_id(manifest):          # built-ins need an id
                manifest.setdefault("browser_specific_settings", {}).setdefault("gecko", {})["id"] = f"{d}@swift.local"
                with open(mpath, "w", encoding="utf-8") as f:
                    json.dump(manifest, f)
            print(f"ok   {d:18s} {manifest.get('name')} {manifest.get('version')}")
        except Exception as e:
            print(f"FAIL {d}: {e}", file=sys.stderr)
            failed.append(d)

    entries = []
    for d in list(EXTENSIONS) + ALWAYS:
        mpath = os.path.join(OUT, d, "manifest.json")
        if not os.path.isfile(mpath):
            continue
        with open(mpath, encoding="utf-8") as f:
            gid = gecko_id(json.load(f))
        entries.append({"dir": d, "id": gid})
    with open(os.path.join(OUT, "extensions.json"), "w", encoding="utf-8") as f:
        json.dump(entries, f, indent=1)
    print("extensions.json:", [e["dir"] for e in entries])
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
