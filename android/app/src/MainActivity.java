package com.tuopzf.tuyin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 图隐 — MIUI X 风格主界面：原生标题栏 + 分段标签 + 圆角卡片承载内嵌工作台。 */
public class MainActivity extends Activity {

    private WebView web;
    private TextView segEmbed;
    private TextView segExtract;

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

        // ---- 标题栏：应用名「图隐」+ 关于入口 ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.setMargins(dp(24), dp(18), dp(24), dp(12));
        header.setLayoutParams(hp);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(text);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView about = new TextView(this);
        about.setText(getString(R.string.about_title));
        about.setTextSize(15);
        about.setTextColor(primary);
        about.setTypeface(Typeface.DEFAULT_BOLD);
        about.setGravity(android.view.Gravity.CENTER);
        about.setPadding(dp(18), dp(8), dp(18), dp(8));
        about.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AboutActivity.class)));

        header.addView(title);
        header.addView(about);
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
                // 隐藏页面自带的头部与双标签，避免与原生产生重复
                view.evaluateJavascript(
                    "var h=document.querySelector('.site-header'); if(h) h.style.display='none';" +
                    "var f=document.querySelector('.site-footer'); if(f) f.style.display='none';" +
                    "var t=document.querySelector('.tabs'); if(t) t.style.display='none';",
                    null);
            }
        });
        cardBox.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(cardBox);

        setContentView(root);
        web.loadUrl("file:///android_asset/index.html");
        switchTab(0);
    }

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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
