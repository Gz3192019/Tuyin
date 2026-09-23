# 图隐 · Android 独立应用（本地构建）

「图隐」——把一张图藏进另一张图的独立安卓应用，MIUI X 风格原生界面。
基于 RAC-Hide 鲁棒 DCT 隐写算法内核（零依赖、抗 JPEG 重压缩与等比缩放），
**全程本地计算，不上传任何图片**。

- 原生 MIUI X 风格壳：瓷白大标题主页、分段标签（嵌入/提取）、圆角卡片工作台、圆形「关于」入口
- 原生「关于」页（分组列表）：应用图标、应用名 **图隐**、版本、开发者、当前系统信息、开源项目（致敬原作者 GitHub）
- 工作台内核为单文件自包含页面（内联全部 JS/CSS），随 APK 打包，**离线可用**
- 系统图片选择器：点击工作台上传区直接调起系统相册选图；导出图片自动存入系统相册
- 明暗模式：原生壳跟随系统（values-night），工作台内可手动切换

## 成品

- 根目录 `Tuyin-1.0.apk`（约 78 KB，签名 v2+v3）
  - 包名 `com.tuopzf.tuyin`，应用名 **图隐**，minSdk 24（Android 7.0+），targetSdk 34
  - 权限：读取图片（Android 13+ 用 READ_MEDIA_IMAGES，7-12 用 READ_EXTERNAL_STORAGE）与保存图片到相册（Android 9 及以下用 WRITE_EXTERNAL_STORAGE）；不联网、不上传任何图片

## 安装到手机

1. 把 `Tuyin-1.0.apk` 传到安卓手机（数据线 / 微信 / QQ 均可）。
2. 点击安装；若提示"未知来源"，在系统设置里允许该来源（或选择"仍要安装"）。
3. 打开即进入工作台：嵌入 / 提取 / 通道模拟 / 明暗模式全部可用；右上角
   「关于」查看应用信息。

> 注意：APK 用本地自签名密钥（`android/app/rac-hide.keystore`，密码
> `rachide2026`）签署，系统可能提示"未受信任"，这是自签名应用的正常提示；
> 请勿将该密钥用于正式分发。

## 重新构建

工具链在 `android-tools/`（JDK 17 + build-tools 34 + android-34 platform，
约 500 MB，不需要时整个目录可删）。

```powershell
$bt = "D:\files\Desk\work\tank-github\rac-hide-main\android-tools"
$jdk = "$bt\jdk\jdk-17.0.2\bin"
$app = "D:\files\Desk\work\tank-github\rac-hide-main\android\app"

# 0. 页面更新后重新生成 assets/index.html
python android\prep_assets.py

# 1. aapt：打包资源/清单/资产 并生成 R.java
Set-Location $app
& "$bt\android-14\aapt.exe" package -f -M AndroidManifest.xml -S res -A assets `
  -I "$bt\android-34-ext12\android.jar" -F "$app\build\app-unsigned.apk" -J "$app\build\gen"

# 2. 编译
& "$jdk\javac.exe" -encoding UTF-8 -classpath "$bt\android-34-ext12\android.jar" `
  -d "$app\build\classes" "$app\build\gen\R.java" "$app\src\MainActivity.java" "$app\src\AboutActivity.java"

# 3. dex
$classFiles = (Get-ChildItem "$app\build\classes" -Recurse -Filter *.class | % { $_.FullName }) -join " "
& cmd /c "cd /d `"$app`" && `"$bt\android-14\d8.bat`" --lib `"$bt\android-34-ext12\android.jar`" `
  --min-api 24 --output `"$app\build\dex`" $classFiles"

# 4. 加入 classes.dex（python android\add_dex.py）+ 对齐 + 签名
& "$bt\android-14\zipalign.exe" -f 4 "$app\build\app-withdex.apk" "$app\build\app-aligned.apk"
& "$bt\android-14\apksigner.bat" sign --ks "$app\rac-hide.keystore" `
  --ks-pass pass:rachide2026 --key-pass pass:rachide2026 `
  --out "$app\build\Tuyin-1.0.apk" "$app\build\app-aligned.apk"
```

## 工程结构

```
android/
  app/
    AndroidManifest.xml       清单（包名 com.tuopzf.tuyin / 应用名 图隐 / 图片读写权限）
    src/MainActivity.java     MIUI X 主界面（瓷白主页：标题+标语+圆形关于入口+分段标签+圆角卡片 WebView+文件选择/相册保存）
    src/AboutActivity.java    关于页（分组列表：图标 / 应用名 / 版本 / 开发者 / 系统信息 / 致敬原作者）
    res/values*/              浅色 + 深色主题、颜色、文案（app_name=图隐）
    res/drawable/             圆角卡片、分段标签、启动图标（隐字标）
    assets/index.html         自包含工作台页面（构建时生成）
    build/                    构建产物与最终 APK
  prep_assets.py              从 gui/ 源码内联生成 assets/index.html（克隆后可重建）
  add_dex.py                  把 classes.dex 写入未签名 APK
  gen_tuyin_icon.py           生成「隐」字启动图标
```
