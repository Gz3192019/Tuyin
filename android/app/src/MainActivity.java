package com.tuopzf.tuyin;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
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

import java.io.ByteArrayOutputStream;
import java.util.function.IntConsumer;

/**
 * 图隐 — 原生隐写工作台（Material 风格）。
 * 卡片：图标瓦片 + 双行文字（标题/副标题）+ 右侧状态；
 * 图片区：上传/结果卡显示图片后，卡片高度完全跟随图片宽高比自适应
 * （FIT_CENTER 完整显示、无高度上限，与 web 端 width:100%; height:auto 一致），不裁切、不挤压。
 * v1.4：
 *  - 可用容量随画质滑条 / 自定义参数实时刷新
 *  - 封面增强改为 Material 单选下拉并真实生效（重新按档位处理封面）
 *  - 原图尺寸（可选）：只填一项会提示，生效时有明确反馈
 *  - 新增「通道模拟 · 验证鲁棒性」：缩放 → JPEG 重压缩 → 再提取（对齐 web 端 sim 面板）
 */
public class MainActivity extends Activity {

    private static final int PICK_COVER = 100;
    private static final int PICK_SECRET = 101;
    private static final int PICK_STEGO = 102;
    private static final int REQ_WRITE = 200;

    // 封面增强档位（对齐 web coverUpscale：0=不放大，其余=长边上限）
    private static final String[] ENHANCE_LABELS = {
            "不放大", "长边 ≤ 512", "长边 ≤ 768", "长边 ≤ 1024", "长边 ≤ 1280", "长边 ≤ 2048" };
    private static final int[] ENHANCE_VALUES = { 0, 512, 768, 1024, 1280, 2048 };

    // 通道模拟：缩放 / JPEG 质量档位（对齐 web simScale / simQuality）
    private static final String[] SCALE_LABELS = {
            "不缩放（100%）", "缩放 90%", "缩放 80%", "缩放 70%", "缩放 60%", "缩放 50%" };
    private static final double[] SCALE_VALUES = { 1.0, 0.9, 0.8, 0.7, 0.6, 0.5 };
    private static final String[] QUALITY_LABELS = {
            "q100（无损）", "q90", "q80", "q70", "q60", "q50（很狠）", "q30" };
    private static final int[] QUALITY_VALUES = { 100, 90, 80, 70, 60, 50, 30 };

    // 状态
    private RacCore.ImageData coverData;
    private RacCore.ImageData secretData;
    private RacCore.ImageData stegoData;
    private Bitmap extractedBitmap;
    private Bitmap simRecoveredBitmap;
    private Uri coverUri;
    private int payloadBytes;
    private int capacityBytes = 0;
    private int enhanceIndex = 0;
    private int scaleIndex = 0;
    private int qualityIndex = 5;

    // UI
    private LinearLayout panelEmbed;
    private LinearLayout panelExtract;
    private LinearLayout stegoResultBox, extractResultBox;
    private LinearLayout tabEmbed;
    private LinearLayout tabExtract;
    private LinearLayout coverBox;
    private LinearLayout secretBox;
    private LinearLayout stegoBox;
    private FrameLayout stegoResultArea;
    private FrameLayout extractResultArea;
    private LinearLayout.LayoutParams coverLp;
    private LinearLayout.LayoutParams secretLp;
    private LinearLayout.LayoutParams stegoLp;
    private LinearLayout.LayoutParams stegoResultAreaLp;
    private LinearLayout.LayoutParams extractResultAreaLp;
    private ImageView coverBoxImg;
    private ImageView secretBoxImg;
    private ImageView stegoBoxImg;
    private ImageView stegoResultImg;
    private ImageView extractResultImg;
    private View coverBoxText;
    private View secretBoxText;
    private View stegoBoxText;
    private View stegoResultText;
    private View extractResultText;
    private TextView capacityText;
    private ProgressBar capacityBar;
    private TextView embedStatus;
    private TextView extractStatus;
    private SeekBar tierSlider;
    private TextView tierName;
    private TextView enhanceSelect;
    private LinearLayout customRow;
    private SeekBar ppbBar;
    private TextView ppbVal;
    private SeekBar repeatBar;
    private TextView repeatVal;
    private SeekBar nsymBar;
    private TextView nsymVal;
    private EditText coverWInput;
    private EditText coverHInput;
    private TextView btnEmbed;
    private TextView btnSaveStego;
    private TextView btnSaveExtracted;
    private TextView btnExtract;
    private TextView btnResetEmbed;
    private TextView btnResetExtract;
    // 通道模拟
    private TextView btnSim;
    private TextView simStatus;
    private TextView simScaleSel;
    private TextView simQualitySel;
    private LinearLayout simAttackedBox, simRecoveredBox;
    private FrameLayout simAttackedArea, simRecoveredArea;
    private LinearLayout.LayoutParams simAttackedAreaLp, simRecoveredAreaLp;
    private ImageView simAttackedImg, simRecoveredImg;
    private View simAttackedText, simRecoveredText;

    /** 卡片统一圆角（dp）。 */
    private static final int CARD_RADIUS = 16;
    /** 图片卡占位高度（dp，未选图时）。 */
    private static final int IMG_CARD_H = 300;
    /** 结果卡图片区初始高度 = 占位卡高 - 标题行高。 */
    private static final int RESULT_AREA_H = IMG_CARD_H - 46;
    /** 统一卡片间距。 */
    private static final int GAP = 12;
    /** 投影深度（dp）。 */
    private static final int SHADOW_DEPTH = 3;

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

        // ---- 工作区（滚动区，置于最底层，从顶部开始） ----
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 顶部渐隐覆盖层（自上而下由实渐透，滚动内容从下方透出） ----
        FrameLayout headerOverlay = new FrameLayout(this);
        int bgR = Color.red(bg), bgG = Color.green(bg), bgB = Color.blue(bg);
        GradientDrawable fade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { bg, Color.argb(0, bgR, bgG, bgB) });
        headerOverlay.setBackground(fade);

        LinearLayout headerInner = new LinearLayout(this);
        headerInner.setOrientation(LinearLayout.VERTICAL);
        headerInner.setPadding(dp(20), dp(20), dp(20), dp(4));
        headerOverlay.addView(headerInner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        headerInner.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(text);
        title.setTextSize(34);
        title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView aboutBtn = new TextView(this);
        aboutBtn.setText(R.string.about_title);
        aboutBtn.setTextColor(primary);
        aboutBtn.setTextSize(14);
        aboutBtn.setGravity(Gravity.CENTER);
        aboutBtn.setClickable(true);
        aboutBtn.setBackground(rippleBg(14, translucent(R.color.tuyin_card, 245), 3));
        aboutBtn.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        titleRow.addView(aboutBtn, new LinearLayout.LayoutParams(dp(72), dp(38)));

        TextView slogan = new TextView(this);
        slogan.setText(R.string.home_slogan);
        slogan.setTextColor(sub);
        slogan.setTextSize(13);
        headerInner.addView(slogan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(headerOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(112)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(120));
        scroll.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 顶部透明占位：把功能区顶到渐隐层下缘之下（初始不遮挡）
        View topSpacer = new View(this);
        content.addView(topSpacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));

        // ================ 嵌入面板 ================
        panelEmbed = new LinearLayout(this);
        panelEmbed.setOrientation(LinearLayout.VERTICAL);
        content.addView(panelEmbed, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 封面图（全宽大卡，选图后高度跟随图片比例） ----
        coverBox = makeImageCard(card, primary, "封面图 · 宿主", "点击选择要藏秘密图的封面", v -> pick(PICK_COVER));
        coverLp = imgCardLp(0);
        panelEmbed.addView(coverBox, coverLp);
        coverBoxText = (View) coverBox.getTag(R.id.placeholder);
        coverBoxImg = (ImageView) coverBox.getTag(R.id.preview);

        // ---- 秘密图（全宽大卡） ----
        secretBox = makeImageCard(card, primary, "秘密图 · 被隐藏", "点击选择要藏进封面的图片", v -> pick(PICK_SECRET));
        secretLp = imgCardLp(GAP);
        panelEmbed.addView(secretBox, secretLp);
        secretBoxText = (View) secretBox.getTag(R.id.placeholder);
        secretBoxImg = (ImageView) secretBox.getTag(R.id.preview);

        // ---- 可用容量卡（图标瓦片 + 双行文字 + 右侧状态 + 进度条） ----
        LinearLayout capCard = makeCard(card);
        panelEmbed.addView(capCard, cardLp(0));

        LinearLayout capRow = makeTileRow(card, "容", "可用容量", "封面可隐藏的最大字节数，随参数实时更新");
        capCard.addView(capRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        capacityText = (TextView) capRow.getTag(R.id.trailing);
        capacityText.setTextColor(primary);
        capacityText.setText("0 B");
        capacityText.setTextSize(13);
        capacityText.setTypeface(null, Typeface.BOLD);

        capacityBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        GradientDrawable barTrack = new GradientDrawable();
        barTrack.setColor(translucent(R.color.tuyin_seg_bg, 210));
        barTrack.setCornerRadius(dp(4));
        capacityBar.setProgressDrawable(barTrack);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        barLp.topMargin = dp(12);
        barLp.leftMargin = dp(58);
        capCard.addView(capacityBar, barLp);

        // ---- 封面增强卡（图标瓦片 + 双行文字 + 右侧 Material 单选下拉） ----
        LinearLayout enhanceCard = makeCard(card);
        panelEmbed.addView(enhanceCard, cardLp(GAP));

        LinearLayout enhanceRow = makeTileRow(card, "增", "封面增强", "嵌入前把封面压缩到指定长边");
        enhanceCard.addView(enhanceRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        enhanceSelect = makeSelectField(ENHANCE_LABELS, 0, idx -> {
            enhanceIndex = idx;
            if (coverUri != null) reapplyCover();
        });
        enhanceRow.addView(enhanceSelect, new LinearLayout.LayoutParams(dp(124), dp(44)));

        // ---- 画质与容量卡（图标瓦片 + 双行文字 + 右侧档位 + 滑条） ----
        LinearLayout tierCard = makeCard(card);
        panelEmbed.addView(tierCard, cardLp(GAP));

        LinearLayout tierRow = makeTileRow(card, "质", "画质与容量", "画质与可嵌入容量的平衡，滑动即时重算容量");
        tierCard.addView(tierRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tierName = (TextView) tierRow.getTag(R.id.trailing);
        tierName.setTextSize(13);
        tierName.setTypeface(null, Typeface.BOLD);

        tierSlider = new SeekBar(this);
        tierSlider.setMax(100);
        tierSlider.setProgress(50);
        LinearLayout.LayoutParams tierLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tierLp.topMargin = dp(8);
        tierLp.leftMargin = dp(58);
        tierCard.addView(tierSlider, tierLp);
        tierSlider.setOnSeekBarChangeListener(new SimpleBar() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTierName(progress);
                updateCapacityMeter();
            }
        });
        updateTierName(50);

        // ---- 自定义参数（胶囊） ----
        TextView customToggle = new TextView(this);
        customToggle.setText("自定义参数");
        customToggle.setTextColor(primary);
        customToggle.setTextSize(13);
        customToggle.setTypeface(null, Typeface.BOLD);
        customToggle.setGravity(Gravity.CENTER);
        customToggle.setClickable(true);
        customToggle.setBackground(rippleBg(16, translucent(R.color.tuyin_card, 245), 3));
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        toggleLp.topMargin = dp(GAP);
        panelEmbed.addView(customToggle, toggleLp);

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
            updateCapacityMeter();
        });

        // ---- 开始嵌入（大按钮） ----
        btnEmbed = primaryButton("开始嵌入", primary, card);
        btnSaveStego = ghostButton("保存到相册", primary, card);
        btnResetEmbed = ghostButton("重置", sub, card);
        panelEmbed.addView(btnEmbed, btnLp(GAP));
        LinearLayout embedBtnRow = new LinearLayout(this);
        embedBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        embedBtnRow.setGravity(Gravity.CENTER);
        panelEmbed.addView(embedBtnRow, wrapLp(0, Gravity.CENTER));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        saveLp.rightMargin = dp(GAP / 2);
        embedBtnRow.addView(btnSaveStego, saveLp);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        resetLp.leftMargin = dp(GAP / 2);
        embedBtnRow.addView(btnResetEmbed, resetLp);

        embedStatus = new TextView(this);
        embedStatus.setTextColor(sub);
        embedStatus.setTextSize(12);
        embedStatus.setGravity(Gravity.CENTER);
        panelEmbed.addView(embedStatus, wrapLp(0, Gravity.CENTER));

        // ---- 隐写结果（全宽大卡，嵌入后显示，高度跟随图片比例） ----
        stegoResultBox = makeResultCard(card, "隐写结果", "嵌入完成后在这里显示");
        panelEmbed.addView(stegoResultBox, cardLp(GAP));
        stegoResultArea = (FrameLayout) stegoResultBox.getTag(R.id.imageArea);
        stegoResultAreaLp = (LinearLayout.LayoutParams) stegoResultArea.getLayoutParams();
        stegoResultText = (View) stegoResultBox.getTag(R.id.placeholder);
        stegoResultImg = (ImageView) stegoResultBox.getTag(R.id.preview);

        // ---- 通道模拟 · 验证鲁棒性（缩放 → JPEG 重压缩 → 再提取） ----
        LinearLayout simCard = makeCard(card);
        panelEmbed.addView(simCard, cardLp(GAP));

        LinearLayout simRow = makeTileRow(card, "验", "通道模拟 · 验证鲁棒性",
                "模拟真实平台链路：缩放 → JPEG 重压缩 → 再提取");
        simCard.addView(simRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        simScaleSel = makeSelectField(SCALE_LABELS, 0, idx -> scaleIndex = idx);
        simCard.addView(makeSelectRow("缩放", simScaleSel), wrapLp(10, Gravity.CENTER));

        simQualitySel = makeSelectField(QUALITY_LABELS, 5, idx -> qualityIndex = idx);
        simCard.addView(makeSelectRow("JPEG 重压缩质量", simQualitySel), wrapLp(8, Gravity.CENTER));

        btnSim = primaryButton("模拟通道并提取", primary, card);
        simCard.addView(btnSim, btnLp(14));
        btnSim.setOnClickListener(v -> doSimulate());

        simStatus = new TextView(this);
        simStatus.setTextColor(sub);
        simStatus.setTextSize(13);
        simStatus.setGravity(Gravity.CENTER);
        simCard.addView(simStatus, wrapLp(10, Gravity.CENTER));

        // 传输后的图像（模拟后显示被通道处理过的隐写图）
        simAttackedBox = makeResultCard(card, "传输后的图像", "模拟后在这里显示被通道处理过的图像");
        panelEmbed.addView(simAttackedBox, cardLp(GAP));
        simAttackedArea = (FrameLayout) simAttackedBox.getTag(R.id.imageArea);
        simAttackedAreaLp = (LinearLayout.LayoutParams) simAttackedArea.getLayoutParams();
        simAttackedText = (View) simAttackedBox.getTag(R.id.placeholder);
        simAttackedImg = (ImageView) simAttackedBox.getTag(R.id.preview);

        // 提取结果 · 恢复的秘密图
        simRecoveredBox = makeResultCard(card, "提取结果 · 恢复的秘密图", "模拟提取成功后在这里显示");
        panelEmbed.addView(simRecoveredBox, cardLp(GAP));
        simRecoveredArea = (FrameLayout) simRecoveredBox.getTag(R.id.imageArea);
        simRecoveredAreaLp = (LinearLayout.LayoutParams) simRecoveredArea.getLayoutParams();
        simRecoveredText = (View) simRecoveredBox.getTag(R.id.placeholder);
        simRecoveredImg = (ImageView) simRecoveredBox.getTag(R.id.preview);

        TextView btnSaveSim = ghostButton("保存恢复图到相册", primary, card);
        LinearLayout simSaveRow = new LinearLayout(this);
        simSaveRow.setOrientation(LinearLayout.HORIZONTAL);
        simSaveRow.setGravity(Gravity.CENTER);
        panelEmbed.addView(simSaveRow, wrapLp(GAP, Gravity.CENTER));
        LinearLayout.LayoutParams simSaveLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        simSaveRow.addView(btnSaveSim, simSaveLp);
        btnSaveSim.setOnClickListener(v -> saveBitmap(simRecoveredBitmap, "tuyin-recovered", "image/png", 100));

        // ================ 提取面板 ================
        panelExtract = new LinearLayout(this);
        panelExtract.setOrientation(LinearLayout.VERTICAL);
        panelExtract.setVisibility(View.GONE);
        content.addView(panelExtract, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 隐写图（全宽大卡） ----
        stegoBox = makeImageCard(card, primary, "隐写图 · 要解密的图片", "点击选择需要解密的图片", v -> pick(PICK_STEGO));
        stegoLp = imgCardLp(0);
        panelExtract.addView(stegoBox, stegoLp);
        stegoBoxText = (View) stegoBox.getTag(R.id.placeholder);
        stegoBoxImg = (ImageView) stegoBox.getTag(R.id.preview);

        // ---- 原图尺寸卡（图标瓦片 + 双行文字 + 输入） ----
        LinearLayout sizeCard = makeCard(card);
        panelExtract.addView(sizeCard, cardLp(GAP));

        LinearLayout sizeRow = makeTileRow(card, "尺", "原图尺寸（可选）", "恢复被缩放的原图比例；留空则自动检测");
        sizeCard.addView(sizeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout sizeInputRow = new LinearLayout(this);
        sizeInputRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeInputRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sizeInputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sizeInputLp.topMargin = dp(10);
        sizeInputLp.leftMargin = dp(58);
        sizeCard.addView(sizeInputRow, sizeInputLp);

        coverWInput = numberInput("宽");
        sizeInputRow.addView(coverWInput, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView xMark = new TextView(this);
        xMark.setText(" × ");
        xMark.setTextColor(sub);
        sizeInputRow.addView(xMark);
        coverHInput = numberInput("高");
        sizeInputRow.addView(coverHInput, new LinearLayout.LayoutParams(0, dp(46), 1f));

        // ---- 提取秘密图（大按钮） ----
        btnExtract = primaryButton("提取秘密图", primary, card);
        btnSaveExtracted = ghostButton("保存到相册", primary, card);
        btnResetExtract = ghostButton("重置", sub, card);
        panelExtract.addView(btnExtract, btnLp(GAP));
        LinearLayout extractBtnRow = new LinearLayout(this);
        extractBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        extractBtnRow.setGravity(Gravity.CENTER);
        panelExtract.addView(extractBtnRow, wrapLp(0, Gravity.CENTER));
        LinearLayout.LayoutParams saveLp2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        saveLp2.rightMargin = dp(GAP / 2);
        extractBtnRow.addView(btnSaveExtracted, saveLp2);
        LinearLayout.LayoutParams resetLp2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        resetLp2.leftMargin = dp(GAP / 2);
        extractBtnRow.addView(btnResetExtract, resetLp2);

        extractStatus = new TextView(this);
        extractStatus.setTextColor(sub);
        extractStatus.setTextSize(12);
        extractStatus.setGravity(Gravity.CENTER);
        panelExtract.addView(extractStatus, wrapLp(0, Gravity.CENTER));

        // ---- 提取结果（全宽大卡，提取后显示，高度跟随图片比例） ----
        extractResultBox = makeResultCard(card, "提取结果", "提取成功后在这里显示");
        panelExtract.addView(extractResultBox, cardLp(GAP));
        extractResultArea = (FrameLayout) extractResultBox.getTag(R.id.imageArea);
        extractResultAreaLp = (LinearLayout.LayoutParams) extractResultArea.getLayoutParams();
        extractResultText = (View) extractResultBox.getTag(R.id.placeholder);
        extractResultImg = (ImageView) extractResultBox.getTag(R.id.preview);

        // ---------------- 底部 Material 3 悬浮导航 ----------------
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setGravity(Gravity.CENTER);
        seg.setPadding(dp(6), dp(4), dp(6), dp(4));
        seg.setBackground(glassBg(32, translucent(R.color.tuyin_card, 204)));

        tabEmbed = makeNavItem(R.drawable.ic_embed, R.string.tab_embed);
        tabExtract = makeNavItem(R.drawable.ic_extract, R.string.tab_extract);
        tabEmbed.setOnClickListener(v -> switchPanel(true));
        tabExtract.setOnClickListener(v -> switchPanel(false));
        tabEmbed.setOnTouchListener(pressScale());
        tabExtract.setOnTouchListener(pressScale());
        seg.addView(tabEmbed, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        seg.addView(tabExtract, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 液态玻璃容器：缩略模糊下层内容 + 胶囊（80% 透明、50% 模糊、左上高光、无投影）
        BlurView blurWrap = new BlurView(this);
        blurWrap.setPadding(dp(3), dp(3), dp(3), dp(3));
        blurWrap.addView(seg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams segLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        segLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        segLp.bottomMargin = dp(22);
        root.addView(blurWrap, segLp);
        // 实时刷新液态玻璃模糊（滚动 + 布局变化）
        scroll.setOnScrollChangeListener((v, sx, sy, ox, oy) -> blurWrap.invalidate());
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> blurWrap.invalidate());

        switchPanel(true);

        btnEmbed.setOnClickListener(v -> doEmbed());
        btnExtract.setOnClickListener(v -> doExtract());
        btnSaveStego.setOnClickListener(v -> saveBitmap(stegoData, "tuyin-stego", "image/png", 100));
        btnSaveExtracted.setOnClickListener(v -> saveBitmap(null, "tuyin-extracted", "image/png", 100));
        btnResetEmbed.setOnClickListener(v -> resetEmbed());
        btnResetExtract.setOnClickListener(v -> resetExtract());

        setContentView(root);
    }

    /* ================= UI 构建工具 ================= */

    /** 图片选择大卡：全宽、统一占位高度，选图后高度跟随图片比例。 */
    private LinearLayout makeImageCard(int card, int primary, String title, String subTitle,
                                       View.OnClickListener l) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setClickable(true);
        box.setBackground(rippleBg(CARD_RADIUS, translucent(R.color.tuyin_card, 250), SHADOW_DEPTH));
        box.setOnClickListener(l);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setVisibility(View.GONE);
        box.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout iconWrap = new LinearLayout(this);
        iconWrap.setOrientation(LinearLayout.VERTICAL);
        iconWrap.setGravity(Gravity.CENTER);

        TextView icon = new TextView(this);
        icon.setText("＋");
        icon.setTextColor(card);
        icon.setTextSize(26);
        icon.setTypeface(null, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(primary);
        icon.setBackground(circle);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        iconLp.bottomMargin = dp(12);
        iconWrap.addView(icon, iconLp);

        TextView placeholder = new TextView(this);
        placeholder.setText(title);
        placeholder.setTextColor(color(R.color.tuyin_text));
        placeholder.setTextSize(16);
        placeholder.setTypeface(null, Typeface.BOLD);
        placeholder.setGravity(Gravity.CENTER);
        iconWrap.addView(placeholder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subHint = new TextView(this);
        subHint.setText(subTitle);
        subHint.setTextColor(color(R.color.tuyin_sub));
        subHint.setTextSize(12);
        subHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(6);
        iconWrap.addView(subHint, subLp);

        box.addView(iconWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        box.setTag(R.id.placeholder, iconWrap);
        box.setTag(R.id.preview, preview);
        return box;
    }

    /** 结果展示大卡：标题行 + 图片区，图片区高度显示后跟随图片比例。 */
    private LinearLayout makeResultCard(int card, String title, String emptyHint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(shadowBg(CARD_RADIUS, translucent(R.color.tuyin_card, 250), SHADOW_DEPTH));

        TextView lab = new TextView(this);
        lab.setText(title);
        lab.setTextColor(color(R.color.tuyin_text));
        lab.setTextSize(14);
        lab.setTypeface(null, Typeface.BOLD);
        lab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labLp.topMargin = dp(12);
        box.addView(lab, labLp);

        FrameLayout imgArea = new FrameLayout(this);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setVisibility(View.GONE);
        imgArea.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView placeholder = new TextView(this);
        placeholder.setText(emptyHint);
        placeholder.setTextColor(color(R.color.tuyin_sub));
        placeholder.setTextSize(13);
        placeholder.setGravity(Gravity.CENTER);
        imgArea.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        box.addView(imgArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(RESULT_AREA_H)));

        box.setTag(R.id.placeholder, placeholder);
        box.setTag(R.id.preview, preview);
        box.setTag(R.id.imageArea, imgArea);
        return box;
    }

    /** 图标瓦片 + 双行文字行卡片（Material 列表项）。返回该行，trailing 通过 tag 获取。 */
    private LinearLayout makeTileRow(int card, String tileChar, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        // 左侧图标瓦片（深色圆角方块 + 白色单字）
        TextView tile = new TextView(this);
        tile.setText(tileChar);
        tile.setTextColor(Color.WHITE);
        tile.setTextSize(16);
        tile.setTypeface(null, Typeface.BOLD);
        tile.setGravity(Gravity.CENTER);
        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setShape(GradientDrawable.RECTANGLE);
        tileBg.setCornerRadius(dp(12));
        tileBg.setColor(Color.parseColor("#2B2B2B"));
        tile.setBackground(tileBg);
        row.addView(tile, new LinearLayout.LayoutParams(dp(46), dp(46)));

        // 中间：主标题 + 副标题
        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(color(R.color.tuyin_text));
        titleTv.setTextSize(15);
        titleTv.setTypeface(null, Typeface.BOLD);
        textWrap.addView(titleTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subTv = new TextView(this);
        subTv.setText(subtitle);
        subTv.setTextColor(color(R.color.tuyin_sub));
        subTv.setTextSize(12);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        textWrap.addView(subTv, subLp);

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = dp(12);
        row.addView(textWrap, textLp);

        // 右侧状态（调用方填充 spinner / 数值 / 档位名）
        TextView trailing = new TextView(this);
        trailing.setTextColor(color(R.color.tuyin_sub));
        trailing.setTextSize(12);
        trailing.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams trailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        trailLp.leftMargin = dp(8);
        row.addView(trailing, trailLp);

        row.setTag(R.id.trailing, trailing);
        return row;
    }

    /** 通用信息卡（Material：白底、16dp 圆角、投影）。 */
    private LinearLayout makeCard(int card) {
        LinearLayout cardView = new LinearLayout(this);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setBackground(shadowBg(CARD_RADIUS, translucent(R.color.tuyin_card, 250), SHADOW_DEPTH));
        cardView.setPadding(dp(16), dp(14), dp(16), dp(14));
        return cardView;
    }

    /** 主按钮：蓝色胶囊 + 水波纹 + 圆角投影。 */
    private TextView primaryButton(String label, int primary, int card) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(card);
        b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setBackground(rippleBg(26, primary, SHADOW_DEPTH + 1));
        return b;
    }

    /** 次按钮：白底 + 彩色描边 + 水波纹。 */
    private TextView ghostButton(String label, int accent, int card) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(accent);
        b.setTextSize(13);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        GradientDrawable body = shapeBg(22, translucent(R.color.tuyin_card, 250));
        body.setStroke(dp(1), accent);
        b.setBackground(rippleWrap(body, 22, 4));
        return b;
    }

    /** 悬浮导航项：图标 + 文字，选中蓝底胶囊。 */
    private LinearLayout makeNavItem(int iconRes, int labelRes) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(dp(18));
        pillBg.setColor(Color.TRANSPARENT);
        item.setBackground(pillBg);
        item.setPadding(dp(14), dp(6), dp(14), dp(6));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setColorFilter(color(R.color.tuyin_sub), PorterDuff.Mode.SRC_IN);
        item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(12);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(color(R.color.tuyin_sub));
        LinearLayout.LayoutParams labLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labLp.leftMargin = dp(6);
        item.addView(label, labLp);

        item.setTag(R.id.navIcon, icon);
        item.setTag(R.id.navLabel, label);
        item.setTag(R.id.navPillBg, pillBg);
        return item;
    }

    /** 按压反馈：按下轻微缩小。 */
    private View.OnTouchListener pressScale() {
        return (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        };
    }

    /** Material 单选下拉：TextView 显示当前值，点击弹出单选对话框（比系统 Spinner 美观统一）。 */
    private TextView makeSelectField(String[] labels, int defIndex, IntConsumer onChange) {
        TextView sel = new TextView(this);
        sel.setText(labels[defIndex]);
        sel.setTextColor(color(R.color.tuyin_primary));
        sel.setTextSize(13);
        sel.setTypeface(null, Typeface.BOLD);
        sel.setGravity(Gravity.CENTER);
        sel.setPadding(dp(12), 0, dp(12), 0);
        sel.setBackground(rippleBg(14, translucent(R.color.tuyin_seg_bg, 210), 2));
        sel.setClickable(true);
        sel.setOnClickListener(v -> {
            int cur = 0;
            for (int i = 0; i < labels.length; i++) {
                if (labels[i].equals(sel.getText().toString())) { cur = i; break; }
            }
            final int[] chosen = { cur };
            new AlertDialog.Builder(this)
                    .setTitle("请选择")
                    .setSingleChoiceItems(labels, cur, (d, w) -> chosen[0] = w)
                    .setPositiveButton("确定", (d, w) -> {
                        sel.setText(labels[chosen[0]]);
                        onChange.accept(chosen[0]);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        return sel;
    }

    /** 选择行：左标签 + 右侧选择控件。 */
    private LinearLayout makeSelectRow(String label, TextView sel) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(color(R.color.tuyin_sub));
        lab.setTextSize(13);
        row.addView(lab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(sel, new LinearLayout.LayoutParams(dp(150), dp(44)));
        return row;
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
                if (customRow != null && customRow.getVisibility() == View.VISIBLE) {
                    updateCapacityMeter();
                }
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
        et.setBackground(shadowBg(10, translucent(R.color.tuyin_card, 250), 2));
        et.setPadding(dp(10), 0, dp(10), 0);
        return et;
    }

    /** 图片卡布局：全宽 + 占位高度 + 垂直间距。 */
    private LinearLayout.LayoutParams imgCardLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(IMG_CARD_H));
        lp.topMargin = dp(top);
        return lp;
    }

    /** 卡片布局：满宽 + 垂直间距。 */
    private LinearLayout.LayoutParams cardLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        return lp;
    }

    /** 行容器布局：MATCH_PARENT + 可选 gravity。 */
    private LinearLayout.LayoutParams wrapLp(int top, int gravity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        lp.gravity = gravity;
        return lp;
    }

    /** 主按钮布局：满宽 + 垂直间距。 */
    private LinearLayout.LayoutParams btnLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(top);
        return lp;
    }

    /**
     * 显示图片并让容器高度完全跟随图片宽高比（FIT_CENTER 完整显示，不裁切）。
     * 与 web 端 canvas 的 width:100%; height:auto 语义一致：高度 = 可用宽 × 高/宽，无上限。
     * 策略：先用已知容器宽度立即计算并设置高度（不依赖布局回调，必然生效），
     * 再挂一个根视图布局监听，等真正布局完成后用实测宽度精调一遍。
     */
    private void fitImage(ImageView img, View placeholder, Bitmap bmp,
                          View target, ViewGroup.LayoutParams targetLp, int restoreHp,
                          View box, ViewGroup.LayoutParams boxLp, int headerH) {
        if (bmp == null) return;
        img.setImageBitmap(bmp);
        img.setVisibility(View.VISIBLE);
        if (placeholder != null) placeholder.setVisibility(View.GONE);
        // 立即计算：内容区左右 padding 各 16dp，图片区宽度 = 屏宽 - 32dp
        int w = img.getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels - dp(32);
        if (w <= 0) return;
        int h = (int) (w * (float) bmp.getHeight() / bmp.getWidth());
        targetLp.height = h;
        target.requestLayout();
        // 显式同步设置卡片外壳高度（标题行 + 图片区），不依赖 wrap_content 重测
        if (boxLp != null && headerH > 0) {
            boxLp.height = h + dp(headerH);
            box.requestLayout();
        }
        // 布局完成后用实测宽度精调（覆盖屏宽估算的偏差）
        final int guessW = w;
        View host = target.getRootView();
        if (host == null) return;
        host.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                int rw = img.getWidth();
                if (rw <= 0) return; // 还没布局，等下一次
                host.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (rw == guessW) return; // 与估算一致，无需精调
                int rh = (int) (rw * (float) bmp.getHeight() / bmp.getWidth());
                targetLp.height = rh;
                target.requestLayout();
                if (boxLp != null && headerH > 0) {
                    boxLp.height = rh + dp(headerH);
                    box.requestLayout();
                }
            }
        });
    }

    private abstract static class SimpleBar implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    }

    /** 液态玻璃模糊层：把下层滚动内容缩略采样后放大绘制，形成毛玻璃效果。 */
    private final class BlurView extends FrameLayout {
        private Bitmap small;
        private final Paint blurPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

        BlurView(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override protected void onDraw(Canvas canvas) {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent == null || parent.getChildCount() == 0) return;
            View stack = parent.getChildAt(0);
            if (!(stack instanceof ScrollView)) return;
            ScrollView sv = (ScrollView) stack;
            View content = sv.getChildCount() > 0 ? sv.getChildAt(0) : null;
            if (content == null) return;
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            int sw = Math.max(1, w / 5), sh = Math.max(1, h / 5);
            if (small == null || small.getWidth() != sw || small.getHeight() != sh) {
                small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            }
            Canvas c = new Canvas(small);
            // 滚动补偿：ScrollView 滚动 = 内容平移，实时读取 scrollY（不依赖显示列表缓存）
            c.translate(-sv.getScrollX(), -sv.getScrollY());
            // 对齐模糊区（全尺寸空间）
            c.translate(-getLeft(), -getTop());
            // 缩略采样
            c.scale(1f / 5f, 1f / 5f);
            content.draw(c);
            canvas.drawBitmap(small, null, new android.graphics.Rect(0, 0, w, h), blurPaint);
            super.onDraw(canvas);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int color(int id) {
        return getResources().getColor(id);
    }

    /** 资源色按指定 alpha 转半透明。 */
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

    /** 圆角投影背景。 */
    private Drawable shadowBg(int radiusDp, int fillColor, int depthDp) {
        return shadowWrap(shapeBg(radiusDp, fillColor), radiusDp, depthDp);
    }

    /** 圆角水波纹背景（Material ripple）。 */
    private Drawable rippleBg(int radiusDp, int fillColor, int depthDp) {
        return rippleWrap(shapeBg(radiusDp, fillColor), radiusDp, depthDp);
    }

    /** 液态玻璃胶囊：半透明底 + 左上高光 + 弧形描边（无投影）。 */
    private Drawable glassBg(int radiusDp, int fillColor) {
        GradientDrawable body = shapeBg(radiusDp, fillColor);
        GradientDrawable gloss = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] { Color.argb(55, 255, 255, 255), Color.argb(0, 255, 255, 255) });
        gloss.setCornerRadius(dp(radiusDp));
        // 弧形描边层（透明底 + 白色半透明轮廓，置于最上层）
        GradientDrawable outline = shapeBg(radiusDp, Color.TRANSPARENT);
        outline.setStroke(dp(1), Color.argb(70, 255, 255, 255));
        return new LayerDrawable(new Drawable[] { body, gloss, outline });
    }

    /** 圆角水波纹背景（Material ripple + 轻投影）。 */
    private Drawable rippleWrap(GradientDrawable body, int radiusDp, int depthDp) {
        int d = dp(depthDp);
        int[] alphas = { 2, 4, 6, 9 };
        GradientDrawable mask = shapeBg(radiusDp, Color.WHITE);
        int primary = color(R.color.tuyin_primary);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(Color.argb(60, Color.red(primary), Color.green(primary), Color.blue(primary))),
                body, mask);
        Drawable[] layers = new Drawable[5];
        for (int i = 0; i < 4; i++) {
            layers[i] = shapeBg(radiusDp, Color.argb(alphas[i], 0, 0, 0));
        }
        layers[4] = ripple;
        LayerDrawable ld = new LayerDrawable(layers);
        for (int i = 0; i < 4; i++) {
            int inset = d * (3 - i) / 4;
            ld.setLayerInset(i, 0, 0, inset, inset);
        }
        ld.setLayerInset(4, 0, 0, d, d);
        return ld;
    }

    /** 圆角轻投影背景（无 ripple，用于不可点容器）。 */
    private Drawable shadowWrap(GradientDrawable body, int radiusDp, int depthDp) {
        int d = dp(depthDp);
        int[] alphas = { 2, 4, 6, 9 };
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** 字节数人性化显示（对齐 web fmtKB）。 */
    private String fmtKB(int bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return "<1 KB";
        return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
    }

    /* ================= 面板切换 ================= */

    private void switchPanel(boolean embed) {
        panelEmbed.setVisibility(embed ? View.VISIBLE : View.GONE);
        panelExtract.setVisibility(embed ? View.GONE : View.VISIBLE);
        applyNavState(tabEmbed, embed);
        applyNavState(tabExtract, !embed);
    }

    /** Material 3 导航选中态：蓝底胶囊 + 蓝图标 + 蓝字；未选中：透明、灰、半透明。 */
    private void applyNavState(LinearLayout item, boolean selected) {
        ImageView icon = (ImageView) item.getTag(R.id.navIcon);
        TextView label = (TextView) item.getTag(R.id.navLabel);
        GradientDrawable pillBg = (GradientDrawable) item.getTag(R.id.navPillBg);
        int primary = color(R.color.tuyin_primary);
        if (selected) {
            pillBg.setColor(Color.argb(32, Color.red(primary), Color.green(primary), Color.blue(primary)));
            icon.setColorFilter(primary, PorterDuff.Mode.SRC_IN);
            label.setTextColor(primary);
            item.setAlpha(1f);
        } else {
            pillBg.setColor(Color.TRANSPARENT);
            icon.setColorFilter(color(R.color.tuyin_sub), PorterDuff.Mode.SRC_IN);
            label.setTextColor(color(R.color.tuyin_sub));
            item.setAlpha(0.45f);
        }
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
                coverUri = uri;
                coverData = RacImages.decodeUri(this, uri, enhanceTarget());
                Bitmap bmp = RacImages.imageDataToBitmap(coverData);
                fitImage(coverBoxImg, coverBoxText, bmp, coverBox, coverLp, IMG_CARD_H,
                        coverBox, coverLp, 0);
                payloadBytes = 0;
                updateCapacityMeter();
            } else if (requestCode == PICK_SECRET) {
                secretData = RacImages.decodeUri(this, uri, RacImages.SECRET_MAX);
                Bitmap bmp = RacImages.imageDataToBitmap(secretData);
                fitImage(secretBoxImg, secretBoxText, bmp, secretBox, secretLp, IMG_CARD_H,
                        secretBox, secretLp, 0);
                updateCapacityMeter();
            } else if (requestCode == PICK_STEGO) {
                stegoData = RacImages.decodeUri(this, uri, 0);
                Bitmap bmp = RacImages.imageDataToBitmap(stegoData);
                fitImage(stegoBoxImg, stegoBoxText, bmp, stegoBox, stegoLp, IMG_CARD_H,
                        stegoBox, stegoLp, 0);
            }
        } catch (Exception e) {
            toast("读取图片失败：" + e.getMessage());
        }
    }

    /** 封面增强档位变化：按新档位重新处理封面。 */
    private void reapplyCover() {
        if (coverUri == null) return;
        try {
            coverData = RacImages.decodeUri(this, coverUri, enhanceTarget());
            Bitmap bmp = RacImages.imageDataToBitmap(coverData);
            fitImage(coverBoxImg, coverBoxText, bmp, coverBox, coverLp, IMG_CARD_H,
                    coverBox, coverLp, 0);
            payloadBytes = 0;
            updateCapacityMeter();
        } catch (Exception e) {
            toast("重新处理封面失败：" + e.getMessage());
        }
    }

    private int enhanceTarget() {
        return ENHANCE_VALUES[enhanceIndex];
    }

    private void updateCapacityMeter() {
        if (coverData == null) {
            capacityText.setText("0 B");
            capacityBar.setProgress(0);
            return;
        }
        RacCore.Options opts = currentOptions();
        capacityBytes = RacCore.capacityBytes(coverData.width, coverData.height, opts);
        capacityText.setText(fmtKB(capacityBytes));
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
                final Bitmap bmp = RacImages.imageDataToBitmap(stego);
                runOnUiThread(() -> {
                    fitImage(stegoResultImg, stegoResultText, bmp,
                            stegoResultArea, stegoResultAreaLp, RESULT_AREA_H,
                            stegoResultBox, stegoResultBox.getLayoutParams(), 36);
                    updateCapacityMeter();
                    embedStatus.setTextColor(Color.parseColor("#0086FF"));
                    embedStatus.setText("嵌入完成 " + bmp.getWidth() + "×" + bmp.getHeight()
                            + "，可保存隐写图、切“提取”验证或下滑“通道模拟”验证鲁棒性");
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

    /* ================= 通道模拟：缩放 → JPEG 重压缩 → 再提取 ================= */

    private void doSimulate() {
        if (stegoData == null) {
            toast("请先在“嵌入”页生成隐写图，或在“提取”页选择隐写图");
            return;
        }
        btnSim.setEnabled(false);
        simStatus.setTextColor(color(R.color.tuyin_sub));
        simStatus.setText("模拟通道：缩放 → JPEG 重压缩 → 提取…");
        new Thread(() -> {
            try {
                RacCore.ImageData img = stegoData;
                double scale = SCALE_VALUES[scaleIndex];
                if (scale < 1) {
                    img = RacImages.resizeImageData(img,
                            Math.max(8, (int) Math.round(img.width * scale)),
                            Math.max(8, (int) Math.round(img.height * scale)));
                }
                int quality = QUALITY_VALUES[qualityIndex];
                if (quality < 100) {
                    Bitmap b = RacImages.imageDataToBitmap(img);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    b.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                    byte[] jpeg = baos.toByteArray();
                    Bitmap dec = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                    img = RacImages.bitmapToImageData(dec);
                }
                final RacCore.ImageData attacked = img;
                final RacCore.ExtractResult res = extractAuto(attacked);
                runOnUiThread(() -> {
                    fitImage(simAttackedImg, simAttackedText, RacImages.imageDataToBitmap(attacked),
                            simAttackedArea, simAttackedAreaLp, RESULT_AREA_H,
                            simAttackedBox, simAttackedBox.getLayoutParams(), 36);
                    if (res != null) {
                        final Bitmap recRaw = BitmapFactory.decodeByteArray(res.jpeg, 0, res.jpeg.length);
                        final Bitmap recBmp = RacImages.applyExifRotationBytes(res.jpeg, recRaw);
                        simRecoveredBitmap = recBmp;
                        fitImage(simRecoveredImg, simRecoveredText, recBmp,
                                simRecoveredArea, simRecoveredAreaLp, RESULT_AREA_H,
                                simRecoveredBox, simRecoveredBox.getLayoutParams(), 36);
                        simStatus.setTextColor(Color.parseColor("#0086FF"));
                        simStatus.setText("提取成功：秘密图完好恢复，扛住了这个通道。");
                    } else {
                        simRecoveredBitmap = null;
                        simRecoveredImg.setImageBitmap(null);
                        simRecoveredImg.setVisibility(View.GONE);
                        simRecoveredText.setVisibility(View.VISIBLE);
                        simRecoveredAreaLp.height = dp(RESULT_AREA_H);
                        simRecoveredArea.requestLayout();
                        simRecoveredBox.getLayoutParams().height = dp(RESULT_AREA_H) + dp(36);
                        simRecoveredBox.requestLayout();
                        simStatus.setTextColor(Color.parseColor("#E5484D"));
                        simStatus.setText("提取失败：载荷已超出纠错预算。试试更轻的通道或更稳健的工作点。");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    simStatus.setTextColor(Color.parseColor("#E5484D"));
                    simStatus.setText("模拟失败：" + e.getMessage());
                });
            } finally {
                runOnUiThread(() -> btnSim.setEnabled(true));
            }
        }).start();
    }

    /* ================= 提取 ================= */

    private void doExtract() {
        if (stegoData == null) {
            toast("请先选择隐写图");
            return;
        }
        int manualW = parseDim(coverWInput);
        int manualH = parseDim(coverHInput);
        if ((manualW >= 16) != (manualH >= 16)) {
            toast("宽和高需同时填写，或都留空自动检测封面尺寸");
            return;
        }
        final boolean manual = manualW >= 16 && manualH >= 16;
        btnExtract.setEnabled(false);
        extractStatus.setTextColor(color(R.color.tuyin_sub));
        extractStatus.setText(manual
                ? "正在提取（按 " + manualW + "×" + manualH + " 恢复）…"
                : "正在提取（自动检测封面尺寸）…");
        new Thread(() -> {
            try {
                RacCore.ImageData working = stegoData;
                RacCore.ExtractResult res;
                if (manual) {
                    working = RacImages.resizeImageData(stegoData, manualW, manualH);
                    res = RacCore.extract(working);
                } else {
                    res = extractAuto(working);
                }
                if (res == null) {
                    runOnUiThread(() -> {
                        extractStatus.setTextColor(Color.parseColor("#E5484D"));
                        extractStatus.setText("未找到隐藏图片：图片可能不是图隐产物，或已被大幅修改");
                    });
                    return;
                }
                final Bitmap bmpRaw = BitmapFactory.decodeByteArray(res.jpeg, 0, res.jpeg.length);
                final Bitmap bmp = RacImages.applyExifRotationBytes(res.jpeg, bmpRaw);
                runOnUiThread(() -> {
                    extractedBitmap = bmp;
                    fitImage(extractResultImg, extractResultText, bmp,
                            extractResultArea, extractResultAreaLp, RESULT_AREA_H,
                            extractResultBox, extractResultBox.getLayoutParams(), 36);
                    extractStatus.setTextColor(Color.parseColor("#0086FF"));
                    extractStatus.setText("提取成功 " + bmp.getWidth() + "×" + bmp.getHeight()
                            + (manual ? "（已按 " + manualW + "×" + manualH + " 恢复）" : "（已自动恢复封面尺寸）")
                            + "，可保存到相册");
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

    /** 自动检测封面标尺并提取（对齐 web extractImage 无 manual 分支）。 */
    private RacCore.ExtractResult extractAuto(RacCore.ImageData stego) {
        RacCore.ImageData working = stego;
        byte[] rgb = toRgb(stego.rgba);
        int[] detected = RacRuler.detectRulerSize(rgb, stego.width, stego.height);
        if (detected != null && (detected[0] != stego.width || detected[1] != stego.height)) {
            working = RacImages.resizeImageData(stego, detected[0], detected[1]);
        }
        return RacCore.extract(working);
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

    private void saveBitmap(Bitmap bmp, String name, String mime, int quality) {
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

    private void saveBitmap(RacCore.ImageData imageData, String name, String mime, int quality) {
        Bitmap bmp = null;
        if (imageData != null) {
            bmp = RacImages.imageDataToBitmap(imageData);
        } else if (extractedBitmap != null) {
            bmp = extractedBitmap;
        } else if (stegoData != null) {
            bmp = RacImages.imageDataToBitmap(stegoData);
        }
        saveBitmap(bmp, name, mime, quality);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (extractedBitmap != null) {
                RacImages.saveBitmapToGallery(this, extractedBitmap, "tuyin-extracted", "image/png", 100);
            } else if (stegoData != null) {
                RacImages.saveBitmapToGallery(this, RacImages.imageDataToBitmap(stegoData), "tuyin-stego", "image/png", 100);
            } else if (simRecoveredBitmap != null) {
                RacImages.saveBitmapToGallery(this, simRecoveredBitmap, "tuyin-recovered", "image/png", 100);
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
        coverUri = null;
        payloadBytes = 0;
        coverBoxImg.setImageBitmap(null);
        coverBoxImg.setVisibility(View.GONE);
        coverBoxText.setVisibility(View.VISIBLE);
        coverLp.height = dp(IMG_CARD_H);
        coverBox.requestLayout();
        secretBoxImg.setImageBitmap(null);
        secretBoxImg.setVisibility(View.GONE);
        secretBoxText.setVisibility(View.VISIBLE);
        secretLp.height = dp(IMG_CARD_H);
        secretBox.requestLayout();
        stegoResultImg.setImageBitmap(null);
        stegoResultImg.setVisibility(View.GONE);
        stegoResultText.setVisibility(View.VISIBLE);
        stegoResultAreaLp.height = dp(RESULT_AREA_H);
        stegoResultArea.requestLayout();
        stegoResultBox.getLayoutParams().height = dp(RESULT_AREA_H) + dp(36);
        stegoResultBox.requestLayout();
        // 通道模拟复位
        simRecoveredBitmap = null;
        simAttackedImg.setImageBitmap(null);
        simAttackedImg.setVisibility(View.GONE);
        simAttackedText.setVisibility(View.VISIBLE);
        simAttackedAreaLp.height = dp(RESULT_AREA_H);
        simAttackedArea.requestLayout();
        simAttackedBox.getLayoutParams().height = dp(RESULT_AREA_H) + dp(36);
        simAttackedBox.requestLayout();
        simRecoveredImg.setImageBitmap(null);
        simRecoveredImg.setVisibility(View.GONE);
        simRecoveredText.setVisibility(View.VISIBLE);
        simRecoveredAreaLp.height = dp(RESULT_AREA_H);
        simRecoveredArea.requestLayout();
        simRecoveredBox.getLayoutParams().height = dp(RESULT_AREA_H) + dp(36);
        simRecoveredBox.requestLayout();
        simStatus.setText("");
        updateCapacityMeter();
        embedStatus.setText("");
    }

    private void resetExtract() {
        stegoData = null;
        extractedBitmap = null;
        stegoBoxImg.setImageBitmap(null);
        stegoBoxImg.setVisibility(View.GONE);
        stegoBoxText.setVisibility(View.VISIBLE);
        stegoLp.height = dp(IMG_CARD_H);
        stegoBox.requestLayout();
        extractResultImg.setImageBitmap(null);
        extractResultImg.setVisibility(View.GONE);
        extractResultText.setVisibility(View.VISIBLE);
        extractResultAreaLp.height = dp(RESULT_AREA_H);
        extractResultArea.requestLayout();
        extractResultBox.getLayoutParams().height = dp(RESULT_AREA_H) + dp(36);
        extractResultBox.requestLayout();
        coverWInput.setText("");
        coverHInput.setText("");
        extractStatus.setText("");
    }
}
