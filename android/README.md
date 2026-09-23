# 图隐 · Android 独立应用（原生版，本地构建）

「图隐」——把一张图藏进另一张图的独立安卓应用，MIUI X 风格原生界面。
**无 WebView、纯原生组件**，嵌入/提取算法由 Java 完整实现
（RAC-Hide 鲁棒 DCT 隐写，抗 JPEG 重压缩与等比缩放），与上游 JS 实现互通：
用本应用嵌入的图，可在原版工具/网页中提取，反之亦然。
**全程本地计算，不上传任何图片。**

- 原生 MIUI X 风格：瓷白底（非纯白）、圆角卡片+投影、上传框带图标引导、底部悬浮导航（未选中项半透明）
- 原生「关于」页（分组列表）：应用图标、应用名 **图隐**、版本、开发者、当前系统信息、开源项目（致敬原作者 GitHub）
- 嵌入页：封面/秘密图双上传框、可用容量条、封面增强（长边上限）、画质与容量滑块（容量优先/均衡/抗压缩）、自定义参数（ppb/repeat/nsym）、开始嵌入/保存隐写图/重置、封面·秘密图·隐写结果预览
- 提取页：隐写图上传、原图尺寸（可选，用于恢复缩放；也可自动从空间标尺恢复）、提取秘密图/保存到相册/重置、隐写图·提取结果预览
- 系统图片选择器选图；导出图片自动存入系统相册（Pictures/图隐）
- 明暗模式：跟随系统（values-night），无需手动切换

## 成品

- 根目录 `Tuyin-1.0.apk`（约 113 KB，签名 v2+v3）
  - 包名 `com.tuopzf.tuyin`，应用名 **图隐**，minSdk 24（Android 7.0+），targetSdk 34
  - 权限：读取图片（Android 13+ 用 READ_MEDIA_IMAGES，7-12 用 READ_EXTERNAL_STORAGE）与保存图片到相册（Android 9 及以下用 WRITE_EXTERNAL_STORAGE）；不联网、不上传任何图片

## 安装到手机

1. 把 `Tuyin-1.0.apk` 传到安卓手机（数据线 / 微信 / QQ 均可）。
2. 点击安装；若提示"未知来源"，在系统设置里允许该来源（或选择"仍要安装"）。
3. 打开即进入工作台：嵌入 / 提取全部可用；右上角「关于」查看应用信息。

> 注意：APK 用本地自签名密钥（`android/app/rac-hide.keystore`，密码
> `rachide2026`）签署，系统可能提示"未受信任"，这是自签名应用的正常提示；
> 请勿将该密钥用于正式分发。

## 重新构建

一键构建（推荐）：`python android/build_app.py`（依次执行
aapt → javac → d8 → 写入 dex → zipalign → apksigner → 验证）。

工具链在 `android-tools/`（JDK 17 + build-tools 34 + android-34 platform，
约 500 MB，不需要时整个目录可删）。

## 工程结构

```
android/
  build_app.py               一键构建脚本（aapt/javac/d8/dex/align/sign/verify）
  prep_assets.py             旧 WebView 版资产打包器（仓库保留，原生版构建不依赖）
  add_dex.py                 把 classes.dex 写入未签名 APK
  gen_tuyin_icon.py          生成「隐」字启动图标
  app/
    AndroidManifest.xml      清单（包名 com.tuopzf.tuyin / 应用名 图隐 / 图片读写权限）
    src/MainActivity.java    原生工作台（标题栏+嵌入/提取面板+悬浮导航，全部原生组件）
    src/AboutActivity.java   关于页（分组列表：图标 / 应用名 / 版本 / 开发者 / 系统信息 / 致敬原作者）
    src/RacConstants.java    DCT 块、魔数 STG1、系数对表、默认参数（constants.js）
    src/RacDct.java          8x8 II 型 DCT/IDCT（dct.js）
    src/RacColor.java        BT.601 亮度/重建 RGB（color.js）
    src/RacBits.java         位/字节转换（bits.js）
    src/RacInterleave.java   全局交织（interleave.js）
    src/RacReedSolomon.java  GF(256) RS 编解码（reed-solomon.js）
    src/RacRuler.java        空间标尺，恢复原始尺寸（ruler.js）
    src/RacCore.java         载荷组装/嵌入/提取，暴力搜参数（core.js）
    src/RacImages.java       解码/缩放/JPEG 阶梯压缩/相册保存（browser.js 图片助手）
    res/values*/             浅色 + 深色主题、颜色、文案（app_name=图隐）
    res/drawable/            启动图标（隐字标）
    build/                   构建产物与最终 APK
```
