import zipfile, shutil, os

app = r"D:\files\Desk\work\tank-github\rac-hide-main\android\app\build"
src_apk = os.path.join(app, "app-unsigned.apk")
tmp = os.path.join(app, "app-withdex.apk")

# 把 classes.dex 追加进 APK（zip 追加条目）
shutil.copy2(src_apk, tmp)
with zipfile.ZipFile(tmp, "a") as zf:
    with open(os.path.join(app, "dex", "classes.dex"), "rb") as f:
        zf.writestr("classes.dex", f.read())
print("classes.dex added")
