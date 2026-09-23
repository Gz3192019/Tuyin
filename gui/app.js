import {
  fitSecretJpeg,
  imageDataToBlob,
  resizeImageData,
  decodeImageFile,
  extractImage,
} from '../src/browser.js';
import { buildPayload, embed, capacityBytes } from '../src/core.js';

const MAX_LONG = 4096;

const state = {
  coverFile: null,
  secretFile: null,
  cover: null,
  secret: null,
  stego: null,
  payloadBytes: 0,
  capacity: 0,
  extractStego: null,
  extracted: null,
  attacked: null,
  recovered: null,
  customMode: false,
};

const $ = (id) => document.getElementById(id);

/* ------------------------------------------------------------------ *
 * 工作点（与项目 demo 一致的映射）
 * ------------------------------------------------------------------ */
function sliderToTier(value) {
  if (value <= 50) {
    const t = value / 50;
    return { ppb: Math.round(6 - t * 4), repeat: 1, nsym: Math.round(32 + t * 16) };
  }
  const t = (value - 50) / 50;
  return { ppb: Math.round(2 - t), repeat: t < 0.5 ? 1 : 3, nsym: 48 };
}

function customOptions() {
  return {
    ppb: parseInt($('paramPPB').value, 10) || 2,
    repeat: parseInt($('paramRepeat').value, 10) || 1,
    nsym: parseInt($('paramNsym').value, 10) || 48,
    marginMin: parseInt($('paramMarginMin').value, 10) || 40,
    marginGain: (parseInt($('paramMarginGain').value, 10) || 14) / 10,
    marginMax: parseInt($('paramMarginMax').value, 10) || 200,
    secretMax: parseInt($('paramSecretMax').value, 10) || 1280,
  };
}

function currentOptions() {
  if (state.customMode) return customOptions();
  const tier = sliderToTier(parseInt($('tierSlider').value, 10));
  return { ...tier, marginMin: 40, marginGain: 1.4, marginMax: 200, secretMax: 1280 };
}

function tierText(options) {
  if (options.ppb >= 5) {
    return { name: '容量优先', desc: '最大、最清晰的秘密图，抗攻击能力最弱' };
  }
  if (options.ppb <= 1 && options.repeat >= 3) {
    return { name: '抗压缩', desc: '对压缩与缩放最稳健，容量最小' };
  }
  if (options.ppb <= 2 && options.nsym >= 48) {
    return { name: '均衡', desc: '视觉隐蔽与抗压缩兼顾（推荐）' };
  }
  return { name: '自定义', desc: `ppb=${options.ppb} repeat=${options.repeat} nsym=${options.nsym}` };
}

/* ------------------------------------------------------------------ *
 * 工具函数
 * ------------------------------------------------------------------ */
function render(canvas, imageData) {
  canvas.width = imageData.width;
  canvas.height = imageData.height;
  canvas
    .getContext('2d')
    .putImageData(new ImageData(imageData.data, imageData.width, imageData.height), 0, 0);
}

function setStatus(id, message, kind) {
  const el = $(id);
  el.textContent = message || '';
  el.className = 'status' + (kind ? ' ' + kind : '');
}

function showProgress(id) { $(id).classList.add('active'); }
function hideProgress(id) { $(id).classList.remove('active'); }
function nextFrame() { return new Promise((resolve) => setTimeout(resolve, 30)); }

async function downloadImageData(imageData, filename) {
  // 原生壳（Android）：把 PNG 交给 TuyinBridge 存入相册
  if (window.TuyinBridge) {
    const c = document.createElement('canvas');
    c.width = imageData.width;
    c.height = imageData.height;
    c.getContext('2d').putImageData(
      new ImageData(imageData.data, imageData.width, imageData.height), 0, 0);
    try {
      const dataUrl = c.toDataURL('image/png');
      window.TuyinBridge.savePng(dataUrl, filename);
      return;
    } catch (e) {
      // 原生保存失败：明确反馈，不落入 WebView 里无效的浏览器下载
      try { window.TuyinBridge.showToast('保存失败，请重试'); } catch (_) { }
      return;
    }
  }
  const blob = await imageDataToBlob(imageData, 'image/png');
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function capLongEdge(imageData) {
  const long = Math.max(imageData.width, imageData.height);
  if (long <= MAX_LONG) return imageData;
  const scale = MAX_LONG / long;
  return resizeImageData(
    imageData,
    Math.max(1, Math.round(imageData.width * scale)),
    Math.max(1, Math.round(imageData.height * scale)),
  );
}

function fmtKB(bytes) {
  return bytes <= 0 ? '0 B' : `${(bytes / 1024).toFixed(1)} KB`;
}

/* ------------------------------------------------------------------ *
 * 容量 / 工作点 UI
 * ------------------------------------------------------------------ */
function updateCapacity() {
  const options = currentOptions();
  if (state.cover) {
    state.capacity = Math.max(0, capacityBytes(state.cover.width, state.cover.height, options));
    $('capacityInfo').textContent = `≈ ${fmtKB(state.capacity)}`;
  } else {
    state.capacity = 0;
    $('capacityInfo').textContent = '—';
  }
  updateMeter();
  $('embedBtn').disabled = !(state.cover && state.secret);
}

function updateMeter() {
  const meter = $('capacityMeter');
  if (state.capacity > 0 && state.payloadBytes > 0) {
    const pct = Math.min(100, Math.round((state.payloadBytes / state.capacity) * 100));
    meter.style.width = `${pct}%`;
    meter.classList.toggle('hot', pct > 85);
    $('payloadInfo').textContent = `${fmtKB(state.payloadBytes)} · 占用 ${pct}%`;
  } else {
    meter.style.width = '0%';
    meter.classList.remove('hot');
    $('payloadInfo').textContent = '尚未嵌入';
  }
}

function updateTierUI() {
  if (state.customMode) {
    $('tierName').textContent = '自定义';
    $('tierDesc').textContent = '全部参数手动设置';
  } else {
    const label = tierText(currentOptions());
    $('tierName').textContent = label.name;
    $('tierDesc').textContent = label.desc;
  }
  $('tierSlider').disabled = state.customMode;
  $('tierSliderWrap').style.opacity = state.customMode ? '0.4' : '1';
  $('tierSliderWrap').style.pointerEvents = state.customMode ? 'none' : '';
  $('customPanel').classList.toggle('open', state.customMode);
}

function refreshAll() {
  updateTierUI();
  updateCapacity();
}

/* ------------------------------------------------------------------ *
 * 封面 / 秘密图加载
 * ------------------------------------------------------------------ */
function coverTargetLong() {
  const value = $('coverUpscale').value;
  if (value === 'custom') return parseInt($('coverCustomLong').value, 10) || 0;
  return parseInt(value, 10) || 0;
}

async function loadCover() {
  if (!state.coverFile) return;
  const target = coverTargetLong();
  state.cover = await decodeImageFile(state.coverFile, target || undefined);
  if (!target) state.cover = capLongEdge(state.cover);
  $('coverHint').textContent = `${state.coverFile.name} · ${state.cover.width}×${state.cover.height}`;
  render($('coverCanvas'), state.cover);
  $('coverMeta').textContent = `${state.cover.width} × ${state.cover.height} px`;
  updateCapacity();
}

async function loadSecret() {
  if (!state.secretFile) return;
  state.secret = capLongEdge(await decodeImageFile(state.secretFile));
  $('secretHint').textContent = `${state.secretFile.name} · ${state.secret.width}×${state.secret.height}`;
  render($('secretCanvas'), state.secret);
  $('secretMeta').textContent = `${state.secret.width} × ${state.secret.height} px`;
  updateCapacity();
}

/* ------------------------------------------------------------------ *
 * 拖拽
 * ------------------------------------------------------------------ */
function bindDrop(zoneId, inputId, loadFn) {
  const zone = $(zoneId);
  const input = $(inputId);
  zone.addEventListener('dragover', (event) => {
    event.preventDefault();
    zone.classList.add('dragover');
  });
  zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
  zone.addEventListener('drop', async (event) => {
    event.preventDefault();
    zone.classList.remove('dragover');
    const file = event.dataTransfer.files[0];
    if (!file) return;
    const dt = new DataTransfer();
    dt.items.add(file);
    input.files = dt.files;
    if (inputId === 'coverInput') state.coverFile = file;
    else if (inputId === 'secretInput') state.secretFile = file;
    await loadFn();
  });
  input.addEventListener('change', async (event) => {
    const file = event.target.files[0];
    if (!file) return;
    if (inputId === 'coverInput') state.coverFile = file;
    else if (inputId === 'secretInput') state.secretFile = file;
    await loadFn();
  });
}

/* ------------------------------------------------------------------ *
 * 明暗模式
 * ------------------------------------------------------------------ */
const THEME_KEY = 'rac-hide-theme';
const THEME_META = document.querySelector('meta[name="theme-color"]#themeColor');

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  const toggle = $('themeToggle');
  if (toggle) toggle.dataset.theme = theme;
  if (THEME_META) THEME_META.setAttribute('content', theme === 'light' ? '#f3f5fa' : '#0a0c11');
}

function initTheme() {
  const saved = localStorage.getItem(THEME_KEY);
  const prefers = matchMedia('(prefers-color-scheme: light)').matches;
  applyTheme(saved || (prefers ? 'light' : 'dark'));
}

const themeToggleEl = $('themeToggle');
if (themeToggleEl) {
  themeToggleEl.addEventListener('click', () => {
    const next = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
    localStorage.setItem(THEME_KEY, next);
    applyTheme(next);
  });
}

/* ------------------------------------------------------------------ *
 * 标签页
 * ------------------------------------------------------------------ */
document.querySelectorAll('.tab').forEach((tab) => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t === tab));
    document
      .querySelectorAll('.panel')
      .forEach((panel) => panel.classList.toggle('hidden', panel.id !== 'panel-' + tab.dataset.tab));
  });
});

/* ------------------------------------------------------------------ *
 * 输入事件
 * ------------------------------------------------------------------ */
bindDrop('coverDrop', 'coverInput', loadCover);
bindDrop('secretDrop', 'secretInput', loadSecret);

$('coverUpscale').addEventListener('change', () => {
  if ($('coverUpscale').value !== 'custom') $('coverCustomLong').value = '';
  loadCover();
});

$('coverCustomLong').addEventListener('input', () => {
  const input = $('coverCustomLong');
  let value = parseInt(input.value, 10) || 0;
  if (value > 8192) { value = 8192; input.value = '8192'; }
  $('coverUpscale').value = 'custom';
  loadCover();
});

$('tierSlider').addEventListener('input', refreshAll);

const paramIds = ['paramPPB', 'paramRepeat', 'paramNsym', 'paramMarginMin', 'paramMarginGain', 'paramMarginMax', 'paramSecretMax'];
paramIds.forEach((id) => {
  $(id).addEventListener('input', () => {
    const value = id === 'paramMarginGain'
      ? (parseInt($(id).value, 10) / 10).toFixed(1)
      : $(id).value;
    $(id + '_v').textContent = value;
    if (state.customMode) refreshAll();
  });
});

$('customBtn').addEventListener('click', () => {
  state.customMode = !state.customMode;
  refreshAll();
});

/* ------------------------------------------------------------------ *
 * 嵌入
 * ------------------------------------------------------------------ */
$('embedBtn').addEventListener('click', async () => {
  const button = $('embedBtn');
  button.disabled = true;
  setStatus('embedStatus', '准备嵌入…');
  showProgress('embedProgress');
  await nextFrame();
  try {
    const options = currentOptions();
    const capacity = capacityBytes(state.cover.width, state.cover.height, options);
    setStatus('embedStatus', '压缩秘密图并适配容量…');
    await nextFrame();
    const fitted = await fitSecretJpeg(state.secret, capacity, options.secretMax ?? 1280);
    if (!fitted) {
      throw new Error('秘密图太大，塞不进当前封面（试试更大的封面或调低容量参数）');
    }
    setStatus('embedStatus', '正在把载荷调制进 DCT 系数…');
    await nextFrame();
    const payload = buildPayload(fitted.bytes, options);
    state.stego = embed(state.cover, payload, options);
    state.payloadBytes = payload.length;
    state.capacity = capacity;
    updateMeter();

    render($('stegoCanvas'), state.stego);
    $('stegoMeta').textContent =
      `${state.stego.width} × ${state.stego.height} px · 秘密图 ${fitted.width}×${fitted.height} · JPEG q${Math.round(fitted.quality * 100)}`;
    $('downloadStegoBtn').disabled = false;
    $('verifyExtractBtn').disabled = false;
    $('simCard').hidden = false;
    setStatus('embedStatus', '嵌入完成。可下载隐写图，或进入下方“通道模拟”验证鲁棒性。', 'success');
  } catch (error) {
    setStatus('embedStatus', error.message, 'error');
  }
  hideProgress('embedProgress');
  button.disabled = false;
});

$('downloadStegoBtn').addEventListener('click', () => {
  if (state.stego) downloadImageData(state.stego, 'rac-hide-stego.png');
});

/* 就地提取验证：从内存中的隐写结果直接提取，无需下载再上传 */
$('verifyExtractBtn').addEventListener('click', async () => {
  const button = $('verifyExtractBtn');
  button.disabled = true;
  setStatus('embedStatus', '从当前隐写结果就地提取，验证闭环…');
  showProgress('embedProgress');
  await nextFrame();
  try {
    const result = await extractImage(state.stego);
    if (!result) {
      setStatus('embedStatus', '就地提取失败：未能从当前结果读到载荷。', 'error');
    } else {
      render($('recoveredCanvas'), result.imageData);
      $('recoveredMeta').textContent =
        `${result.imageData.width} × ${result.imageData.height} px · ppb=${result.ppb} repeat=${result.repeat} nsym=${result.nsym}`;
      $('downloadRecoveredBtn').disabled = false;
      setStatus('embedStatus', `闭环验证通过：直接从隐写结果提取成功（${result.imageData.width}×${result.imageData.height}）。`, 'success');
    }
  } catch (error) {
    setStatus('embedStatus', error.message, 'error');
  }
  hideProgress('embedProgress');
  button.disabled = false;
});

$('resetEmbedBtn').addEventListener('click', () => {
  state.coverFile = state.secretFile = state.cover = state.secret = state.stego = null;
  state.payloadBytes = 0;
  state.customMode = false;
  $('coverInput').value = '';
  $('secretInput').value = '';
  $('coverUpscale').value = '0';
  $('coverCustomLong').value = '';
  $('tierSlider').value = '50';
  $('coverHint').textContent = '点击选择或拖入文件，建议 ≥ 512×512';
  $('secretHint').textContent = '会被压缩嵌入封面中';
  ['coverCanvas', 'secretCanvas', 'stegoCanvas'].forEach((id) => {
    const canvas = $(id);
    canvas.width = canvas.height = 0;
  });
  $('coverMeta').textContent = '';
  $('secretMeta').textContent = '';
  $('stegoMeta').textContent = '';
  $('downloadStegoBtn').disabled = true;
  $('verifyExtractBtn').disabled = true;
  $('simCard').hidden = true;
  setStatus('embedStatus', '');
  hideProgress('embedProgress');
  refreshAll();
});

/* ------------------------------------------------------------------ *
 * 通道模拟：缩放 → JPEG 重压缩 → 再提取
 * ------------------------------------------------------------------ */
$('simBtn').addEventListener('click', async () => {
  if (!state.stego) return;
  const button = $('simBtn');
  button.disabled = true;
  setStatus('simStatus', '模拟通道：缩放 → JPEG 重压缩 → 提取…');
  showProgress('simProgress');
  await nextFrame();
  try {
    const scale = parseFloat($('simScale').value) || 1;
    const quality = parseInt($('simQuality').value, 10) || 90;

    let img = state.stego;
    if (scale < 1) {
      img = resizeImageData(
        img,
        Math.max(8, Math.round(img.width * scale)),
        Math.max(8, Math.round(img.height * scale)),
      );
    }
    if (quality < 100) {
      const blob = await imageDataToBlob(img, 'image/jpeg', quality / 100);
      img = await decodeImageFile(blob);
    }
    state.attacked = img;
    render($('attackedCanvas'), img);
    $('attackedMeta').textContent =
      `${img.width} × ${img.height} px` +
      (scale < 1 ? ` · 已缩放 ${Math.round(scale * 100)}%` : ' · 未缩放') +
      (quality < 100 ? ` · JPEG q${quality}` : '');

    setStatus('simStatus', '正在从传输后的图像中提取…');
    await nextFrame();
    const result = await extractImage(img);
    if (!result) {
      state.recovered = null;
      $('recoveredCanvas').width = $('recoveredCanvas').height = 0;
      $('recoveredMeta').textContent = '';
      $('downloadRecoveredBtn').disabled = true;
      setStatus('simStatus', '提取失败：载荷已超出纠错预算。试试更轻的通道（更高 q / 更小缩放）或更稳健的工作点。', 'error');
    } else {
      state.recovered = result.imageData;
      render($('recoveredCanvas'), result.imageData);
      $('recoveredMeta').textContent =
        `${result.imageData.width} × ${result.imageData.height} px · ppb=${result.ppb} repeat=${result.repeat} nsym=${result.nsym}`;
      $('downloadRecoveredBtn').disabled = false;
      setStatus('simStatus', '提取成功：秘密图完好恢复，扛住了这个通道。', 'success');
    }
  } catch (error) {
    setStatus('simStatus', error.message, 'error');
  }
  hideProgress('simProgress');
  button.disabled = false;
});

$('downloadRecoveredBtn').addEventListener('click', () => {
  if (state.recovered) downloadImageData(state.recovered, 'rac-hide-recovered.png');
});

/* ------------------------------------------------------------------ *
 * 提取
 * ------------------------------------------------------------------ */
bindDrop('stegoDrop', 'stegoInput', async () => {
  const file = $('stegoInput').files[0];
  if (!file) return;
  state.extractStego = capLongEdge(await decodeImageFile(file));
  $('stegoHint').textContent = `${file.name} · ${state.extractStego.width}×${state.extractStego.height}`;
  render($('extractStegoCanvas'), state.extractStego);
  $('extractStegoMeta').textContent = `${state.extractStego.width} × ${state.extractStego.height} px`;
  $('extractBtn').disabled = false;
});

$('extractBtn').addEventListener('click', async () => {
  const button = $('extractBtn');
  button.disabled = true;
  setStatus('extractStatus', '读取空间标尺、恢复尺寸并提取…');
  showProgress('extractProgress');
  await nextFrame();
  try {
    const manualW = parseInt($('manualW').value, 10) || 0;
    const manualH = parseInt($('manualH').value, 10) || 0;
    const manual = manualW >= 16 && manualH >= 16 ? [manualW, manualH] : null;
    const result = await extractImage(state.extractStego, manual);
    if (!result) {
      setStatus('extractStatus', '未找到有效载荷：图片不是本工具产出，或已被裁剪/重绘。', 'error');
    } else {
      render($('extractedCanvas'), result.imageData);
      $('extractMeta').textContent =
        `${result.imageData.width} × ${result.imageData.height} px · ppb=${result.ppb} repeat=${result.repeat} nsym=${result.nsym}`;
      state.extracted = result.imageData;
      $('downloadSecretBtn').disabled = false;
      setStatus('extractStatus', '提取成功。', 'success');
    }
  } catch (error) {
    setStatus('extractStatus', error.message, 'error');
  }
  hideProgress('extractProgress');
  button.disabled = false;
});

$('downloadSecretBtn').addEventListener('click', () => {
  if (state.extracted) downloadImageData(state.extracted, 'rac-hide-secret.png');
});

$('resetExtractBtn').addEventListener('click', () => {
  state.extractStego = state.extracted = null;
  $('stegoInput').value = '';
  $('manualW').value = '';
  $('manualH').value = '';
  $('stegoHint').textContent = '嵌入步骤产出的图片（或任意被平台处理过的版本）';
  ['extractStegoCanvas', 'extractedCanvas'].forEach((id) => {
    const canvas = $(id);
    canvas.width = canvas.height = 0;
  });
  $('extractStegoMeta').textContent = '';
  $('extractMeta').textContent = '';
  $('extractBtn').disabled = true;
  $('downloadSecretBtn').disabled = true;
  setStatus('extractStatus', '');
  hideProgress('extractProgress');
});

/* ------------------------------------------------------------------ */
initTheme();

// 原生壳（Android）：下载按钮改为「保存到相册」，下载即存相册
if (window.TuyinBridge) {
  const androidLabels = {
    downloadStegoBtn: '保存隐写图到相册',
    downloadSecretBtn: '保存秘密图到相册',
    downloadRecoveredBtn: '保存恢复图到相册',
  };
  for (const [id, label] of Object.entries(androidLabels)) {
    const btn = document.getElementById(id);
    if (btn) btn.textContent = label;
  }
}

refreshAll();
window.__racBooted = true;
