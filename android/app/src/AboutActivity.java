package com.tuopzf.tuyin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 图隐 — 关于页：致敬原作者的 RAC-Hide（分组列表式，瓷白底 + 白色卡片）。 */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int bg = getColor(R.color.tuyin_bg);
        int card = getColor(R.color.tuyin_card);
        int text = getColor(R.color.tuyin_text);
        int sub = getColor(R.color.tuyin_sub);
        int primary = getColor(R.color.tuyin_primary);
        int line = getColor(R.color.tuyin_seg_line);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        // ---- 顶部：返回 + 标题 ----
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

        // ---- 滚动内容 ----
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(12), dp(28), dp(36));
        scroll.addView(content);

        // ---- 头部：图标 + 名称 + 标语 + 版本 ----
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(104), dp(104));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(icon, ip);

        TextView appName = new TextView(this);
        appName.setText(getString(R.string.app_name));
        appName.setTextSize(28);
        appName.setTypeface(Typeface.DEFAULT_BOLD);
        appName.setTextColor(text);
        appName.setGravity(Gravity.CENTER);
        content.addView(appName, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        TextView slogan = new TextView(this);
        slogan.setText(getString(R.string.home_slogan));
        slogan.setTextSize(14);
        slogan.setTextColor(sub);
        slogan.setGravity(Gravity.CENTER);
        content.addView(slogan);

        TextView version = new TextView(this);
        version.setText(getString(R.string.about_version_fmt, getString(R.string.version_name)));
        version.setTextSize(13);
        version.setTextColor(sub);
        version.setGravity(Gravity.CENTER);
        content.addView(version, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        // ---- 开发者分组 ----
        LinearLayout devGroup = makeGroup(getString(R.string.about_dev_title), card, line, text, sub);
        devGroup.addView(makePersonRow(text, sub, primary, card, line,
                "T", getString(R.string.about_dev_author),
                getString(R.string.about_dev_author_desc),
                "https://github.com/tuoPzf/rac-hide"));
        devGroup.addView(makePersonRow(text, sub, primary, card, line,
                "G", getString(R.string.about_dev_forker),
                getString(R.string.about_dev_forker_desc),
                "https://github.com/Gz3192019/rac-hide"));
        content.addView(devGroup);

        // ---- 当前系统信息分组 ----
        LinearLayout sysGroup = makeGroup(getString(R.string.about_sys_title), card, line, text, sub);
        sysGroup.addView(makeInfoRow(text, sub, line, getString(R.string.about_sys_model), deviceModel()));
        sysGroup.addView(makeInfoRow(text, sub, line, getString(R.string.about_sys_os), osVersion()));
        content.addView(sysGroup);

        // ---- 开源项目分组（致敬原作者）----
        LinearLayout ossGroup = makeGroup(getString(R.string.about_oss_title), card, line, text, sub);
        ossGroup.addView(makeLinkRow(text, primary, sub, card, line,
                getString(R.string.about_oss_name), getString(R.string.about_oss_desc),
                "https://github.com/tuoPzf/rac-hide"));
        content.addView(ossGroup);

        // ---- 脚注 ----
        TextView foot = new TextView(this);
        foot.setText(getString(R.string.about_foot));
        foot.setTextSize(12);
        foot.setTextColor(sub);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(24), 0, 0);
        content.addView(foot);

        // 关键：必须把构建好的视图树挂到窗口，否则关于页空白
        setContentView(root);
    }

    /* ------------------------------------------------------------------ *
     * 分组容器
     * ------------------------------------------------------------------ */
    private LinearLayout makeGroup(String label, int card, int line, int text, int sub) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);

        TextView head = new TextView(this);
        head.setText(label);
        head.setTextSize(13);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextColor(sub);
        head.setPadding(0, dp(18), 0, dp(8));
        group.addView(head);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(card);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), line);
        list.setBackground(g);
        list.setPadding(dp(16), dp(6), dp(16), dp(6));
        group.addView(list);
        group.setTag(list);
        return group;
    }

    private LinearLayout rowContainer(int card, int line) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        return row;
    }

    /* ------------------------------------------------------------------ *
     * 开发者行：首字母头像 + 昵称/贡献 + 箭头
     * ------------------------------------------------------------------ */
    private LinearLayout makePersonRow(int text, int sub, int primary, int card, int line,
                                       String letter, String name, String desc, final String url) {
        LinearLayout row = rowContainer(card, line);
        row.setClickable(true);

        TextView avatar = new TextView(this);
        avatar.setText(letter);
        avatar.setTextSize(17);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable av = new GradientDrawable();
        av.setShape(GradientDrawable.OVAL);
        av.setColor(primary);
        avatar.setBackground(av);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        row.addView(avatar, avLp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.setMargins(dp(12), 0, dp(8), 0);
        info.setLayoutParams(infoLp);

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextSize(15);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);
        nameTv.setTextColor(text);
        info.addView(nameTv);

        TextView descTv = new TextView(this);
        descTv.setText(desc);
        descTv.setTextSize(12.5f);
        descTv.setTextColor(sub);
        descTv.setPadding(0, dp(2), 0, 0);
        info.addView(descTv);

        row.addView(info);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(sub);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow);

        if (url != null) {
            row.setOnClickListener(v -> openUrl(url));
        }
        return row;
    }

    /* ------------------------------------------------------------------ *
     * 信息行：标签 + 值
     * ------------------------------------------------------------------ */
    private LinearLayout makeInfoRow(int text, int sub, int line, String label, String value) {
        LinearLayout row = rowContainer(sub, line);
        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(14);
        labelTv.setTextColor(text);
        row.addView(labelTv, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(14);
        valueTv.setTextColor(sub);
        valueTv.setGravity(Gravity.END);
        row.addView(valueTv);
        return row;
    }

    /* ------------------------------------------------------------------ *
     * 链接行：名称 + 描述 + 箭头
     * ------------------------------------------------------------------ */
    private LinearLayout makeLinkRow(int text, int primary, int sub, int card, int line,
                                     String name, String desc, final String url) {
        LinearLayout row = rowContainer(card, line);
        row.setClickable(true);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextSize(15);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);
        nameTv.setTextColor(text);
        info.addView(nameTv);

        TextView descTv = new TextView(this);
        descTv.setText(desc);
        descTv.setTextSize(12.5f);
        descTv.setTextColor(primary);
        descTv.setPadding(0, dp(2), 0, 0);
        info.addView(descTv);

        row.addView(info);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(sub);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow);

        row.setOnClickListener(v -> openUrl(url));
        return row;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) { }
    }

    /* ------------------------------------------------------------------ */
    private String deviceModel() {
        String model = Build.MODEL;
        String brand = Build.BRAND;
        if (brand != null && !brand.isEmpty() && !model.startsWith(brand)) {
            return brand + " " + model;
        }
        return model;
    }

    private String osVersion() {
        return "OS " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
