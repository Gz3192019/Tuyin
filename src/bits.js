/**
 * Bit / byte conversion helpers.  Bits are packed most-significant first.
 * @module bits
 */

/**
 * @param {Uint8Array|ArrayLike<number>} bytes
 * @returns {Uint8Array} one byte per bit
 */
export function bytesToBits(bytes) {
  const bits = new Uint8Array(bytes.length * 8);
  let p = 0;
  for (let i = 0; i < bytes.length; i++) {
    const byte = bytes[i];
    for (let k = 7; k >= 0; k--) bits[p++] = (byte >> k) & 1;
  }
  return bits;
}

/**
 * @param {Uint8Array|ArrayLike<number>} bits one byte per bit
 * @returns {Uint8Array} packed bytes, trailing partial byte dropped
 */
export function bitsToBytes(bits) {
  const n = Math.floor(bits.length / 8);
  const out = new Uint8Array(n);
  for (let i = 0; i < n; i++) {
    let byte = 0;
    for (let k = 0; k < 8; k++) byte = (byte << 1) | (bits[i * 8 + k] & 1);
    out[i] = byte;
  }
  return out;
}
