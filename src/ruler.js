/**
 * Spatial ruler — resolution-independent geometry metadata.
 *
 * The payload lives on an absolute 8x8 grid, so any proportional rescaling by
 * a platform breaks the grid alignment.  Writing the cover dimensions into the
 * payload header does not help: reading the header already requires the grid.
 *
 * The ruler breaks that circular dependency.  It divides the image into a
 * fixed `G x G` lattice whose *relative* positions are preserved under
 * scaling, then encodes the cover width and height as a smooth, low-amplitude
 * luminance offset spread over that lattice.  A decoder can therefore recover
 * the original dimensions at any resolution, normalise the image back and only
 * then run the normal payload decoder.
 *
 * Design notes:
 *  - the ruler is written into the **chroma Cb** channel while the payload
 *    uses **luma Y**, so the two never interfere;
 *  - each bit is repeated over many lattice pairs and protected by a short
 *    Reed–Solomon code;
 *  - the offset field is bilinearly interpolated to avoid block artefacts.
 *
 * @module ruler
 */
import { ReedSolomon } from './reed-solomon.js';

/** Lattice edge length. */
const G = 112;
/** Luminance offset amplitude. */
const D = 6.0;
/** Ruler magic byte. */
const MAGIC_BYTE = 0xa5;
/** Second magic byte, guards against false positives. */
const MAGIC_BYTE2 = 0x5a;
/** Reed–Solomon parity for the 6-byte ruler payload (RS(10,6)). */
const RS_PARITY = 4;
/** Ruler bits: 10 bytes. */
const K = 80;
/** Seed for the deterministic pair shuffle. */
const SEED = 0x51ed;
/** Maximum recoverable dimension. */
const MAX_DIM = 65535;

/**
 * Lattice pairs used to carry the ruler bits.  Half are horizontal neighbours,
 * half vertical, then shuffled deterministically so the two members of a pair
 * are always adjacent in one axis.
 * @type {ReadonlyArray<readonly [number, number, number, number]>}
 */
const PAIRS = (() => {
  const pairs = [];
  for (let j = 0; j < G; j++) {
    for (let i = 0; i + 1 < G; i += 2) pairs.push([i, j, i + 1, j]);
  }
  for (let i = 0; i < G; i++) {
    for (let j = 0; j + 1 < G; j += 2) pairs.push([i, j, i, j + 1]);
  }
  let state = SEED >>> 0;
  const rnd = () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 4294967296;
  };
  for (let k = pairs.length - 1; k > 0; k--) {
    const r = Math.floor(rnd() * (k + 1));
    const tmp = pairs[k];
    pairs[k] = pairs[r];
    pairs[r] = tmp;
  }
  return pairs;
})();

/** Repetitions per ruler bit. */
const R = Math.floor(PAIRS.length / K);

/**
 * Pack the cover dimensions into RS-protected ruler bits.
 * @param {number} coverW
 * @param {number} coverH
 * @returns {Uint8Array} K bits
 */
export function encodeRulerBits(coverW, coverH) {
  const data = [
    MAGIC_BYTE,
    MAGIC_BYTE2,
    (coverW >> 8) & 0xff,
    coverW & 0xff,
    (coverH >> 8) & 0xff,
    coverH & 0xff,
  ];
  const encoded = new ReedSolomon(RS_PARITY).encode(data);
  const bits = new Uint8Array(K);
  for (let i = 0; i < K; i++) bits[i] = (encoded[i >> 3] >> (7 - (i & 7))) & 1;
  return bits;
}

/**
 * Decode ruler bits back to dimensions.
 * @param {Uint8Array} bits K bits
 * @returns {[number, number]|null} [coverW, coverH]
 */
export function decodeRulerBits(bits) {
  const bytes = new Uint8Array(K / 8);
  for (let i = 0; i < K; i++) if (bits[i]) bytes[i >> 3] |= 1 << (7 - (i & 7));
  let decoded;
  try {
    decoded = new ReedSolomon(RS_PARITY).decode(bytes);
  } catch {
    return null;
  }
  if (!decoded || decoded[0] !== MAGIC_BYTE || decoded[1] !== MAGIC_BYTE2) return null;
  const coverW = (decoded[2] << 8) | decoded[3];
  const coverH = (decoded[4] << 8) | decoded[5];
  if (coverW < 16 || coverW > MAX_DIM || coverH < 16 || coverH > MAX_DIM) return null;
  return [coverW, coverH];
}

/**
 * Build the smooth luminance offset field for a cover image.
 * @param {number} coverW
 * @param {number} coverH
 * @param {number} width output field width (usually the working resolution)
 * @param {number} height output field height
 * @returns {Float32Array} `width * height` offsets
 */
export function buildRulerField(coverW, coverH, width, height) {
  const bits = encodeRulerBits(coverW, coverH);
  const lattice = new Float64Array(G * G);
  for (let k = 0; k < K; k++) {
    const sign = bits[k] ? 1 : -1;
    for (let r = 0; r < R; r++) {
      const p = PAIRS[k * R + r];
      lattice[p[1] * G + p[0]] += sign * D;
      lattice[p[3] * G + p[2]] -= sign * D;
    }
  }
  return bilinear(lattice, height, width);
}

function bilinear(lattice, height, width) {
  const out = new Float32Array(height * width);
  for (let y = 0; y < height; y++) {
    const v = ((y + 0.5) * G) / height - 0.5;
    const j0 = Math.floor(v);
    const t = v - j0;
    const j0c = clampInt(j0, 0, G - 1);
    const j1c = clampInt(j0 + 1, 0, G - 1);
    for (let x = 0; x < width; x++) {
      const u = ((x + 0.5) * G) / width - 0.5;
      const i0 = Math.floor(u);
      const s = u - i0;
      const i0c = clampInt(i0, 0, G - 1);
      const i1c = clampInt(i0 + 1, 0, G - 1);
      const a = lattice[j0c * G + i0c] * (1 - s) + lattice[j0c * G + i1c] * s;
      const b = lattice[j1c * G + i0c] * (1 - s) + lattice[j1c * G + i1c] * s;
      out[y * width + x] = a * (1 - t) + b * t;
    }
  }
  return out;
}

function cellBounds(n) {
  const bounds = new Int32Array(G + 1);
  for (let i = 0; i <= G; i++) bounds[i] = Math.round((i * n) / G);
  return bounds;
}

/**
 * Read the lattice and decode the ruler bits from an RGB buffer.
 * @param {Uint8ClampedArray|Uint8Array} rgb `width * height * 3`
 * @param {number} width
 * @param {number} height
 * @param {boolean} useLuma read from luma (legacy) instead of chroma
 * @returns {[number, number]|null}
 */
function readLattice(rgb, width, height, useLuma) {
  const xs = cellBounds(width);
  const ys = cellBounds(height);
  const lattice = new Float64Array(G * G);
  for (let j = 0; j < G; j++) {
    const y0 = ys[j];
    const y1 = ys[j + 1];
    for (let i = 0; i < G; i++) {
      const x0 = xs[i];
      const x1 = xs[i + 1];
      let sum = 0;
      let n = 0;
      for (let y = y0; y < y1; y++) {
        for (let x = x0; x < x1; x++) {
          const p = (y * width + x) * 3;
          sum += useLuma
            ? 0.299 * rgb[p] + 0.587 * rgb[p + 1] + 0.114 * rgb[p + 2]
            : 128 - 0.168736 * rgb[p] - 0.331264 * rgb[p + 1] + 0.5 * rgb[p + 2];
          n++;
        }
      }
      lattice[j * G + i] = n ? sum / n : 0;
    }
  }

  const bits = new Uint8Array(K);
  for (let k = 0; k < K; k++) {
    let acc = 0;
    for (let r = 0; r < R; r++) {
      const q = PAIRS[k * R + r];
      acc += lattice[q[1] * G + q[0]] - lattice[q[3] * G + q[2]];
    }
    bits[k] = acc > 0 ? 1 : 0;
  }
  return decodeRulerBits(bits);
}

/**
 * Recover the cover dimensions from an RGB buffer, trying chroma first and
 * luma second (for images produced by older releases).
 * @param {Uint8ClampedArray|Uint8Array} rgb
 * @param {number} width
 * @param {number} height
 * @returns {[number, number]|null}
 */
export function readRulerSize(rgb, width, height) {
  return readLattice(rgb, width, height, false) || readLattice(rgb, width, height, true);
}

/**
 * Like {@link readRulerSize} but additionally requires the recovered aspect
 * ratio to match the observed image, which rejects false positives.
 * @param {Uint8ClampedArray|Uint8Array} rgb
 * @param {number} width
 * @param {number} height
 * @returns {[number, number]|null}
 */
export function detectRulerSize(rgb, width, height) {
  const got = readRulerSize(rgb, width, height);
  if (!got) return null;
  const expected = got[0] / got[1];
  const observed = width / height;
  if (Math.abs(expected - observed) / observed > 0.06) return null;
  return got;
}

function clampInt(v, lo, hi) {
  return v < lo ? lo : v > hi ? hi : v;
}
