package com.tuopzf.tuyin;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 图隐 — 原生隐写工作台。
 * MIUI X 风格：瓷白底、圆角卡片+投影、悬浮导航（未选中半透明）。
 * 嵌入/提取全部由本地 Java 算法完成（与上游 JS 实现互通）。
 */
public class MainActivity extends Activity {

    private static final int PICK_COVER = 100;
    private static final int PICK_SECRET = 101;
    private static final int PICK_STEGO = 102;
    private static final int REQ_WRITE = 200;

    // 状态
    private RacCore.ImageData coverData;
    private RacCore.ImageData secretData;
    private RacCore.ImageData stegoData;
    private Bitmap extractedBitmap;
    private int payloadBytes;
    private int capacityBytes = 0;

    // UI
    private LinearLayout panelEmbed;
    private LinearLayout panelExtract;
    private TextView tabEmbed;
    private TextView tabExtract;
    private ImageView coverPreview;
    private ImageView secretPreview;
    private ImageView stegoPreview;
    private ImageView stegoPreview2;
    private ImageView coverBoxImg;
    private ImageView secretBoxImg;
    private ImageView stegoBoxImg;
    private View coverBoxText;
    private View secretBoxText;
    private View stegoBoxText;
    private TextView capacityText;
    private ProgressBar capacityBar;
    private TextView embedStatus;
    private TextView extractStatus;
    private SeekBar tierSlider;
    private TextView tierName;
    private Spinner enhanceSpinner;
    private LinearLayout customRow;
    private SeekBar ppbBar;
    private TextView ppbVal;
    private SeekBar repeatBar;
    private TextView repeatVal;
    private SeekBar nsymBar;
    private TextView nsymVal;
    private EditText coverWInput;
    private EditText coverHInput;
    private Button btnEmbed;
    private Button btnSaveStego;
    private Button btnSaveExtracted;
    private Button btnExtract;
    private Button btnResetEmbed;
    private Button btnResetExtract;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int bg = color(R.color.tuyin_bg);
        int card = color(R.color.tuyin_card);
        int primary = color(R.color.tuyin_primary);
        int text = color(R.color.tuyin_text);
        int sub = color(R.color.tuyin_sub);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        // ---------------- 纵向主栈 ----------------
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        root.addView(stack, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 标题栏 ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(16), dp(20), dp(6));
        stack.addView(header, new LinearLayout.LayoutParams(
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

        TextView aboutBtn = new TextView(this);
        aboutBtn.setText(R.string.about_title);
        aboutBtn.setTextColor(primary);
        aboutBtn.setTextSize(14);
        aboutBtn.setGravity(Gravity.CENTER);
        GradientDrawable aboutShape = new GradientDrawable();
        aboutShape.setCornerRadius(dp(14));
        aboutShape.setStroke(dp(1), primary);
        aboutShape.setColor(card);
        aboutBtn.setBackground(aboutShape);
        aboutBtn.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        titleRow.addView(aboutBtn, new LinearLayout.LayoutParams(dp(72), dp(36)));

        TextView slogan = new TextView(this);
        slogan.setText(R.string.home_slogan);
        slogan.setTextColor(sub);
        slogan.setTextSize(13);
        header.addView(slogan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 工作区 ----
        ScrollView scroll = new ScrollView(this);
        stack.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(112));
        scroll.addView(content);

        // ================ 嵌入面板 ================
        panelEmbed = new LinearLayout(this);
        panelEmbed.setOrientation(LinearLayout.VERTICAL);
        content.addView(panelEmbed, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout uploadRow = new LinearLayout(this);
        uploadRow.setOrientation(LinearLayout.HORIZONTAL);
        panelEmbed.addView(uploadRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout coverBox = makeUploadBox(card, primary, "封面图 · 宿主", v -> pick(PICK_COVER));
        LinearLayout secretBox = makeUploadBox(card, primary, "秘密图 · 被隐藏", v -> pick(PICK_SECRET));
        uploadRow.addView(coverBox, new LinearLayout.LayoutParams(0, dp(190), 1f));
        LinearLayout.LayoutParams secretLp = new LinearLayout.LayoutParams(0, dp(190), 1f);
        secretLp.leftMargin = dp(10);
        uploadRow.addView(secretBox, secretLp);

        coverBoxText = (View) coverBox.getTag(R.id.placeholder);
        coverBoxImg = (ImageView) coverBox.getTag(R.id.preview);
        secretBoxText = (View) secretBox.getTag(R.id.placeholder);
        secretBoxImg = (ImageView) secretBox.getTag(R.id.preview);

        // 容量条
        LinearLayout capCard = makeCard(card);
        panelEmbed.addView(capCard, cardLp(0, dp(12)));

        LinearLayout capTitleRow = new LinearLayout(this);
        capTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        capTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        capCard.addView(capTitleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView capLabel = new TextView(this);
        capLabel.setText("可用容量");
        capLabel.setTextColor(text);
        capLabel.setTextSize(14);
        capLabel.setTypeface(null, Typeface.BOLD);
        capTitleRow.addView(capLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        capacityText = new TextView(this);
        capacityText.setTextColor(sub);
        capacityText.setTextSize(12);
        capTitleRow.addView(capacityText);

        capacityBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        GradientDrawable barTrack = new GradientDrawable();
        barTrack.setColor(color(R.color.tuyin_seg_bg));
        barTrack.setCornerRadius(dp(4));
        capacityBar.setProgressDrawable(barTrack);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        barLp.topMargin = dp(8);
        capCard.addView(capacityBar, barLp);

        // 封面增强
        LinearLayout enhanceCard = makeCard(card);
        panelEmbed.addView(enhanceCard, cardLp(0, dp(12)));

        LinearLayout enhanceRow = new LinearLayout(this);
        enhanceRow.setOrientation(LinearLayout.HORIZONTAL);
        enhanceRow.setGravity(Gravity.CENTER_VERTICAL);
        enhanceCard.addView(enhanceRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView enhanceLabel = new TextView(this);
        enhanceLabel.setText("封面增强");
        enhanceLabel.setTextColor(text);
        enhanceLabel.setTextSize(14);
        enhanceLabel.setTypeface(null, Typeface.BOLD);
        enhanceRow.addView(enhanceLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        enhanceSpinner = new Spinner(this);
        enhanceSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[] { "不放大（原尺寸）", "长边 ≤ 512", "长边 ≤ 768", "长边 ≤ 1024", "长边 ≤ 1280", "长边 ≤ 2048" }));
        enhanceRow.addView(enhanceSpinner, new LinearLayout.LayoutParams(0, dp(44), 1f));

        // 画质档位
        LinearLayout tierCard = makeCard(card);
        panelEmbed.addView(tierCard, cardLp(0, dp(12)));

        LinearLayout tierTitleRow = new LinearLayout(this);
        tierTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        tierTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        tierCard.addView(tierTitleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tierLabel = new TextView(this);
        tierLabel.setText("画质与容量");
        tierLabel.setTextColor(text);
        tierLabel.setTextSize(14);
        tierLabel.setTypeface(null, Typeface.BOLD);
        tierTitleRow.addView(tierLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tierName = new TextView(this);
        tierName.setTextColor(primary);
        tierName.setTextSize(12);
        tierTitleRow.addView(tierName);

        tierSlider = new SeekBar(this);
        tierSlider.setMax(100);
        tierSlider.setProgress(50);
        LinearLayout.LayoutParams tierLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tierLp.topMargin = dp(6);
        tierCard.addView(tierSlider, tierLp);
        tierSlider.setOnSeekBarChangeListener(new SimpleBar() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTierName(progress);
            }
        });
        updateTierName(50);

        // 自定义参数（可展开）
        Button customToggle = new Button(this);
        customToggle.setText("自定义参数");
        customToggle.setTextColor(primary);
        customToggle.setTextSize(12);
        customToggle.setAllCaps(false);
        customToggle.setBackgroundColor(Color.TRANSPARENT);
        panelEmbed.addView(customToggle, wrapLp(0, dp(4), Gravity.START));

        customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.VERTICAL);
        customRow.setVisibility(View.GONE);
        panelEmbed.addView(customRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addParamSlider(customRow, "每块系数对 ppb", 1, 12, 2, "ppb");
        addParamSlider(customRow, "重复 repeat", 1, 5, 1, "repeat");
        addParamSlider(customRow, "校验 nsym", 8, 64, 48, "nsym");
        customToggle.setOnClickListener(v -> {
            customRow.setVisibility(customRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            customToggle.setText(customRow.getVisibility() == View.VISIBLE ? "收起参数" : "自定义参数");
        });

        // 操作按钮
        btnEmbed = primaryButton("开始嵌入", primary, card);
        btnSaveStego = ghostButton("保存隐写图到相册", primary, card);
        btnResetEmbed = ghostButton("重置", sub, card);
        panelEmbed.addView(btnEmbed, btnLp(0, dp(12)));
        LinearLayout embedBtnRow = new LinearLayout(this);
        embedBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        panelEmbed.addView(embedBtnRow, wrapLp(0, dp(8), Gravity.START));
        embedBtnRow.addView(btnSaveStego, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        resetLp.leftMargin = dp(10);
        embedBtnRow.addView(btnResetEmbed, resetLp);

        embedStatus = new TextView(this);
        embedStatus.setTextColor(sub);
        embedStatus.setTextSize(12);
        panelEmbed.addView(embedStatus, wrapLp(0, dp(10), Gravity.START));

        // 预览三卡
        LinearLayout embedPreviewRow = new LinearLayout(this);
        embedPreviewRow.setOrientation(LinearLayout.HORIZONTAL);
        panelEmbed.addView(embedPreviewRow, wrapLp(0, dp(4), Gravity.START));
        ImageView coverPv = new ImageView(this);
        ImageView secretPv = new ImageView(this);
        ImageView stegoPv = new ImageView(this);
        embedPreviewRow.addView(makePreviewCard(card, "封面", coverPv),
                new LinearLayout.LayoutParams(0, dp(150), 1f));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(150), 1f);
        p1.leftMargin = dp(8);
        embedPreviewRow.addView(makePreviewCard(card, "秘密图", secretPv), p1);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(150), 1f);
        p2.leftMargin = dp(8);
        embedPreviewRow.addView(makePreviewCard(card, "隐写结果", stegoPv), p2);
        coverPreview = coverPv;
        secretPreview = secretPv;
        stegoPreview = stegoPv;
        coverBoxImg.setVisibility(View.GONE);
        secretBoxImg.setVisibility(View.GONE);

        // ================ 提取面板 ================
        panelExtract = new LinearLayout(this);
        panelExtract.setOrientation(LinearLayout.VERTICAL);
        panelExtract.setVisibility(View.GONE);
        content.addView(panelExtract, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout stegoBox = makeUploadBox(card, primary, "隐写图 · 要解密的图片", v -> pick(PICK_STEGO));
        panelExtract.addView(stegoBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
        stegoBoxText = (View) stegoBox.getTag(R.id.placeholder);
        stegoBoxImg = (ImageView) stegoBox.getTag(R.id.preview);
        stegoBoxImg.setVisibility(View.GONE);

        LinearLayout sizeCard = makeCard(card);
        panelExtract.addView(sizeCard, cardLp(0, dp(12)));

        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("原图尺寸（可选，恢复缩放）");
        sizeLabel.setTextColor(text);
        sizeLabel.setTextSize(13);
        sizeCard.addView(sizeLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout sizeInputRow = new LinearLayout(this);
        sizeInputRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeInputRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sizeInputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sizeInputLp.topMargin = dp(8);
        sizeCard.addView(sizeInputRow, sizeInputLp);

        coverWInput = numberInput("宽");
        sizeInputRow.addView(coverWInput, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView xMark = new TextView(this);
        xMark.setText(" × ");
        xMark.setTextColor(sub);
        sizeInputRow.addView(xMark);
        coverHInput = numberInput("高");
        sizeInputRow.addView(coverHInput, new LinearLayout.LayoutParams(0, dp(44), 1f));

        btnExtract = primaryButton("提取秘密图", primary, card);
        btnSaveExtracted = ghostButton("保存秘密图到相册", primary, card);
        btnResetExtract = ghostButton("重置", sub, card);
        panelExtract.addView(btnExtract, btnLp(0, dp(12)));
        LinearLayout extractBtnRow = new LinearLayout(this);
        extractBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        panelExtract.addView(extractBtnRow, wrapLp(0, dp(8), Gravity.START));
        extractBtnRow.addView(btnSaveExtracted, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams resetLp2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        resetLp2.leftMargin = dp(10);
        extractBtnRow.addView(btnResetExtract, resetLp2);

        extractStatus = new TextView(this);
        extractStatus.setTextColor(sub);
        extractStatus.setTextSize(12);
        panelExtract.addView(extractStatus, wrapLp(0, dp(10), Gravity.START));

        LinearLayout extractPreviewRow = new LinearLayout(this);
        extractPreviewRow.setOrientation(LinearLayout.HORIZONTAL);
        panelExtract.addView(extractPreviewRow, wrapLp(0, dp(4), Gravity.START));
        ImageView stegoPv2 = new ImageView(this);
        ImageView resultPv = new ImageView(this);
        extractPreviewRow.addView(makePreviewCard(card, "隐写图", stegoPv2),
                new LinearLayout.LayoutParams(0, dp(160), 1f));
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(160), 1f);
        p3.leftMargin = dp(8);
        extractPreviewRow.addView(makePreviewCard(card, "提取结果", resultPv), p3);
        stegoPreview2 = stegoPv2;

        // ---------------- 悬浮导航（未选中半透明） ----------------
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable segShape = new GradientDrawable();
        segShape.setColor(card);
        segShape.setCornerRadius(dp(28));
        seg.setElevation(dp(10));
        seg.setBackground(segShape);

        tabEmbed = new TextView(this);
        tabEmbed.setText(R.string.tab_embed);
        tabEmbed.setGravity(Gravity.CENTER);
        tabEmbed.setTextSize(14);
        tabEmbed.setTypeface(null, Typeface.BOLD);
        tabEmbed.setClickable(true);
        seg.addView(tabEmbed, new LinearLayout.LayoutParams(dp(140), dp(52)));

        tabExtract = new TextView(this);
        tabExtract.setText(R.string.tab_extract);
        tabExtract.setGravity(Gravity.CENTER);
        tabExtract.setTextSize(14);
        tabExtract.setTypeface(null, Typeface.BOLD);
        tabExtract.setClickable(true);
        seg.addView(tabExtract, new LinearLayout.LayoutParams(dp(140), dp(52)));

        FrameLayout.LayoutParams segLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        segLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        segLp.bottomMargin = dp(22);
        root.addView(seg, segLp);

        tabEmbed.setOnClickListener(v -> switchPanel(true));
        tabExtract.setOnClickListener(v -> switchPanel(false));
        switchPanel(true);

        btnEmbed.setOnClickListener(v -> doEmbed());
        btnSaveStego.setOnClickListener(v -> saveBitmap(stegoData, "tuyin-stego", "image/png", 100));
        btnSaveExtracted.setOnClickListener(v -> saveBitmap(null, "tuyin-extracted", "image/png", 100));
        btnResetEmbed.setOnClickListener(v -> resetEmbed());
        btnResetExtract.setOnClickListener(v -> resetExtract());

        setContentView(root);
    }

    /* ================= UI 构建工具 ================= */

    private LinearLayout makeUploadBox(int card, int primary, String hint, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(card);
        shape.setCornerRadius(dp(20));
        box.setBackground(shape);
        box.setElevation(dp(6));
        box.setClickable(true);
        box.setOnClickListener(l);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setVisibility(View.GONE);
        box.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 上传引导：淡蓝圆钮 + 主提示 + 副提示
        LinearLayout iconWrap = new LinearLayout(this);
        iconWrap.setOrientation(LinearLayout.VERTICAL);
        iconWrap.setGravity(Gravity.CENTER);

        TextView icon = new TextView(this);
        icon.setText("＋");
        icon.setTextColor(card);
        icon.setTextSize(24);
        icon.setTypeface(null, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(primary);
        icon.setBackground(circle);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        iconLp.bottomMargin = dp(10);
        iconWrap.addView(icon, iconLp);

        TextView placeholder = new TextView(this);
        placeholder.setText(hint);
        placeholder.setTextColor(color(R.color.tuyin_text));
        placeholder.setTextSize(13);
        placeholder.setTypeface(null, Typeface.BOLD);
        placeholder.setGravity(Gravity.CENTER);
        iconWrap.addView(placeholder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subHint = new TextView(this);
        subHint.setText("点击选择图片");
        subHint.setTextColor(color(R.color.tuyin_sub));
        subHint.setTextSize(11);
        subHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        iconWrap.addView(subHint, subLp);

        box.addView(iconWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        box.setTag(R.id.placeholder, iconWrap);
        box.setTag(R.id.preview, preview);
        return box;
    }

    private LinearLayout makeCard(int card) {
        LinearLayout cardView = new LinearLayout(this);
        cardView.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(card);
        shape.setCornerRadius(dp(18));
        cardView.setBackground(shape);
        cardView.setElevation(dp(5));
        cardView.setPadding(dp(16), dp(14), dp(16), dp(14));
        return cardView;
    }

    private LinearLayout makePreviewCard(int card, String label, ImageView img) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(card);
        shape.setCornerRadius(dp(16));
        wrapper.setBackground(shape);
        wrapper.setElevation(dp(4));
        wrapper.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(color(R.color.tuyin_sub));
        lab.setTextSize(11);
        lab.setGravity(Gravity.CENTER);
        wrapper.addView(lab, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        imgLp.topMargin = dp(6);
        wrapper.addView(img, imgLp);
        return wrapper;
    }

    private Button primaryButton(String label, int primary, int card) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(card);
        b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setAllCaps(false);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(primary);
        shape.setCornerRadius(dp(24));
        b.setBackground(shape);
        b.setElevation(dp(7));
        return b;
    }

    private Button ghostButton(String label, int accent, int card) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(accent);
        b.setTextSize(13);
        b.setAllCaps(false);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(card);
        shape.setCornerRadius(dp(22));
        shape.setStroke(dp(1), accent);
        b.setBackground(shape);
        b.setElevation(dp(4));
        return b;
    }

    private void addParamSlider(LinearLayout parent, String label, int min, int max,
                                int def, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(color(R.color.tuyin_sub));
        lab.setTextSize(12);
        row.addView(lab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = new TextView(this);
        val.setTextColor(color(R.color.tuyin_text));
        val.setTextSize(12);
        val.setText(String.valueOf(def));
        row.addView(val);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(def - min);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        parent.addView(bar, lp);
        bar.setOnSeekBarChangeListener(new SimpleBar() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                val.setText(String.valueOf(min + progress));
            }
        });
        if ("ppb".equals(key)) { ppbBar = bar; ppbVal = val; }
        else if ("repeat".equals(key)) { repeatBar = bar; repeatVal = val; }
        else if ("nsym".equals(key)) { nsymBar = bar; nsymVal = val; }
    }

    private EditText numberInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(13);
        et.setGravity(Gravity.CENTER);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color(R.color.tuyin_card));
        shape.setCornerRadius(dp(10));
        shape.setStroke(dp(1), color(R.color.tuyin_seg_line));
        et.setBackground(shape);
        et.setPadding(dp(10), 0, dp(10), 0);
        return et;
    }

    private LinearLayout.LayoutParams cardLp(int top, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        lp.leftMargin = dp(left);
        return lp;
    }

    private LinearLayout.LayoutParams wrapLp(int top, int left, int gravity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        lp.leftMargin = dp(left);
        lp.gravity = gravity;
        return lp;
    }

    private LinearLayout.LayoutParams btnLp(int top, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.topMargin = dp(top);
        lp.leftMargin = dp(left);
        return lp;
    }

    private abstract static class SimpleBar implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int color(int id) {
        return getResources().getColor(id);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /* ================= 面板切换 ================= */

    private void switchPanel(boolean embed) {
        panelEmbed.setVisibility(embed ? View.VISIBLE : View.GONE);
        panelExtract.setVisibility(embed ? View.GONE : View.VISIBLE);
        int text = color(R.color.tuyin_text);
        int primary = color(R.color.tuyin_primary);
        tabEmbed.setTextColor(embed ? primary : text);
        tabExtract.setTextColor(embed ? text : primary);
        tabEmbed.setAlpha(embed ? 1f : 0.45f);
        tabExtract.setAlpha(embed ? 0.45f : 1f);
    }

    /* ================= 选图 ================= */

    private void pick(int code) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_image_title)), code);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == PICK_COVER) {
                coverData = RacImages.decodeUri(this, uri, 0);
                showPreview(coverBoxImg, coverBoxText, coverData);
                showPreview(coverPreview, null, coverData);
                payloadBytes = 0;
                updateCapacityMeter();
            } else if (requestCode == PICK_SECRET) {
                secretData = RacImages.decodeUri(this, uri, RacImages.SECRET_MAX);
                showPreview(secretBoxImg, secretBoxText, secretData);
                showPreview(secretPreview, null, secretData);
                updateCapacityMeter();
            } else if (requestCode == PICK_STEGO) {
                stegoData = RacImages.decodeUri(this, uri, 0);
                showPreview(stegoBoxImg, stegoBoxText, stegoData);
                showPreview(stegoPreview2, null, stegoData);
            }
        } catch (Exception e) {
            toast("读取图片失败：" + e.getMessage());
        }
    }

    private void showPreview(ImageView img, View placeholder, RacCore.ImageData data) {
        Bitmap bmp = RacImages.imageDataToBitmap(data);
        img.setImageBitmap(bmp);
        img.setVisibility(View.VISIBLE);
        if (placeholder != null) placeholder.setVisibility(View.GONE);
    }

    private void updateCapacityMeter() {
        if (coverData == null) {
            capacityText.setText("—");
            capacityBar.setProgress(0);
            return;
        }
        RacCore.Options opts = currentOptions();
        capacityBytes = RacCore.capacityBytes(coverData.width, coverData.height, opts);
        capacityText.setText(capacityBytes + " B");
        if (payloadBytes > 0 && capacityBytes > 0) {
            int pct = (int) Math.min(100, payloadBytes * 100 / capacityBytes);
            capacityBar.setProgress(pct);
        } else {
            capacityBar.setProgress(0);
        }
    }

    private RacCore.Options currentOptions() {
        RacCore.Options o = new RacCore.Options();
        int slider = tierSlider.getProgress();
        int[] tier = sliderToTier(slider);
        o.ppb = tier[0];
        o.repeat = tier[1];
        o.nsym = tier[2];
        if (customRow.getVisibility() == View.VISIBLE) {
            o.ppb = ppbBar.getProgress() + 1;
            o.repeat = repeatBar.getProgress() + 1;
            o.nsym = nsymBar.getProgress() + 8;
        }
        o.marginMin = 40;
        o.marginGain = 1.4;
        o.marginMax = 200;
        o.secretMax = RacImages.SECRET_MAX;
        return o;
    }

    /** 对齐 GUI sliderToTier。 */
    private int[] sliderToTier(int value) {
        if (value <= 50) {
            double t = value / 50.0;
            return new int[] { (int) Math.round(6 - t * 4), 1, (int) Math.round(32 + t * 16) };
        }
        double t = (value - 50) / 50.0;
        return new int[] { (int) Math.round(2 - t), t < 0.5 ? 1 : 3, 48 };
    }

    private void updateTierName(int progress) {
        int[] tier = sliderToTier(progress);
        if (tier[0] >= 5) tierName.setText("容量优先");
        else if (tier[0] <= 1 && tier[1] >= 3) tierName.setText("抗压缩");
        else if (tier[0] <= 2 && tier[2] >= 48) tierName.setText("均衡");
        else tierName.setText("自定义 " + tier[0] + "/" + tier[1] + "/" + tier[2]);
    }

    /* ================= 嵌入 ================= */

    private void doEmbed() {
        if (coverData == null || secretData == null) {
            toast("请先选择封面图和秘密图");
            return;
        }
        RacCore.Options opts = currentOptions();
        int cap = RacCore.capacityBytes(coverData.width, coverData.height, opts);
        if (cap < 64) {
            embedStatus.setTextColor(Color.parseColor("#E5484D"));
            embedStatus.setText("封面太小，无法嵌入");
            return;
        }
        btnEmbed.setEnabled(false);
        embedStatus.setTextColor(color(R.color.tuyin_sub));
        embedStatus.setText("正在嵌入…");
        new Thread(() -> {
            try {
                byte[] jpeg = RacImages.fitSecretJpeg(secretData, cap, opts.secretMax);
                if (jpeg == null) {
                    runOnUiThread(() -> {
                        embedStatus.setTextColor(Color.parseColor("#E5484D"));
                        embedStatus.setText("秘密图太大，塞不进这张封面（可把画质档位往左调）");
                    });
                    return;
                }
                byte[] payload = RacCore.buildPayload(jpeg, opts);
                RacCore.ImageData stego = RacCore.embed(coverData, payload, opts);
                payloadBytes = payload.length;
                stegoData = stego;
                runOnUiThread(() -> {
                    stegoPreview.setImageBitmap(RacImages.imageDataToBitmap(stego));
                    updateCapacityMeter();
                    embedStatus.setTextColor(Color.parseColor("#0086FF"));
                    embedStatus.setText("嵌入完成，可保存隐写图或切换“提取”验证");
                    toast("嵌入完成");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    embedStatus.setTextColor(Color.parseColor("#E5484D"));
                    embedStatus.setText("嵌入失败：" + e.getMessage());
                });
            } finally {
                runOnUiThread(() -> btnEmbed.setEnabled(true));
            }
        }).start();
    }

    /* ================= 提取 ================= */

    private void doExtract() {
        if (stegoData == null) {
            toast("请先选择隐写图");
            return;
        }
        btnExtract.setEnabled(false);
        extractStatus.setTextColor(color(R.color.tuyin_sub));
        extractStatus.setText("正在提取…");
        int manualW = parseDim(coverWInput);
        int manualH = parseDim(coverHInput);
        new Thread(() -> {
            try {
                RacCore.ImageData working = stegoData;
                if (manualW >= 16 && manualH >= 16) {
                    working = RacImages.resizeImageData(stegoData, manualW, manualH);
                } else {
                    byte[] rgb = toRgb(stegoData.rgba);
                    int[] detected = RacRuler.detectRulerSize(rgb, stegoData.width, stegoData.height);
                    if (detected != null
                            && (detected[0] != stegoData.width || detected[1] != stegoData.height)) {
                        working = RacImages.resizeImageData(stegoData, detected[0], detected[1]);
                    }
                }
                RacCore.ExtractResult res = RacCore.extract(working);
                if (res == null) {
                    runOnUiThread(() -> {
                        extractStatus.setTextColor(Color.parseColor("#E5484D"));
                        extractStatus.setText("未找到隐藏图片：图片可能不是图隐产物，或已被大幅修改");
                    });
                    return;
                }
                final Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(res.jpeg, 0, res.jpeg.length);
                runOnUiThread(() -> {
                    extractedBitmap = bmp;
                    ImageView resultImg = findResultPreview();
                    resultImg.setImageBitmap(bmp);
                    extractStatus.setTextColor(Color.parseColor("#0086FF"));
                    extractStatus.setText("提取成功，可保存到相册");
                    toast("提取成功");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    extractStatus.setTextColor(Color.parseColor("#E5484D"));
                    extractStatus.setText("提取失败：" + e.getMessage());
                });
            } finally {
                runOnUiThread(() -> btnExtract.setEnabled(true));
            }
        }).start();
    }

    private ImageView findResultPreview() {
        LinearLayout row = (LinearLayout) panelExtract.getChildAt(panelExtract.getChildCount() - 1);
        return (ImageView) ((ViewGroup) row.getChildAt(1)).getChildAt(1);
    }

    private byte[] toRgb(byte[] rgba) {
        byte[] rgb = new byte[rgba.length / 4 * 3];
        for (int i = 0; i < rgba.length / 4; i++) {
            rgb[i * 3] = rgba[i * 4];
            rgb[i * 3 + 1] = rgba[i * 4 + 1];
            rgb[i * 3 + 2] = rgba[i * 4 + 2];
        }
        return rgb;
    }

    private int parseDim(EditText et) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /* ================= 保存 ================= */

    private void saveBitmap(RacCore.ImageData imageData, String name, String mime, int quality) {
        Bitmap bmp = null;
        if (imageData != null) {
            bmp = RacImages.imageDataToBitmap(imageData);
        } else if (extractedBitmap != null) {
            bmp = extractedBitmap;
        } else if (stegoData != null) {
            bmp = RacImages.imageDataToBitmap(stegoData);
        }
        if (bmp == null) {
            toast("还没有可保存的图片");
            return;
        }
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQ_WRITE);
            return;
        }
        boolean ok = RacImages.saveBitmapToGallery(this, bmp, name, mime, quality);
        toast(ok ? getString(R.string.saved_to_gallery) : getString(R.string.save_failed));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (extractedBitmap != null) {
                RacImages.saveBitmapToGallery(this, extractedBitmap, "tuyin-extracted", "image/png", 100);
            } else if (stegoData != null) {
                RacImages.saveBitmapToGallery(this, RacImages.imageDataToBitmap(stegoData), "tuyin-stego", "image/png", 100);
            }
        } else {
            toast(getString(R.string.save_denied));
        }
    }

    /* ================= 重置 ================= */

    private void resetEmbed() {
        coverData = null;
        secretData = null;
        stegoData = null;
        payloadBytes = 0;
        coverPreview.setImageBitmap(null);
        coverBoxImg.setImageBitmap(null);
        coverBoxImg.setVisibility(View.GONE);
        coverBoxText.setVisibility(View.VISIBLE);
        secretPreview.setImageBitmap(null);
        secretBoxImg.setImageBitmap(null);
        secretBoxImg.setVisibility(View.GONE);
        secretBoxText.setVisibility(View.VISIBLE);
        stegoPreview.setImageBitmap(null);
        updateCapacityMeter();
        embedStatus.setText("");
    }

    private void resetExtract() {
        stegoData = null;
        extractedBitmap = null;
        stegoPreview2.setImageBitmap(null);
        stegoBoxImg.setImageBitmap(null);
        stegoBoxImg.setVisibility(View.GONE);
        stegoBoxText.setVisibility(View.VISIBLE);
        ImageView resImg = findResultPreview();
        resImg.setImageBitmap(null);
        coverWInput.setText("");
        coverHInput.setText("");
        extractStatus.setText("");
    }
}
