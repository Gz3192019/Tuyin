# -*- coding: utf-8 -*-
"""图隐 APK 完整构建脚本（原生版，无 WebView）。
用法: python build_app.py
"""
import glob
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(ROOT, 'app')
TOOLS = r'D:\files\Desk\work\tank-github\rac-hide-main\android-tools'
JDK = os.path.join(TOOLS, 'jdk', 'jdk-17.0.2', 'bin')
AAPT = os.path.join(TOOLS, 'android-14', 'aapt.exe')
D8 = os.path.join(TOOLS, 'android-14', 'd8.bat')
ZIPALIGN = os.path.join(TOOLS, 'android-14', 'zipalign.exe')
APKSIGNER = os.path.join(TOOLS, 'android-14', 'apksigner.bat')
ANDROID_JAR = os.path.join(TOOLS, 'android-34-ext12', 'android.jar')
KEYSTORE = os.path.join(APP, 'rac-hide.keystore')

BUILD = os.path.join(APP, 'build')
GEN = os.path.join(BUILD, 'gen')
CLASSES = os.path.join(BUILD, 'classes')
DEX = os.path.join(BUILD, 'dex')
MANIFEST = os.path.join(APP, 'AndroidManifest.xml')
RES = os.path.join(APP, 'res')
ASSETS = os.path.join(APP, 'assets')

OUT_UNSIGNED = os.path.join(BUILD, 'app-unsigned.apk')
OUT_WITHDEX = os.path.join(BUILD, 'app-withdex.apk')
OUT_ALIGNED = os.path.join(BUILD, 'app-aligned.apk')
OUT_FINAL = os.path.join(BUILD, 'Tuyin-1.0.apk')


def run(cmd, cwd=None):
    print('>>>', ' '.join(cmd))
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.stdout:
        print(r.stdout[:4000])
    if r.returncode != 0:
        print('!!! STDERR:\n' + (r.stderr or '')[:4000])
        sys.exit(r.returncode)


def main():
    # 0. 清理
    for d in (GEN, CLASSES, DEX):
        if os.path.isdir(d):
            shutil.rmtree(d)
    for f in (OUT_UNSIGNED, OUT_WITHDEX, OUT_ALIGNED, OUT_FINAL):
        if os.path.exists(f):
            os.remove(f)
    os.makedirs(GEN, exist_ok=True)
    os.makedirs(CLASSES, exist_ok=True)
    os.makedirs(DEX, exist_ok=True)

    # 1. aapt package
    aapt_cmd = [AAPT, 'package', '-f', '-M', MANIFEST, '-S', RES,
                '-A', ASSETS, '-I', ANDROID_JAR, '-F', OUT_UNSIGNED, '-J', GEN]
    run(aapt_cmd)

    # 2. javac
    src_files = glob.glob(os.path.join(APP, 'src', '*.java'))
    r_java = os.path.join(GEN, 'R.java')
    javac = [os.path.join(JDK, 'javac.exe'), '-encoding', 'UTF-8',
             '-classpath', ANDROID_JAR, '-d', CLASSES, r_java] + src_files
    run(javac)

    # 3. d8
    class_files = []
    for root_dir, _, files in os.walk(CLASSES):
        for f in files:
            if f.endswith('.class'):
                class_files.append(os.path.join(root_dir, f))
    d8_cmd = [D8, '--lib', ANDROID_JAR, '--min-api', '24', '--output', DEX] + class_files
    run(d8_cmd)

    # 4. add_dex.py（产出 app-withdex.apk）
    run([sys.executable, os.path.join(ROOT, 'add_dex.py')])

    # 5. zipalign + apksigner
    run([ZIPALIGN, '-f', '4', OUT_WITHDEX, OUT_ALIGNED])
    run([APKSIGNER, 'sign', '--ks', KEYSTORE, '--ks-pass', 'pass:rachide2026',
         '--ks-key-alias', 'rachide', '--out', OUT_FINAL, OUT_ALIGNED])

    # 6. 验证
    run([sys.executable, os.path.join(ROOT, 'apk_check2.py')])
    run([AAPT, 'dump', 'badging', OUT_FINAL])
    run([APKSIGNER, 'verify', OUT_FINAL])
    print('BUILD OK:', OUT_FINAL, os.path.getsize(OUT_FINAL), 'bytes')


if __name__ == '__main__':
    main()
