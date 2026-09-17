/**
 * Global interleaving.
 *
 * Coded bits are scattered over the whole image so that a locally damaged
 * region (a crop edge, a platform logo, a heavy-quantisation band) spreads its
 * errors thinly across many codewords instead of destroying one of them.
 *
 * Bit `k` of the logical stream is placed at physical slot
 * `(k mod CW) * rmax + floor(k / CW)` where `CW = 255 * 8` and
 * `rmax = floor(totalSlots / CW)`.  The extractor applies the inverse mapping.
 *
 * @module interleave
 */
import { CODEWORD_BITS } from './constants.js';

/**
 * Map a logical bit index to a physical slot index.
 * @param {number} k logical index
 * @param {number} rmax number of codewords that fit
 * @returns {number} slot index
 */
export function slotForIndex(k, rmax) {
  return (k % CODEWORD_BITS) * rmax + Math.floor(k / CODEWORD_BITS);
}

/**
 * Read `count` logical bits back out of a physical slot array.
 * @param {Uint8Array|Int8Array} slotBits physical slots
 * @param {number} rmax number of codewords that fit
 * @param {number} count number of logical bits to read
 * @returns {Uint8Array}
 */
export function deinterleave(slotBits, rmax, count) {
  const out = new Uint8Array(count);
  for (let k = 0; k < count; k++) out[k] = slotBits[slotForIndex(k, rmax)];
  return out;
}
