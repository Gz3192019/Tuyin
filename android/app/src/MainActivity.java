package com.tuopzf.tuyin;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 图隐 — WebView 壳工作台。
 * 顶部：沉浸渐变标题栏（由上到下由实渐透，浮动在内容上方），左「图隐」+ slogan，
 * 右上角圆角方形蓝色「关于」按钮；内容区：WebView 加载打包的 RAC-Hide 工作台
 * （瓷白底透出、隐藏网页头部，顶部预留 96dp 给渐变标题栏）；
 * 底部：悬浮图标导航（去文字、60% 透明胶囊），联动网页 tab。
 * 上传图片走 WebChromeClient 系统选图器（免存储权限）；保存图片由
 * TuyinBridge 原生接管（API 29+ MediaStore 免权限 / 更低版本请求写入权限）。
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 100;
    private static final int REQ_WRITE = 200;

    // UI
    private WebView web;
    private LinearLayout tabEmbed;
    private LinearLayout tabExtract;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int bg = color(R.color.tuyin_bg);
        int card = color(R.color.tuyin_card);
        int primary = color(R.color.tuyin_primary);
        int text = color(R.color.tuyin_text);
        int sub = color(R.color.tuyin_sub);

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        // ---- 内容区：WebView 全屏，加载打包工作台 ----
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new TuyinBridge(), "TuyinBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        // 上传图片：网页 <input type=file> → 系统选图器 → 回传 URI 给网页
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(intent,
                            getString(R.string.pick_image_title)), REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl("file:///android_asset/index.html");

        // ---- 顶部标题栏：浮动 + 由上到下渐变（越往下越透明） ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(16), dp(20), dp(8));
        header.setBackground(headerGradient(bg));
        root.addView(header, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(text);
        title.setTextSize(30);
        title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 圆角方形蓝色「关于」按钮
        TextView aboutBtn = new TextView(this);
        aboutBtn.setText(R.string.about_title);
        aboutBtn.setTextColor(primary);
        aboutBtn.setTextSize(14);
        aboutBtn.setTypeface(null, Typeface.BOLD);
        aboutBtn.setGravity(Gravity.CENTER);
        aboutBtn.setBackground(shadowBg(14, translucent(R.color.tuyin_card, 235), 3));
        aboutBtn.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        titleRow.addView(aboutBtn, new LinearLayout.LayoutParams(dp(72), dp(36)));

        // slogan 左对齐（与标题同一条基线）
        TextView slogan = new TextView(this);
        slogan.setText(R.string.home_slogan);
        slogan.setTextColor(sub);
        slogan.setTextSize(13);
        slogan.setGravity(Gravity.START);
        header.addView(slogan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---------------- 底部悬浮导航：去文字、60% 透明、圆角胶囊 ----------------
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setGravity(Gravity.CENTER);
        seg.setBackground(shadowBg(32, translucent(R.color.tuyin_card, 153), 6));

        tabEmbed = makeNavItem(R.drawable.ic_embed);
        tabExtract = makeNavItem(R.drawable.ic_extract);
        tabEmbed.setOnClickListener(v -> switchPanel(true));
        tabExtract.setOnClickListener(v -> switchPanel(false));
        tabEmbed.setOnTouchListener(pressScale());
        tabExtract.setOnTouchListener(pressScale());
        seg.addView(tabEmbed, new LinearLayout.LayoutParams(dp(72), dp(64)));
        seg.addView(tabExtract, new LinearLayout.LayoutParams(dp(72), dp(64)));

        FrameLayout.LayoutParams segLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        segLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        segLp.bottomMargin = dp(24);
        root.addView(seg, segLp);

        switchPanel(true);
        setContentView(root);
    }

    /** 标题栏垂直渐变：顶部实色 → 中部半透 → 底部近透明。 */
    private Drawable headerGradient(int base) {
        int r = Color.red(base), g = Color.green(base), b = Color.blue(base);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(255, r, g, b),
                        Color.argb(160, r, g, b),
                        Color.argb(70, r, g, b)
                });
        return gd;
    }

    /** TuyinBridge：网页 -> 原生（保存到相册、toast）。 */
    private class TuyinBridge {
        @JavascriptInterface
        public void savePng(final String dataUrl, final String filename) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT <= 28
                        && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQ_WRITE);
                    return;
                }
                try {
                    String b64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp == null) {
                        toast("保存失败：图片解码出错");
                        return;
                    }
                    boolean ok = RacImages.saveBitmapToGallery(MainActivity.this, bmp, filename, "image/png", 100);
                    toast(ok ? "已保存到相册" : "保存失败，请重试");
                } catch (Exception e) {
                    toast("保存失败：" + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void showToast(final String msg) {
            runOnUiThread(() -> toast(msg));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (filePathCallback == null) return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri == null) {
                    if (data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                        uri = data.getClipData().getItemAt(0).getUri();
                    }
                }
                if (uri != null) result = new Uri[] { uri };
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
            web.requestFocus();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toast("已授权，请再次点击保存");
            } else {
                toast(getString(R.string.save_denied));
            }
        }
    }

    /** 切换网页 tab + 导航高亮。 */
    private void switchPanel(boolean embed) {
        String tab = embed ? "embed" : "extract";
        web.evaluateJavascript(
                "document.querySelector('.tab[data-tab=\"" + tab + "\"]').click();", null);
        applyNavState(tabEmbed, embed);
        applyNavState(tabExtract, !embed);
    }

    /** 悬浮导航选中态：蓝色圆形图标；未选中：半透明灰、无圆底。 */
    private void applyNavState(LinearLayout item, boolean selected) {
        ImageView icon = (ImageView) item.getTag(R.id.navIcon);
        GradientDrawable circle = (GradientDrawable) icon.getBackground();
        if (selected) {
            circle.setColor(color(R.color.tuyin_primary));
            icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            item.setAlpha(1f);
        } else {
            circle.setColor(Color.TRANSPARENT);
            icon.setColorFilter(color(R.color.tuyin_sub), PorterDuff.Mode.SRC_IN);
            item.setAlpha(0.45f);
        }
    }

    /** 悬浮导航项：纯图标（圆形底），无文字。 */
    private LinearLayout makeNavItem(int iconRes) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.TRANSPARENT);
        icon.setBackground(circle);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        iconLp.topMargin = dp(6);
        iconLp.bottomMargin = dp(6);
        item.addView(icon, iconLp);

        item.setTag(R.id.navIcon, icon);
        return item;
    }

    /** 按压反馈：按下轻微缩小。 */
    private View.OnTouchListener pressScale() {
        return (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        };
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int color(int id) {
        return getResources().getColor(id);
    }

    /** 资源色按指定 alpha 转半透明（深浅模式都生效）。 */
    private int translucent(int colorId, int alpha) {
        int c = color(colorId);
        return Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c));
    }

    private GradientDrawable shapeBg(int radiusDp, int fillColor) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(radiusDp));
        g.setColor(fillColor);
        return g;
    }

    /**
     * 圆角渐变投影：5 层 LayerDrawable（4 层阴影从外到内渐深 + 主体）。
     * 主体向右下内缩 depthDp；阴影层 inset 阶梯递减，右/下边缘呈
     * 由深到浅的柔和渐变，投影 100% 跟随圆角，无均匀灰条分界线。
     */
    private Drawable shadowWrap(GradientDrawable body, int radiusDp, int depthDp) {
        int d = dp(depthDp);
        int[] alphas = { 10, 18, 26, 36 };
        Drawable[] layers = new Drawable[5];
        for (int i = 0; i < 4; i++) {
            layers[i] = shapeBg(radiusDp, Color.argb(alphas[i], 0, 0, 0));
        }
        layers[4] = body;
        LayerDrawable ld = new LayerDrawable(layers);
        for (int i = 0; i < 4; i++) {
            int inset = d * (3 - i) / 4;
            ld.setLayerInset(i, 0, 0, inset, inset);
        }
        ld.setLayerInset(4, 0, 0, d, d);
        return ld;
    }

    /** 圆角投影背景。 */
    private Drawable shadowBg(int radiusDp, int fillColor, int depthDp) {
        return shadowWrap(shapeBg(radiusDp, fillColor), radiusDp, depthDp);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
