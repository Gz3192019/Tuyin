import { embedImage, extractImage, decodeImageFile, imageDataToBlob } from '../src/browser.js';
import { capacityBytes } from '../src/core.js';

const PRESETS = {
  capacity: { ppb: 6, repeat: 1, nsym: 32, marginMin: 40, marginGain: 1.4, marginMax: 200 },
  balanced: { ppb: 2, repeat: 1, nsym: 48, marginMin: 40, marginGain: 1.4, marginMax: 200 },
  robust: { ppb: 1, repeat: 3, nsym: 48, marginMin: 40, marginGain: 1.4, marginMax: 200 },
};

const state = { cover: null, secret: null, stego: null, extractStego: null };
const $ = (id) => document.getElementById(id);

function currentOptions() {
  return PRESETS[$('preset').value];
}

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

function refreshEmbed() {
  const options = currentOptions();
  $('paramInfo').textContent = `ppb=${options.ppb} repeat=${options.repeat} nsym=${options.nsym}`;
  if (state.cover) {
    const capacity = capacityBytes(state.cover.width, state.cover.height, options);
    $('coverMeta').textContent =
      `${state.cover.width}x${state.cover.height} · capacity ${(capacity / 1024).toFixed(1)} KB`;
  }
  $('embedBtn').disabled = !(state.cover && state.secret);
}

document.querySelectorAll('.tab').forEach((tab) => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t === tab));
    document
      .querySelectorAll('.panel')
      .forEach((panel) => panel.classList.toggle('hidden', panel.id !== tab.dataset.tab));
  });
});

$('preset').addEventListener('change', refreshEmbed);

$('coverInput').addEventListener('change', async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  state.cover = await decodeImageFile(file);
  $('coverHint').textContent = `${file.name} · ${state.cover.width}x${state.cover.height}`;
  render($('coverCanvas'), state.cover);
  refreshEmbed();
});

$('secretInput').addEventListener('change', async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  state.secret = await decodeImageFile(file);
  $('secretHint').textContent = `${file.name} · ${state.secret.width}x${state.secret.height}`;
  render($('secretCanvas'), state.secret);
  refreshEmbed();
});

$('embedBtn').addEventListener('click', async () => {
  const button = $('embedBtn');
  button.disabled = true;
  setStatus('embedStatus', 'Embedding…');
  try {
    await new Promise((resolve) => setTimeout(resolve, 20));
    const result = await embedImage(state.cover, state.secret, currentOptions());
    state.stego = result.stego;
    render($('stegoCanvas'), state.stego);
    $('stegoMeta').textContent =
      `${state.stego.width}x${state.stego.height} · secret ${result.secretSize[0]}x${result.secretSize[1]} q${Math.round(result.quality * 100)}`;
    const blob = await imageDataToBlob(state.stego, 'image/png');
    const link = $('downloadStego');
    link.href = URL.createObjectURL(blob);
    link.hidden = false;
    setStatus('embedStatus', 'Done. Download the stego image as PNG.', 'success');
  } catch (error) {
    setStatus('embedStatus', error.message, 'error');
  }
  button.disabled = false;
});

$('stegoInput').addEventListener('change', async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  state.extractStego = await decodeImageFile(file);
  $('stegoHint').textContent =
    `${file.name} · ${state.extractStego.width}x${state.extractStego.height}`;
  render($('extractStegoCanvas'), state.extractStego);
  $('extractStegoMeta').textContent = `${state.extractStego.width}x${state.extractStego.height}`;
  $('extractBtn').disabled = false;
});

$('extractBtn').addEventListener('click', async () => {
  const button = $('extractBtn');
  button.disabled = true;
  setStatus('extractStatus', 'Extracting…');
  try {
    await new Promise((resolve) => setTimeout(resolve, 20));
    const manualW = parseInt($('manualW').value, 10) || 0;
    const manualH = parseInt($('manualH').value, 10) || 0;
    const manual = manualW >= 16 && manualH >= 16 ? [manualW, manualH] : null;
    const result = await extractImage(state.extractStego, manual);
    if (!result) {
      setStatus('extractStatus', 'No valid payload found.', 'error');
      button.disabled = false;
      return;
    }
    render($('extractedCanvas'), result.imageData);
    $('extractedMeta').textContent =
      `${result.imageData.width}x${result.imageData.height} · ppb=${result.ppb} repeat=${result.repeat} nsym=${result.nsym}`;
    const blob = await imageDataToBlob(result.imageData, 'image/png');
    const link = $('downloadSecret');
    link.href = URL.createObjectURL(blob);
    link.hidden = false;
    setStatus('extractStatus', 'Done.', 'success');
  } catch (error) {
    setStatus('extractStatus', error.message, 'error');
  }
  button.disabled = false;
});

refreshEmbed();
