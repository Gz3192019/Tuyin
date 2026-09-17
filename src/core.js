/**
 * RAC-Hide core codec — pure JavaScript, no DOM.
 *
 * Images are represented as `{ data, width, height }` where `data` is an RGBA
 * byte buffer (exactly the shape of the browser's `ImageData`).  Everything in
 * this module runs unchanged in Node, a Web Worker or a browser.
 *
 * @module core
 */
import {
  BLOCK_SIZE,
  MAGIC,
  PAYLOAD_HEADER_BYTES,
  CODEWORD_BITS,
  COEFFICIENT_PAIRS,
  READ_THRESHOLD,
  MAX_PAYLOAD_BYTES,
  DEFAULT_OPTIONS,
} from './constants.js';
import { dct2d, idct2d } from './dct.js';
import { toLuma, reconstructRgb } from './color.js';
import { ReedSolomon } from './reed-solomon.js';
import { slotForIndex, deinterleave } from './interleave.js';
import { buildRulerField } from './ruler.js';
import { bytesToBits, bitsToBytes } from './bits.js';

const RS_CACHE = new Map();
function rsFor(nsym) {
  let codec = RS_CACHE.get(nsym);
  if (!codec) {
    codec = new ReedSolomon(nsym);
    RS_CACHE.set(nsym, codec);
  }
  return codec;
}

function normalizeOptions(options) {
  const merged = { ...DEFAULT_OPTIONS, ...(options || {}) };
  const { ppb, repeat, nsym } = merged;
  if (!Number.isInteger(ppb) || ppb < 1 || ppb > COEFFICIENT_PAIRS.length) {
    throw new RangeError(`ppb must be an integer in 1..${COEFFICIENT_PAIRS.length}`);
  }
  if (!Number.isInteger(repeat) || repeat < 1 || repeat > 5) {
    throw new RangeError('repeat must be an integer in 1..5');
  }
  if (!Number.isInteger(nsym) || nsym < 8 || nsym > 64) {
    throw new RangeError('nsym must be an integer in 8..64');
  }
  return merged;
}

/**
 * Payload bytes (including the 11-byte header) that fit in an image.
 * @param {number} width
 * @param {number} height
 * @param {import('./constants.js').EmbedOptions} [options]
 * @returns {number}
 */
export function capacityBytes(width, height, options) {
  const { ppb, repeat, nsym } = normalizeOptions(options);
  const bw = Math.floor(width / BLOCK_SIZE);
  const bh = Math.floor(height / BLOCK_SIZE);
  const totalSlots = bw * bh * ppb;
  const rmax = Math.floor(totalSlots / CODEWORD_BITS);
  const maxCodewords = Math.floor(rmax / repeat);
  return Math.max(0, maxCodewords * (255 - nsym) - PAYLOAD_HEADER_BYTES);
}

/**
 * Assemble the self-describing payload header + body.
 * @param {Uint8Array} jpeg secret image as JPEG bytes
 * @param {import('./constants.js').EmbedOptions} [options]
 * @returns {Uint8Array}
 */
export function buildPayload(jpeg, options) {
  const { ppb, repeat, nsym } = normalizeOptions(options);
  const payload = new Uint8Array(PAYLOAD_HEADER_BYTES + jpeg.length);
  payload.set(MAGIC, 0);
  payload[4] = ppb;
  payload[5] = repeat;
  payload[6] = nsym;
  const len = jpeg.length;
  payload[7] = (len >>> 24) & 0xff;
  payload[8] = (len >>> 16) & 0xff;
  payload[9] = (len >>> 8) & 0xff;
  payload[10] = len & 0xff;
  payload.set(jpeg, PAYLOAD_HEADER_BYTES);
  return payload;
}

/**
 * Embed a payload into a cover image.
 * @param {{data: Uint8ClampedArray|Uint8Array, width: number, height: number}} imageData
 * @param {Uint8Array} payload result of {@link buildPayload}
 * @param {import('./constants.js').EmbedOptions} [options]
 * @returns {{data: Uint8ClampedArray, width: number, height: number}}
 */
export function embed(imageData, payload, options) {
  const { ppb, repeat, nsym, marginMin, marginGain, marginMax } = normalizeOptions(options);
  const { data, width, height } = imageData;
  const bw = Math.floor(width / BLOCK_SIZE);
  const bh = Math.floor(height / BLOCK_SIZE);
  const totalSlots = bw * bh * ppb;
  const rmax = Math.floor(totalSlots / CODEWORD_BITS);
  if (rmax < 1) throw new Error('cover image is too small to embed anything');

  const messageBytes = 255 - nsym;
  const maxCodewords = Math.floor(rmax / repeat);
  if (payload.length > maxCodewords * messageBytes) {
    throw new Error(
      `payload of ${payload.length} B exceeds capacity of ${maxCodewords * messageBytes} B`,
    );
  }

  const { repeated } = encodeStream(payload, { ppb, repeat, nsym });

  const slotBits = new Int8Array(totalSlots).fill(-1);
  for (let k = 0; k < repeated.length; k++) {
    slotBits[slotForIndex(k, rmax)] = repeated[k];
  }

  const rulerField = buildRulerField(width, height, width, height);
  const rulerRgb = new Float64Array(width * height * 3);
  const gray = new Float64Array(width * height);

  for (let i = 0; i < width * height; i++) {
    const r = data[i * 4];
    const g = data[i * 4 + 1];
    const b = data[i * 4 + 2];
    const y = 0.299 * r + 0.587 * g + 0.114 * b;
    const cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
    let cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b + rulerField[i];
    if (cb < 0) cb = 0;
    else if (cb > 255) cb = 255;
    const ncr = cr - 128;
    const ncb = cb - 128;
    const nr = y + 1.402 * ncr;
    const ng = y - 0.344136 * ncb - 0.714136 * ncr;
    const nb = y + 1.772 * ncb;
    rulerRgb[i * 3] = nr;
    rulerRgb[i * 3 + 1] = ng;
    rulerRgb[i * 3 + 2] = nb;
    gray[i] = 0.299 * nr + 0.587 * ng + 0.114 * nb;
  }

  const room = 255 - 2 * marginMin;
  for (let by = 0; by < bh; by++) {
    for (let bx = 0; bx < bw; bx++) {
      const base = (by * bw + bx) * ppb;
      let used = false;
      for (let p = 0; p < ppb; p++) {
        if (slotBits[base + p] >= 0) {
          used = true;
          break;
        }
      }
      if (!used) continue;

      const block = [];
      let bmn = Infinity;
      let bmx = -Infinity;
      for (let i = 0; i < BLOCK_SIZE; i++) {
        block[i] = new Float64Array(BLOCK_SIZE);
        for (let j = 0; j < BLOCK_SIZE; j++) {
          const v = gray[(by * BLOCK_SIZE + i) * width + (bx * BLOCK_SIZE + j)] - 128;
          block[i][j] = v;
          if (v < bmn) bmn = v;
          if (v > bmx) bmx = v;
        }
      }
      if (bmx - bmn > room) {
        const mid = (bmn + bmx) / 2;
        const scale = room / (bmx - bmn);
        for (let i = 0; i < BLOCK_SIZE; i++) {
          for (let j = 0; j < BLOCK_SIZE; j++) {
            block[i][j] = mid + (block[i][j] - mid) * scale;
          }
        }
      }

      const coeffs = dct2d(block);
      for (let p = 0; p < ppb; p++) {
        const bit = slotBits[base + p];
        if (bit < 0) continue;
        const [a, b] = COEFFICIENT_PAIRS[p];
        const c1 = coeffs[a[0]][a[1]];
        const c2 = coeffs[b[0]][b[1]];
        const d = c1 - c2;
        const avgMag = (Math.abs(c1) + Math.abs(c2)) / 2;
        const margin = Math.min(Math.max(marginMin, marginGain * avgMag), marginMax);
        const target = bit === 1 ? margin : -margin;
        if (bit === 1 && d >= target) continue;
        if (bit === 0 && d <= target) continue;
        const delta = (target - d) / 2;
        coeffs[a[0]][a[1]] += delta;
        coeffs[b[0]][b[1]] -= delta;
      }

      const modulated = idct2d(coeffs);
      let mn = Infinity;
      let mx = -Infinity;
      for (let i = 0; i < BLOCK_SIZE; i++) {
        for (let j = 0; j < BLOCK_SIZE; j++) {
          const v = modulated[i][j];
          if (v < mn) mn = v;
          if (v > mx) mx = v;
        }
      }
      let shift = 0;
      if (mx > 127) shift = 127 - mx;
      else if (mn < -128) shift = -128 - mn;
      for (let i = 0; i < BLOCK_SIZE; i++) {
        for (let j = 0; j < BLOCK_SIZE; j++) {
          gray[(by * BLOCK_SIZE + i) * width + (bx * BLOCK_SIZE + j)] =
            modulated[i][j] + shift + 128;
        }
      }
    }
  }

  const out = new Uint8ClampedArray(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    const rgb = reconstructRgb(rulerRgb[i * 3], rulerRgb[i * 3 + 1], rulerRgb[i * 3 + 2], gray[i]);
    out[i * 4] = rgb[0];
    out[i * 4 + 1] = rgb[1];
    out[i * 4 + 2] = rgb[2];
    out[i * 4 + 3] = 255;
  }
  return { data: out, width, height };
}

function encodeStream(payload, { repeat, nsym }) {
  const messageBytes = 255 - nsym;
  const numCodewords = Math.ceil(payload.length / messageBytes);
  const padded = new Uint8Array(numCodewords * messageBytes);
  padded.set(payload, 0);

  const codec = rsFor(nsym);
  const encoded = new Uint8Array(numCodewords * 255);
  for (let c = 0; c < numCodewords; c++) {
    encoded.set(codec.encode(padded.subarray(c * messageBytes, (c + 1) * messageBytes)), c * 255);
  }

  const encodedBits = bytesToBits(encoded);
  const repeated = new Uint8Array(encodedBits.length * repeat);
  for (let i = 0; i < encodedBits.length; i++) {
    for (let r = 0; r < repeat; r++) repeated[i * repeat + r] = encodedBits[i];
  }
  return { encodedBits, repeated };
}

/**
 * Extract a payload from a stego image that is already on the cover's 8x8 grid.
 *
 * The parameter set is recovered by brute force over the small space of
 * (ppb, repeat, nsym) combinations; the header's magic and self-consistency
 * checks reject false positives.
 *
 * @param {{data: Uint8ClampedArray|Uint8Array, width: number, height: number}} imageData
 * @returns {{jpeg: Uint8Array, ppb: number, repeat: number, nsym: number}|null}
 */
export function extract(imageData) {
  const { data, width, height } = imageData;
  const gray = toLuma(data, width, height);
  const bw = Math.floor(width / BLOCK_SIZE);
  const bh = Math.floor(height / BLOCK_SIZE);
  const totalBlocks = bw * bh;
  if (totalBlocks === 0) return null;

  const allDeltas = new Int16Array(totalBlocks * COEFFICIENT_PAIRS.length);
  for (let by = 0; by < bh; by++) {
    for (let bx = 0; bx < bw; bx++) {
      const block = [];
      for (let i = 0; i < BLOCK_SIZE; i++) {
        block[i] = new Float64Array(BLOCK_SIZE);
        for (let j = 0; j < BLOCK_SIZE; j++) {
          block[i][j] = gray[(by * BLOCK_SIZE + i) * width + (bx * BLOCK_SIZE + j)] - 128;
        }
      }
      const coeffs = dct2d(block);
      const base = (by * bw + bx) * COEFFICIENT_PAIRS.length;
      for (let p = 0; p < COEFFICIENT_PAIRS.length; p++) {
        const [a, b] = COEFFICIENT_PAIRS[p];
        allDeltas[base + p] = Math.round(coeffs[a[0]][a[1]] - coeffs[b[0]][b[1]]);
      }
    }
  }

  for (let ppb = 1; ppb <= COEFFICIENT_PAIRS.length; ppb++) {
    const totalSlots = totalBlocks * ppb;
    const rmax = Math.floor(totalSlots / CODEWORD_BITS);
    if (rmax < 2) continue;

    const slotBits = new Uint8Array(totalSlots);
    for (let blockIndex = 0; blockIndex < totalBlocks; blockIndex++) {
      const base = blockIndex * ppb;
      const deltaBase = blockIndex * COEFFICIENT_PAIRS.length;
      for (let p = 0; p < ppb; p++) {
        slotBits[base + p] = allDeltas[deltaBase + p] >= READ_THRESHOLD ? 1 : 0;
      }
    }

    for (let repeat = 1; repeat <= 5; repeat++) {
      let firstBits;
      if (repeat > 1) {
        const raw = deinterleave(slotBits, rmax, CODEWORD_BITS * repeat);
        firstBits = new Uint8Array(CODEWORD_BITS);
        for (let i = 0; i < CODEWORD_BITS; i++) firstBits[i] = raw[i * repeat];
      } else {
        firstBits = deinterleave(slotBits, rmax, CODEWORD_BITS);
      }
      const firstBytes = bitsToBytes(firstBits);

      for (let nsym = 8; nsym <= 64; nsym++) {
        const codec = rsFor(nsym);
        const first = codec.decode(firstBytes);
        if (!first) continue;
        if (
          first[0] !== MAGIC[0] ||
          first[1] !== MAGIC[1] ||
          first[2] !== MAGIC[2] ||
          first[3] !== MAGIC[3]
        ) {
          continue;
        }
        const headerPpb = first[4];
        const headerRepeat = first[5];
        const headerNsym = first[6];
        if (
          headerPpb !== ppb ||
          headerRepeat !== repeat ||
          headerNsym !== nsym ||
          headerPpb < 1 ||
          headerPpb > COEFFICIENT_PAIRS.length ||
          headerRepeat < 1 ||
          headerRepeat > 5
        ) {
          continue;
        }

        const messageBytes = 255 - nsym;
        const len =
          ((first[7] << 24) | (first[8] << 16) | (first[9] << 8) | first[10]) >>> 0;
        if (len <= 0 || len > MAX_PAYLOAD_BYTES) continue;
        const numCodewords = Math.ceil((PAYLOAD_HEADER_BYTES + len) / messageBytes);
        if (numCodewords > rmax) continue;

        const payload = decodeStream(slotBits, rmax, repeat, numCodewords, messageBytes, codec);
        if (!payload) continue;

        const jpeg = payload.slice(PAYLOAD_HEADER_BYTES, PAYLOAD_HEADER_BYTES + len);
        return { jpeg, ppb, repeat, nsym };
      }
    }
  }
  return null;
}

function decodeStream(slotBits, rmax, repeat, numCodewords, messageBytes, codec) {
  let allBits;
  if (repeat > 1) {
    const raw = deinterleave(slotBits, rmax, numCodewords * CODEWORD_BITS * repeat);
    allBits = new Uint8Array(numCodewords * CODEWORD_BITS);
    for (let j = 0; j < allBits.length; j++) allBits[j] = raw[j * repeat];
  } else {
    allBits = deinterleave(slotBits, rmax, numCodewords * CODEWORD_BITS);
  }
  const allBytes = bitsToBytes(allBits);
  const payload = new Uint8Array(numCodewords * messageBytes);
  for (let c = 0; c < numCodewords; c++) {
    const message = codec.decode(allBytes.subarray(c * 255, (c + 1) * 255));
    if (!message) return null;
    payload.set(message.subarray(0, messageBytes), c * messageBytes);
  }
  return payload;
}
