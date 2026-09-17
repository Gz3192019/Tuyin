/**
 * Colour helpers (BT.601).
 *
 * The payload lives in the luma (Y) channel only.  After the luma plane has
 * been modulated we must turn it back into RGB; {@link reconstructRgb} does
 * this while trying hard to (a) hit the requested luma exactly and (b) keep
 * the original chroma, so the embedding stays visually transparent.
 *
 * @module color
 */

/**
 * Extract the luma plane from an RGBA buffer.
 * @param {Uint8ClampedArray|Uint8Array} rgba RGBA pixels, 4 bytes each
 * @param {number} width
 * @param {number} height
 * @returns {Float64Array} luma values in [0, 255]
 */
export function toLuma(rgba, width, height) {
  const out = new Float64Array(width * height);
  for (let i = 0; i < out.length; i++) {
    out[i] = 0.299 * rgba[i * 4] + 0.587 * rgba[i * 4 + 1] + 0.114 * rgba[i * 4 + 2];
  }
  return out;
}

/**
 * Rebuild an RGB triplet whose BT.601 luma equals `targetY`, as close to the
 * original colour as possible.
 *
 * A direct per-channel offset would clip highlights and shadows and introduce
 * a colour cast, so instead we:
 *   1. distribute the luma error over channels in sensitivity order,
 *   2. round to integers and search the 3x3x3 integer neighbourhood for the
 *      triple whose luma is nearest the target.
 *
 * @param {number} r original red
 * @param {number} g original green
 * @param {number} b original blue
 * @param {number} targetY desired luma
 * @returns {[number, number, number]} integer RGB
 */
export function reconstructRgb(r, g, b, targetY) {
  const luma = 0.299 * r + 0.587 * g + 0.114 * b;
  const delta = targetY - luma;
  let r2 = r + delta;
  let g2 = g + delta;
  let b2 = b + delta;
  r2 = clamp255(r2);
  g2 = clamp255(g2);
  b2 = clamp255(b2);

  let error = targetY - (0.299 * r2 + 0.587 * g2 + 0.114 * b2);
  // channel index and its luma weight, most influential first
  const order = [[1, 0.587], [0, 0.299], [2, 0.114]];
  for (let k = 0; k < order.length && Math.abs(error) > 0.35; k++) {
    const channel = order[k][0];
    const weight = order[k][1];
    const value = clamp255((channel === 0 ? r2 : channel === 1 ? g2 : b2) + error / weight);
    if (channel === 0) r2 = value;
    else if (channel === 1) g2 = value;
    else b2 = value;
    error = targetY - (0.299 * r2 + 0.587 * g2 + 0.114 * b2);
  }
  if (Math.abs(error) > 0.35) {
    r2 = g2 = b2 = targetY;
  }

  const r0 = Math.round(r2);
  const g0 = Math.round(g2);
  const b0 = Math.round(b2);
  let bestR = r0;
  let bestG = g0;
  let bestB = b0;
  let bestError = Math.abs(0.299 * r0 + 0.587 * g0 + 0.114 * b0 - targetY);
  for (let dr = -1; dr <= 1; dr++) {
    for (let dg = -1; dg <= 1; dg++) {
      for (let db = -1; db <= 1; db++) {
        const rr = r0 + dr;
        const gg = g0 + dg;
        const bb = b0 + db;
        if (rr < 0 || rr > 255 || gg < 0 || gg > 255 || bb < 0 || bb > 255) continue;
        const e = Math.abs(0.299 * rr + 0.587 * gg + 0.114 * bb - targetY);
        if (e < bestError) {
          bestError = e;
          bestR = rr;
          bestG = gg;
          bestB = bb;
        }
      }
    }
  }
  return [bestR, bestG, bestB];
}

function clamp255(v) {
  return v < 0 ? 0 : v > 255 ? 255 : v;
}
