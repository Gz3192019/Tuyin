# -*- coding: utf-8 -*-
"""Build android/app/assets/index.html from the committed gui/ sources.

Turns the ES-module workbench (gui/index.html + gui/style.css + gui/app.js and
the src/*.js modules) into one self-contained page for the WebView: the CSS is
inlined into <style>, every src module and app.js are concatenated into a single
inline <script type="module"> (no imports), and the PWA-only <link> tags are
dropped.  Works from any checkout of the repo (paths are resolved relative to
this file), so a fresh clone can build the APK without extra assets.
"""
import io
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GUI = os.path.join(ROOT, "gui")
SRC = os.path.join(ROOT, "src")
APK = os.path.join(HERE, "app")

# Module concatenation order (dependency order, same as the hand-built bundle).
SRC_ORDER = [
    "constants.js",
    "bits.js",
    "dct.js",
    "interleave.js",
    "reed-solomon.js",
    "color.js",
    "ruler.js",
    "core.js",
    "browser.js",
]

# The import block at the top of gui/app.js, replaced by inline modules.
APP_IMPORT_RE = re.compile(
    r"import\s*\{[^}]*\}\s*from\s*'\.\./src/[^']*';\s*"
    r"import\s*\{[^}]*\}\s*from\s*'\.\./src/[^']*';\s*"
)


def build():
    with io.open(os.path.join(GUI, "index.html"), encoding="utf-8") as f:
        html = f.read()

    with io.open(os.path.join(GUI, "style.css"), encoding="utf-8") as f:
        css = f.read()

    # 1. Inline the stylesheet.
    html = html.replace(
        '<link rel="stylesheet" href="style.css">',
        "<style>\n" + css + "\n</style>",
    )

    # 2. Concatenate src modules + app.js into one inline module script.
    #    `export` keywords and cross-module `import ... from` statements are
    #    stripped so the inline script stays self-contained.
    def strip_exports(text):
        text = re.sub(r"import\s*\{[^}]*\}\s*from\s*'[^']*';\s*", "", text)
        out = []
        for line in text.splitlines(True):
            if line.startswith("export "):
                line = line[len("export "):]
            out.append(line)
        return "".join(out)

    parts = []
    for name in SRC_ORDER:
        with io.open(os.path.join(SRC, name), encoding="utf-8") as f:
            parts.append("/* ---- src/%s ---- */\n%s" % (name, strip_exports(f.read())))
    with io.open(os.path.join(GUI, "app.js"), encoding="utf-8") as f:
        app = f.read()
    app = APP_IMPORT_RE.sub("", app, count=1)
    parts.append("/* ---- app.js ---- */\n" + app.lstrip("\n"))

    module_script = '<script type="module">\n' + "".join(parts) + "\n</script>"
    html = html.replace('<script type="module" src="app.js"></script>', module_script)

    # 3. Drop PWA-only links (no manifest / apple-touch-icon inside the WebView).
    html = re.sub(r'<link rel="manifest"[^>]*>\s*', "", html)
    html = re.sub(r'<link rel="apple-touch-icon"[^>]*>\s*', "", html)

    # 4. Write assets/index.html.
    os.makedirs(os.path.join(APK, "assets"), exist_ok=True)
    out_path = os.path.join(APK, "assets", "index.html")
    with io.open(out_path, "w", encoding="utf-8", newline="") as f:
        f.write(html)
    print("assets/index.html bytes:", os.path.getsize(out_path))


if __name__ == "__main__":
    build()
