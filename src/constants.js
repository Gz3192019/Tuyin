/**
 * RAC-Hide — shared constants.
 *
 * RAC stands for *Robust Attribute Coding*: information is carried by the
 * **relationship** between two DCT coefficients rather than by any single
 * coefficient value.  Because JPEG quantisation perturbs the two members of a
 * carefully chosen pair in almost the same direction, the sign of their
 * difference survives re-encoding far better than either magnitude.
 *
 * @module constants
 */

/** DCT block edge length. */
export const BLOCK_SIZE = 8;

/** Payload magic, ASCII "STG1". */
export const MAGIC = Uint8Array.of(0x53, 0x54, 0x47, 0x31);

/** Bits in one Reed–Solomon codeword (255 bytes). */
export const CODEWORD_BITS = 255 * 8;

/** Payload header length in bytes: magic(4) + ppb(1) + repeat(1) + nsym(1) + length(4). */
export const PAYLOAD_HEADER_BYTES = 11;

/** Maximum payload accepted by the extractor, in bytes (sanity bound). */
export const MAX_PAYLOAD_BYTES = 10 * 1024 * 1024;

/** |C1 - C2| decision threshold used by the extractor. */
export const READ_THRESHOLD = 2;

/**
 * Coefficient pairs, given as `[[u1, v1], [u2, v2]]` with `u` the horizontal
 * and `v` the vertical frequency index of the 8x8 DCT block.
 *
 * The pairs are ordered low frequency to mid frequency and were selected so
 * that the two coefficients of a pair receive similar JPEG quantisation steps,
 * which is what makes the difference sign robust.  A block can host several
 * pairs; the pairs are mutually disjoint so they never fight over a
 * coefficient.
 *
 * @type {ReadonlyArray<ReadonlyArray<readonly [number, number]>>}
 */
export const COEFFICIENT_PAIRS = Object.freeze([
  [[0, 1], [1, 0]],
  [[0, 2], [2, 0]],
  [[1, 1], [0, 3]],
  [[3, 0], [2, 1]],
  [[1, 2], [0, 4]],
  [[4, 0], [3, 1]],
  [[2, 2], [1, 3]],
  [[0, 5], [5, 0]],
  [[3, 2], [2, 3]],
  [[1, 4], [4, 1]],
  [[0, 6], [6, 0]],
  [[2, 4], [4, 2]],
]);

/** Number of coefficient pairs available. */
export const PAIR_COUNT = COEFFICIENT_PAIRS.length;

/**
 * Default embedding parameters (the "balanced" operating point).
 * @typedef {object} EmbedOptions
 * @property {number} ppb        coefficient pairs used per block (1..12)
 * @property {number} repeat     times each coded bit is written (1..5)
 * @property {number} nsym       Reed–Solomon parity symbols per codeword (8..64)
 * @property {number} marginMin  lower bound of the adaptive margin
 * @property {number} marginGain margin scaling with local coefficient magnitude
 * @property {number} marginMax  upper bound of the adaptive margin
 */
export const DEFAULT_OPTIONS = Object.freeze({
  ppb: 2,
  repeat: 1,
  nsym: 48,
  marginMin: 40,
  marginGain: 1.4,
  marginMax: 200,
});
