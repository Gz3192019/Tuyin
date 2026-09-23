package com.tuopzf.tuyin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 图隐 — 关于页（MIUI X 风格）：应用图标、应用名「图隐」、版本与项目信息。 */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int bg = getColor(R.color.tuyin_bg);
        int card = getColor(R.color.tuyin_card);
        int text = getColor(R.color.tuyin_text);
        int sub = getColor(R.color.tuyin_sub);
        int primary = getColor(R.color.tuyin_primary);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        // 顶部：返回 + 标题
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(14), dp(24), dp(10));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(26);
        back.setTextColor(text);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(14), 0, dp(14), 0);
        back.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText(getString(R.string.about_title));
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(text);
        title.setGravity(Gravity.CENTER_VERTICAL);

        top.addView(back, new LinearLayout.LayoutParams(0, dp(48), 0));
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(top);

        // 内容卡片
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(28), dp(40), dp(28), dp(28));
        root.addView(inner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // 图标
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(104), dp(104));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        inner.addView(icon, ip);

        // 应用名
        TextView appName = new TextView(this);
        appName.setText(getString(R.string.app_name));
        appName.setTextSize(28);
        appName.setTypeface(Typeface.DEFAULT_BOLD);
        appName.setTextColor(text);
        appName.setGravity(Gravity.CENTER);
        inner.addView(appName, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        // 版本
        TextView version = new TextView(this);
        version.setText("版本 " + getString(R.string.version_name));
        version.setTextSize(14);
        version.setTextColor(sub);
        version.setGravity(Gravity.CENTER);
        inner.addView(version);

        // 说明卡片
        LinearLayout infoCard = makeCard(card);
        LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        icp.topMargin = dp(28);
        infoCard.setLayoutParams(icp);

        TextView d1 = makeDesc(text, sub, getString(R.string.about_desc));
        TextView d2 = makeDesc(text, sub, getString(R.string.about_desc2));
        TextView d3 = makeDesc(text, sub, getString(R.string.about_desc3));
        infoCard.addView(d1);
        infoCard.addView(d2);
        infoCard.addView(d3);
        inner.addView(infoCard);

        // 项目链接卡片
        LinearLayout linkCard = makeCard(card);
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lcp.topMargin = dp(16);
        linkCard.setLayoutParams(lcp);

        TextView gh = new TextView(this);
        gh.setText("开源项目 · RAC-Hide  （点击查看）");
        gh.setTextSize(14);
        gh.setTextColor(primary);
        gh.setTypeface(Typeface.DEFAULT_BOLD);
        gh.setGravity(Gravity.CENTER);
        gh.setPadding(0, dp(6), 0, dp(6));
        gh.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/tuoPzf/rac-hide")));
            } catch (Exception ignored) { }
        });
        linkCard.addView(gh);
        inner.addView(linkCard);
    }

    private LinearLayout makeCard(int cardColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(cardColor);
        g.setCornerRadius(dp(20));
        card.setBackground(g);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        return card;
    }

    private TextView makeDesc(int text, int sub, String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(14);
        tv.setTextColor(sub);
        tv.setLineSpacing(dp(4), 1f);
        tv.setPadding(0, dp(3), 0, dp(3));
        return tv;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
