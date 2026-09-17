/**
 * Orthonormal 8x8 type-II DCT and its inverse.
 *
 * The transform matches the JPEG kernel closely enough that low-frequency
 * structure survives re-encoding, which is the basis of the whole codec.
 *
 * @module dct
 */
import { BLOCK_SIZE } from './constants.js';

const N = BLOCK_SIZE;
const SCALE_DC = Math.sqrt(1 / N);
const SCALE_AC = Math.sqrt(2 / N);

/** Cosine basis table: `COS[k][n] = cos(pi (2n+1) k / 2N)`. */
const COS = (() => {
  const table = [];
  for (let k = 0; k < N; k++) {
    table[k] = new Float64Array(N);
    for (let n = 0; n < N; n++) {
      table[k][n] = Math.cos((Math.PI * (2 * n + 1) * k) / (2 * N));
    }
  }
  return table;
})();

/**
 * One-dimensional DCT-II.
 * @param {ArrayLike<number>} input length-N samples
 * @returns {Float64Array} length-N coefficients
 */
export function dct1d(input) {
  const out = new Float64Array(N);
  for (let k = 0; k < N; k++) {
    let sum = 0;
    const row = COS[k];
    for (let n = 0; n < N; n++) sum += input[n] * row[n];
    out[k] = sum * (k === 0 ? SCALE_DC : SCALE_AC);
  }
  return out;
}

/**
 * One-dimensional inverse DCT-II.
 * @param {ArrayLike<number>} input length-N coefficients
 * @returns {Float64Array} length-N samples
 */
export function idct1d(input) {
  const out = new Float64Array(N);
  for (let n = 0; n < N; n++) {
    let sum = 0;
    for (let k = 0; k < N; k++) {
      sum += (k === 0 ? SCALE_DC : SCALE_AC) * input[k] * COS[k][n];
    }
    out[n] = sum;
  }
  return out;
}

/**
 * Separable two-dimensional DCT-II.
 * @param {ArrayLike<ArrayLike<number>>} block NxN row-major samples
 * @returns {Float64Array[]} NxN row-major coefficients
 */
export function dct2d(block) {
  const rows = [];
  for (let i = 0; i < N; i++) rows[i] = dct1d(block[i]);
  const out = [];
  const column = new Float64Array(N);
  for (let j = 0; j < N; j++) {
    for (let i = 0; i < N; i++) column[i] = rows[i][j];
    const transformed = dct1d(column);
    for (let i = 0; i < N; i++) {
      if (!out[i]) out[i] = new Float64Array(N);
      out[i][j] = transformed[i];
    }
  }
  return out;
}

/**
 * Separable two-dimensional inverse DCT-II.
 * @param {ArrayLike<ArrayLike<number>>} block NxN row-major coefficients
 * @returns {Float64Array[]} NxN row-major samples
 */
export function idct2d(block) {
  const columns = [];
  const column = new Float64Array(N);
  for (let j = 0; j < N; j++) {
    for (let i = 0; i < N; i++) column[i] = block[i][j];
    columns[j] = idct1d(column);
  }
  const out = [];
  for (let i = 0; i < N; i++) {
    const row = new Float64Array(N);
    for (let j = 0; j < N; j++) row[j] = columns[j][i];
    out[i] = idct1d(row);
  }
  return out;
}
