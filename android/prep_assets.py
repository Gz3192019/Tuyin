# -*- coding: utf-8 -*-
import io, os, shutil

root = r"D:\files\Desk\work\tank-github\rac-hide-main"
apk = os.path.join(root, "android", "app")
os.makedirs(os.path.join(apk, "assets"), exist_ok=True)
os.makedirs(os.path.join(apk, "res", "drawable"), exist_ok=True)

# 图标
shutil.copy2(os.path.join(root, "gui", "icons", "icon-512.png"),
             os.path.join(apk, "res", "drawable", "ic_launcher.png"))

# 自包含页面，剥掉 PWA 专用 link（WebView 内无 manifest/apple-touch-icon）
s = io.open(os.path.join(root, "gui", "_deploy", "rac-hide-app", "index.html"), encoding="utf-8").read()
keep = []
for ln in s.splitlines(True):
    if 'rel="manifest"' in ln or "apple-touch-icon" in ln:
        continue
    keep.append(ln)
out = "".join(keep)
io.open(os.path.join(apk, "assets", "index.html"), "w", encoding="utf-8", newline="").write(out)
print("assets/index.html bytes:", len(out))
