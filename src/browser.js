/**
 * Browser helpers — image decoding, canvas round-trips and the two high-level
 * entry points.  Import from `rac-hide/browser`; nothing here runs in Node.
 *
 * @module browser
 */
import { buildPayload, embed, extract, capacityBytes } from './core.js';
import { detectRulerSize } from './ruler.js';

function makeCanvas(width, height) {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  return canvas;
}

async function loadBitmap(source) {
  // `colorSpaceConversion: 'none'` keeps pixels identical to the file, which
  // matters for images carrying an ICC profile (WebP/JPEG).
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(source, {
        colorSpaceConversion: 'none',
        premultiplyAlpha: 'none',
      });
    } catch {
      /* fall through to <img> */
    }
  }
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(source);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = (e) => {
      URL.revokeObjectURL(url);
      reject(e);
    };
    img.src = url;
  });
}

/**
 * Decode an image `File`/`Blob` into an `ImageData`-shaped object.
 * @param {Blob} file
 * @param {number} [targetLong] optionally rescale so the long edge equals this
 * @returns {Promise<{data: Uint8ClampedArray, width: number, height: number}>}
 */
export async function decodeImageFile(file, targetLong) {
  const bitmap = await loadBitmap(file);
  let width = bitmap.width;
  let height = bitmap.height;
  if (targetLong && targetLong > 0) {
    const scale = targetLong / Math.max(width, height);
    width = Math.max(1, Math.round(width * scale));
    height = Math.max(1, Math.round(height * scale));
  }
  const canvas = makeCanvas(width, height);
  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(bitmap, 0, 0, width, height);
  if (typeof bitmap.close === 'function') bitmap.close();
  const image = ctx.getImageData(0, 0, width, height);
  return { data: image.data, width, height };
}

/**
 * Decode raw image bytes (e.g. the extracted JPEG) into an `ImageData`.
 * @param {Uint8Array} bytes
 * @param {string} type MIME type
 * @returns {Promise<{data: Uint8ClampedArray, width: number, height: number}>}
 */
export async function decodeImageBytes(bytes, type = 'image/jpeg') {
  return decodeImageFile(new Blob([bytes], { type }));
}

/**
 * Resample an `ImageData` to a new size using the canvas (high-quality).
 * @param {{data: Uint8ClampedArray, width: number, height: number}} imageData
 * @param {number} width
 * @param {number} height
 * @returns {{data: Uint8ClampedArray, width: number, height: number}}
 */
export function resizeImageData(imageData, width, height) {
  const source = makeCanvas(imageData.width, imageData.height);
  source
    .getContext('2d')
    .putImageData(new ImageData(imageData.data, imageData.width, imageData.height), 0, 0);
  const target = makeCanvas(width, height);
  const ctx = target.getContext('2d');
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(source, 0, 0, width, height);
  const out = ctx.getImageData(0, 0, width, height);
  return { data: out.data, width, height };
}

/**
 * Encode an `ImageData` to a `Blob`.
 * @param {{data: Uint8ClampedArray, width: number, height: number}} imageData
 * @param {string} [type]
 * @param {number} [quality]
 * @returns {Promise<Blob>}
 */
export function imageDataToBlob(imageData, type = 'image/png', quality) {
  const canvas = makeCanvas(imageData.width, imageData.height);
  canvas
    .getContext('2d')
    .putImageData(new ImageData(imageData.data, imageData.width, imageData.height), 0, 0);
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('canvas.toBlob failed'))),
      type,
      quality,
    );
  });
}

/**
 * Compress a secret image to a JPEG that fits `maxBytes`, preferring
 * resolution over quality.
 * @param {{data: Uint8ClampedArray, width: number, height: number}} source
 * @param {number} maxBytes
 * @param {number} [maxLong] largest long edge to keep
 * @returns {Promise<{bytes: Uint8Array, width: number, height: number, quality: number}|null>}
 */
export async function fitSecretJpeg(source, maxBytes, maxLong = 1280) {
  const cap = Math.max(32, Math.min(maxLong, Math.max(source.width, source.height)));
  const dims = [640, 560, 480, 400, 320, 256, 192, 128, 96, 64, 48, 32].filter((d) => d <= cap);
  if (cap > 640 && !dims.includes(cap)) dims.unshift(cap);
  if (dims.length === 0) dims.push(cap);

  const qualities = [0.8, 0.65, 0.5, 0.4, 0.32, 0.25];
  const base = makeCanvas(source.width, source.height);
  base
    .getContext('2d')
    .putImageData(new ImageData(source.data, source.width, source.height), 0, 0);

  for (const maxDim of dims) {
    let width = source.width;
    let height = source.height;
    if (Math.max(width, height) > maxDim) {
      const scale = maxDim / Math.max(width, height);
      width = Math.max(1, Math.round(width * scale));
      height = Math.max(1, Math.round(height * scale));
    }
    const canvas = makeCanvas(width, height);
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(base, 0, 0, width, height);
    for (const quality of qualities) {
      const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
      if (blob && blob.size <= maxBytes) {
        const buffer = await blob.arrayBuffer();
        return { bytes: new Uint8Array(buffer), width, height, quality };
      }
    }
  }
  return null;
}

/**
 * High-level: embed a secret image into a cover image.
 * @param {{data: Uint8ClampedArray, width: number, height: number}} cover
 * @param {{data: Uint8ClampedArray, width: number, height: number}} secret
 * @param {import('./constants.js').EmbedOptions & {secretMax?: number}} [options]
 * @returns {Promise<{stego: object, secretSize: [number, number], quality: number, capacity: number}>}
 */
export async function embedImage(cover, secret, options = {}) {
  const capacity = capacityBytes(cover.width, cover.height, options);
  const fitted = await fitSecretJpeg(secret, capacity, options.secretMax ?? 1280);
  if (!fitted) throw new Error('secret image does not fit into this cover');
  const payload = buildPayload(fitted.bytes, options);
  const stego = embed(cover, payload, options);
  return {
    stego,
    secretSize: [fitted.width, fitted.height],
    quality: fitted.quality,
    capacity,
  };
}

/**
 * High-level: extract a secret image from a stego image, undoing proportional
 * rescaling via the spatial ruler when possible.
 * @param {{data: Uint8ClampedArray, width: number, height: number}} stego
 * @param {[number, number]} [manualSize] cover size to use if the ruler fails
 * @returns {Promise<{imageData: object, ppb: number, repeat: number, nsym: number}|null>}
 */
export async function extractImage(stego, manualSize) {
  let working = stego;
  if (manualSize && manualSize[0] >= 16 && manualSize[1] >= 16) {
    working = resizeImageData(stego, manualSize[0], manualSize[1]);
  } else {
    const detected = detectRulerSize(stego.data, stego.width, stego.height);
    if (detected && (detected[0] !== stego.width || detected[1] !== stego.height)) {
      working = resizeImageData(stego, detected[0], detected[1]);
    }
  }
  const result = extract(working);
  if (!result) return null;
  const imageData = await decodeImageBytes(result.jpeg, 'image/jpeg');
  return { imageData, ppb: result.ppb, repeat: result.repeat, nsym: result.nsym };
}
