import { embedImage, extractImage, decodeImageFile, imageDataToBlob, resizeImageData } from '../src/browser.js';
import { capacityBytes } from '../src/core.js';

const MAX_LONG = 4096;

const state = {
  coverFile: null,
  secretFile: null,
  cover: null,
  secret: null,
  stego: null,
  extractStego: null,
  extracted: null,
  customMode: false,
};

const $ = (id) => document.getElementById(id);

/* ------------------------------------------------------------------ *
 * Operating point
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
    return { name: 'Capacity', desc: 'Largest, sharpest secret — weakest against attacks' };
  }
  if (options.ppb <= 1 && options.repeat >= 3) {
    return { name: 'Anti-compression', desc: 'Strongest against compression and rescaling' };
  }
  if (options.ppb <= 2 && options.nsym >= 48) {
    return { name: 'Balanced', desc: 'Visual subtlety and compression resistance' };
  }
  return { name: 'Custom', desc: `ppb=${options.ppb} repeat=${options.repeat} nsym=${options.nsym}` };
}

/* ------------------------------------------------------------------ *
 * Helpers
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

/* ------------------------------------------------------------------ *
 * Capacity / tier UI
 * ------------------------------------------------------------------ */
function updateCapacity() {
  const options = currentOptions();
  if (state.cover) {
    const bytes = capacityBytes(state.cover.width, state.cover.height, options);
    $('capacityInfo').textContent = `≈ ${(Math.max(0, bytes) / 1024).toFixed(1)} KB`;
  } else {
    $('capacityInfo').textContent = '—';
  }
  $('embedBtn').disabled = !(state.cover && state.secret);
}

function updateTierUI() {
  if (state.customMode) {
    $('tierName').textContent = 'Custom';
    $('tierDesc').textContent = 'All parameters set manually';
  } else {
    const label = tierText(currentOptions());
    $('tierName').textContent = label.name;
    $('tierDesc').textContent = label.desc;
  }
  $('tierSlider').disabled = state.customMode;
  $('tierSliderWrap').style.opacity = state.customMode ? '0.4' : '1';
  $('tierSliderWrap').style.pointerEvents = state.customMode ? 'none' : '';
  $('racCustomPanel').classList.toggle('open', state.customMode);
}

function refreshAll() {
  updateTierUI();
  updateCapacity();
}

/* ------------------------------------------------------------------ *
 * Cover loading (with optional upscaling)
 * ------------------------------------------------------------------ */
function coverTargetLong() {
  const value = $('coverUpscale').value;
  if (value === 'custom') return parseInt($('coverCustomLong').value, 10) || 0;
  return parseInt(value, 10) || 0;
}

async function loadCover() {
  if (!state.coverFile) return;
  const target = coverTargetLong();
  state.cover = await decodeImageFile(state.coverFile, target);
  if (!target) state.cover = capLongEdge(state.cover);
  $('coverHint').textContent = `${state.coverFile.name} · ${state.cover.width}×${state.cover.height}`;
  render($('coverCanvas'), state.cover);
  $('coverMeta').textContent = `${state.cover.width} × ${state.cover.height} px`;
  updateCapacity();
}

/* ------------------------------------------------------------------ *
 * Wiring: tabs
 * ------------------------------------------------------------------ */
document.querySelectorAll('.tab').forEach((tab) => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t === tab));
    document
      .querySelectorAll('.panel')
      .forEach((panel) => panel.classList.toggle('hidden', panel.id !== tab.dataset.tab));
  });
});

/* ------------------------------------------------------------------ *
 * Wiring: inputs
 * ------------------------------------------------------------------ */
$('coverInput').addEventListener('change', async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  state.coverFile = file;
  await loadCover();
});

$('secretInput').addEventListener('change', async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  state.secretFile = file;
  state.secret = capLongEdge(await decodeImageFile(file));
  $('secretHint').textContent = `${file.name} · ${state.secret.width}×${state.secret.height}`;
  render($('secretCanvas'), state.secret);
  $('secretMeta').textContent = `${state.secret.width} × ${state.secret.height} px`;
  updateCapacity();
});

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
 * Embed
 * ------------------------------------------------------------------ */
$('embedBtn').addEventListener('click', async () => {
  const button = $('embedBtn');
  button.disabled = true;
  setStatus('embedStatus', 'Embedding…');
  showProgress('embedProgress');
  await nextFrame();
  try {
    const result = await embedImage(state.cover, state.secret, currentOptions());
    state.stego = result.stego;
    render($('stegoCanvas'), state.stego);
    $('stegoMeta').textContent =
      `${state.stego.width} × ${state.stego.height} px · secret ${result.secretSize[0]}×${result.secretSize[1]} · JPEG q${Math.round(result.quality * 100)}`;
    $('downloadStegoBtn').disabled = false;
    setStatus('embedStatus', 'Done. Download the stego image as PNG.', 'success');
  } catch (error) {
    setStatus('embedStatus', error.message, 'error');
  }
  hideProgress('embedProgress');
  button.disabled = false;
});

$('downloadStegoBtn').addEventListener('click', () => {
  if (state.stego) downloadImageData(state.stego, 'stego-image.png');
});

$('resetEmbedBtn').addEventListener('click', () => {
  state.coverFile = state.secretFile = state.cover = state.secret = state.stego = null;
  state.customMode = false;
  $('coverInput').value = '';
  $('secretInput').value = '';
  $('coverUpscale').value = '0';
  $('coverCustomLong').value = '';
  $('tierSlider').value = '50';
  $('coverHint').textContent = 'Click to choose, or drop a file. Recommended ≥ 512×512.';
  $('secretHint').textContent = 'Hidden inside the cover.';
  ['coverCanvas', 'secretCanvas', 'stegoCanvas'].forEach((id) => {
    const canvas = $(id);
    canvas.width = canvas.height = 0;
  });
  $('coverMeta').textContent = '';
  $('secretMeta').textContent = '';
  $('stegoMeta').textContent = '';
  $('downloadStegoBtn').disabled = true;
  setStatus('embedStatus', '');
  hideProgress('embedProgress');
  refreshAll();
});

/* ------------------------------------------------------------------ *
 * Extract
 * ------------------------------------------------------------------ */
$('stegoInput').addEventListener('change', async (event) => {
  const file = event.target.files[0];
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
  setStatus('extractStatus', 'Extracting…');
  showProgress('extractProgress');
  await nextFrame();
  try {
    const manualW = parseInt($('manualW').value, 10) || 0;
    const manualH = parseInt($('manualH').value, 10) || 0;
    const manual = manualW >= 16 && manualH >= 16 ? [manualW, manualH] : null;
    const result = await extractImage(state.extractStego, manual);
    if (!result) {
      setStatus('extractStatus', 'No valid payload found.', 'error');
    } else {
      render($('extractedCanvas'), result.imageData);
      $('extractMeta').textContent =
        `${result.imageData.width} × ${result.imageData.height} px · ppb=${result.ppb} repeat=${result.repeat} nsym=${result.nsym}`;
      state.extracted = result.imageData;
      $('downloadSecretBtn').disabled = false;
      setStatus('extractStatus', 'Done.', 'success');
    }
  } catch (error) {
    setStatus('extractStatus', error.message, 'error');
  }
  hideProgress('extractProgress');
  button.disabled = false;
});

$('downloadSecretBtn').addEventListener('click', () => {
  if (state.extracted) downloadImageData(state.extracted, 'recovered-secret.png');
});

$('resetExtractBtn').addEventListener('click', () => {
  state.extractStego = state.extracted = null;
  $('stegoInput').value = '';
  $('manualW').value = '';
  $('manualH').value = '';
  $('stegoHint').textContent = 'The image produced by the embed step.';
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
refreshAll();
