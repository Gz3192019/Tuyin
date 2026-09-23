package com.tuopzf.tuyin;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;

/** 图隐 — MIUI X 风格主界面：大标题 + 标语 + 圆形「关于」入口 + 分段标签 + 圆角卡片工作台。 */
public class MainActivity extends Activity {

    private static final int REQ_PICK_IMAGE = 1001;
    private static final int REQ_READ_PERM = 1002;
    private static final int REQ_WRITE_PERM = 1003;

    private WebView web;
    private TextView segEmbed;
    private TextView segExtract;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String pendingSaveDataUrl;
    private String pendingSaveName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int bg = getColor(R.color.tuyin_bg);
        int card = getColor(R.color.tuyin_card);
        int primary = getColor(R.color.tuyin_primary);
        int text = getColor(R.color.tuyin_text);
        int sub = getColor(R.color.tuyin_sub);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        // ---- 顶部：标题「图隐」+ 标语 + 圆形「关于」入口 ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.setMargins(dp(24), dp(20), dp(24), dp(14));
        header.setLayoutParams(hp);

        // 标题行：左「图隐」，右圆形关于按钮
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(text);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // 圆形「关于」入口（MIUI X 圆形描边图标按钮）
        TextView about = new TextView(this);
        about.setText("i");
        about.setTextSize(17);
        about.setTypeface(Typeface.DEFAULT_BOLD);
        about.setTextColor(primary);
        about.setGravity(android.view.Gravity.CENTER);
        GradientDrawable aboutBg = new GradientDrawable();
        aboutBg.setShape(GradientDrawable.OVAL);
        aboutBg.setColor(card);
        aboutBg.setStroke(dp(1.5f), primary);
        about.setBackground(aboutBg);
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        about.setLayoutParams(aboutLp);
        about.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AboutActivity.class)));
        titleRow.addView(title);
        titleRow.addView(about);
        header.addView(titleRow);

        // 标语
        TextView slogan = new TextView(this);
        slogan.setText(getString(R.string.home_slogan));
        slogan.setTextSize(13);
        slogan.setTextColor(sub);
        slogan.setPadding(dp(2), dp(2), 0, 0);
        header.addView(slogan);

        root.addView(header);

        // ---- 分段标签：嵌入 / 提取（MIUI X segmented）----
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setGravity(android.view.Gravity.CENTER);
        GradientDrawable segBg = new GradientDrawable();
        segBg.setColor(getColor(R.color.tuyin_seg_bg));
        segBg.setCornerRadius(dp(24));
        seg.setBackground(segBg);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        sp.setMargins(dp(24), 0, dp(24), dp(14));
        seg.setLayoutParams(sp);

        segEmbed = makeSegItem(getString(R.string.tab_embed));
        segExtract = makeSegItem(getString(R.string.tab_extract));
        segEmbed.setOnClickListener(v -> switchTab(0));
        segExtract.setOnClickListener(v -> switchTab(1));
        seg.addView(segEmbed, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        seg.addView(segExtract, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(seg);

        // ---- 工作台卡片容器 + WebView ----
        FrameLayout cardBox = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(card);
        cardBg.setCornerRadius(dp(20));
        cardBox.setBackground(cardBg);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        cp.setMargins(dp(12), 0, dp(12), dp(12));
        cardBox.setLayoutParams(cp);
        cardBox.setPadding(dp(6), dp(6), dp(6), dp(6));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(
                    "var h=document.querySelector('.site-header'); if(h) h.style.display='none';" +
                    "var f=document.querySelector('.site-footer'); if(f) f.style.display='none';" +
                    "var t=document.querySelector('.tabs'); if(t) t.style.display='none';",
                    null);
            }
        });

        // 文件选择：打开系统图片选择器，把所选 Uri 回传页面 <input type=file>
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                fileChooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_image_title)),
                        REQ_PICK_IMAGE);
                return true;
            }
        });

        // 原生保存桥：页面调用 TuyinBridge.savePng(dataUrl, filename) 存入相册
        web.addJavascriptInterface(new TuyinBridge(), "TuyinBridge");

        cardBox.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(cardBox);

        setContentView(root);
        web.loadUrl("file:///android_asset/index.html");
        switchTab(0);

        // 读取图片权限（Android 7-12 需 READ_EXTERNAL_STORAGE；13+ 用 READ_MEDIA_IMAGES / 系统选择器）
        requestReadPermissionIfNeeded();
    }

    /* ------------------------------------------------------------------ *
     * 权限
     * ------------------------------------------------------------------ */
    private void requestReadPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQ_READ_PERM);
            }
        } else if (Build.VERSION.SDK_INT >= 24) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ_PERM);
            }
        }
    }

    private boolean hasWritePermission() {
        if (Build.VERSION.SDK_INT >= 29) return true; // 29+ 写相册无需权限
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE_PERM && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 用户授权后重试挂起的保存
            if (pendingSaveDataUrl != null) {
                doSavePng(pendingSaveDataUrl, pendingSaveName);
                pendingSaveDataUrl = null;
                pendingSaveName = null;
            }
        } else if (requestCode == REQ_WRITE_PERM) {
            Toast.makeText(this, getString(R.string.save_denied), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(uri == null ? new Uri[0] : new Uri[]{uri});
                fileChooserCallback = null;
            }
        } else if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
    }

    /* ------------------------------------------------------------------ *
     * 保存到相册
     * ------------------------------------------------------------------ */
    private class TuyinBridge {
        @JavascriptInterface
        public void savePng(String dataUrl, String filename) {
            runOnUiThread(() -> {
                if (!hasWritePermission()) {
                    if (Build.VERSION.SDK_INT <= 28) {
                        pendingSaveDataUrl = dataUrl;
                        pendingSaveName = filename;
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                REQ_WRITE_PERM);
                    }
                    return;
                }
                doSavePng(dataUrl, filename);
            });
        }
    }

    private void doSavePng(String dataUrl, String filename) {
        try {
            byte[] bytes = decodeDataUrl(dataUrl);
            if (bytes == null || bytes.length == 0) {
                Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) {
                Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_SHORT).show();
                return;
            }
            String title = filename != null && filename.endsWith(".png")
                    ? filename.substring(0, filename.length() - 4) : "tuyin";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename == null ? "tuyin.png" : filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (FileOutputStream fos = (FileOutputStream) getContentResolver()
                            .openOutputStream(uri)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                    Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES);
                if (dir.exists() || dir.mkdirs()) {
                    File out = new File(dir, (filename == null ? "tuyin.png" : filename));
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                    Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_LONG).show();
                    return;
                }
            }
            Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.save_failed) + ": " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private byte[] decodeDataUrl(String dataUrl) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return null;
            String base64Part = dataUrl.substring(comma + 1);
            if (Build.VERSION.SDK_INT >= 26) {
                return Base64.getDecoder().decode(base64Part);
            }
            return android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }

    /* ------------------------------------------------------------------ *
     * 视图
     * ------------------------------------------------------------------ */
    private TextView makeSegItem(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private void switchTab(int idx) {
        int primary = getColor(R.color.tuyin_primary);
        int sub = getColor(R.color.tuyin_sub);
        int card = getColor(R.color.tuyin_card);
        int segLine = getColor(R.color.tuyin_seg_line);

        segEmbed.setTextColor(idx == 0 ? primary : sub);
        segExtract.setTextColor(idx == 1 ? primary : sub);

        GradientDrawable a0 = new GradientDrawable();
        a0.setColor(card);
        a0.setCornerRadius(dp(24));
        a0.setStroke(dp(1), segLine);
        GradientDrawable a1 = new GradientDrawable();
        a1.setColor(card);
        a1.setCornerRadius(dp(24));
        a1.setStroke(dp(1), segLine);
        segEmbed.setBackground(idx == 0 ? a0 : null);
        segExtract.setBackground(idx == 1 ? a1 : null);

        if (web != null && web.getUrl() != null) {
            web.evaluateJavascript(
                "var t=document.querySelectorAll('.tab'); if(t&&t[" + idx + "]) t[" + idx + "].click();",
                null);
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
