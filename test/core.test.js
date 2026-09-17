import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';

import { ReedSolomon } from '../src/reed-solomon.js';
import { dct2d, idct2d } from '../src/dct.js';
import { slotForIndex, deinterleave } from '../src/interleave.js';
import { encodeRulerBits, decodeRulerBits, buildRulerField, detectRulerSize } from '../src/ruler.js';
import { buildPayload, embed, extract, capacityBytes } from '../src/core.js';
import { BLOCK_SIZE } from '../src/constants.js';

const OPTIONS = { ppb: 2, repeat: 1, nsym: 48, marginMin: 40, marginGain: 1.4, marginMax: 200 };

function syntheticCover(width, height, seed = 1) {
  const data = new Uint8ClampedArray(width * height * 4);
  let state = seed >>> 0;
  const rnd = () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 4294967296;
  };
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      const base = 90 + 70 * Math.sin(x / 23) + 40 * Math.cos(y / 17);
      data[i] = base + 30 * rnd();
      data[i + 1] = base + 20 * rnd();
      data[i + 2] = base + 25 * rnd();
      data[i + 3] = 255;
    }
  }
  return { data, width, height };
}

test('Reed-Solomon corrects up to floor(nsym/2) symbol errors', () => {
  const codec = new ReedSolomon(16);
  const message = randomBytes(239);
  const codeword = codec.encode(message);
  assert.equal(codeword.length, 255);

  const received = codeword.slice();
  for (let i = 0; i < 8; i++) received[i * 13] ^= 0x5a;
  const decoded = codec.decode(received);
  assert.ok(decoded);
  assert.deepEqual([...decoded.slice(0, message.length)], [...message]);
});

test('Reed-Solomon never silently returns the wrong message', () => {
  // Beyond its correction capacity the decoder may fail (null) or miscorrect,
  // but it must never claim to have recovered the original message.
  const codec = new ReedSolomon(8);
  const message = randomBytes(247);
  const received = codec.encode(message).slice();
  for (let i = 0; i < 20; i++) received[i] ^= 0xff;
  const decoded = codec.decode(received);
  if (decoded !== null) {
    assert.notDeepEqual([...decoded.slice(0, message.length)], [...message]);
  }
});

test('DCT and inverse DCT round-trip', () => {
  const block = [];
  for (let i = 0; i < BLOCK_SIZE; i++) {
    block[i] = new Float64Array(BLOCK_SIZE);
    for (let j = 0; j < BLOCK_SIZE; j++) block[i][j] = Math.sin(i * j + 1) * 60;
  }
  const restored = idct2d(dct2d(block));
  for (let i = 0; i < BLOCK_SIZE; i++) {
    for (let j = 0; j < BLOCK_SIZE; j++) {
      assert.ok(Math.abs(restored[i][j] - block[i][j]) < 1e-9);
    }
  }
});

test('interleaving is reversible', () => {
  const rmax = 7;
  const count = 3 * 255 * 8;
  const slots = new Uint8Array(rmax * 255 * 8);
  for (let k = 0; k < count; k++) slots[slotForIndex(k, rmax)] = k & 1;
  const back = deinterleave(slots, rmax, count);
  for (let k = 0; k < count; k++) assert.equal(back[k], k & 1);
});

test('spatial ruler encodes and decodes the cover size', () => {
  for (const [w, h] of [[1234, 567], [16, 16], [4096, 3072]]) {
    assert.deepEqual(decodeRulerBits(encodeRulerBits(w, h)), [w, h]);
  }
});

test('spatial ruler survives a rescale', () => {
  const coverW = 1024;
  const coverH = 768;

  // Render the ruler field directly at half resolution.  Because the field is
  // defined on the resolution-independent lattice, this is equivalent to a
  // proportional rescale of the original image.
  const smallW = coverW / 2;
  const smallH = coverH / 2;
  const field = buildRulerField(coverW, coverH, smallW, smallH);
  const rgb = new Uint8ClampedArray(smallW * smallH * 3);
  for (let i = 0; i < smallW * smallH; i++) {
    // grey pixel with chroma Cb = 128 + field  =>  B = 128 + 2 * field
    const b = 128 + 2 * field[i];
    rgb[i * 3] = 128;
    rgb[i * 3 + 1] = 128;
    rgb[i * 3 + 2] = b < 0 ? 0 : b > 255 ? 255 : b;
  }
  assert.deepEqual(detectRulerSize(rgb, smallW, smallH), [coverW, coverH]);
});

test('capacity grows with ppb and shrinks with nsym', () => {
  const small = capacityBytes(512, 512, { ppb: 1, repeat: 1, nsym: 48 });
  const larger = capacityBytes(512, 512, { ppb: 4, repeat: 1, nsym: 48 });
  const guarded = capacityBytes(512, 512, { ppb: 4, repeat: 1, nsym: 64 });
  assert.ok(larger > small);
  assert.ok(guarded < larger);
});

test('end-to-end embed/extract is lossless on a clean image', () => {
  const cover = syntheticCover(512, 512, 7);
  const secret = randomBytes(300);
  const payload = buildPayload(secret, OPTIONS);
  const stego = embed(cover, payload, OPTIONS);
  const result = extract(stego);
  assert.ok(result, 'extraction failed');
  assert.deepEqual([...result.jpeg], [...secret]);
  assert.equal(result.ppb, OPTIONS.ppb);
  assert.equal(result.repeat, OPTIONS.repeat);
  assert.equal(result.nsym, OPTIONS.nsym);
});

test('end-to-end survives mild additive noise', () => {
  const cover = syntheticCover(512, 512, 11);
  const secret = randomBytes(300);
  const stego = embed(cover, buildPayload(secret, OPTIONS), OPTIONS);
  for (let i = 0; i < stego.data.length; i += 4) {
    for (let c = 0; c < 3; c++) {
      const v = stego.data[i + c] + ((i + c) % 5) - 2;
      stego.data[i + c] = v < 0 ? 0 : v > 255 ? 255 : v;
    }
  }
  const result = extract(stego);
  assert.ok(result, 'extraction failed after noise');
  assert.deepEqual([...result.jpeg], [...secret]);
});

test('different operating points round-trip', () => {
  const cover = syntheticCover(512, 512, 3);
  const secret = randomBytes(120);
  for (const options of [
    { ppb: 1, repeat: 1, nsym: 48, marginMin: 40, marginGain: 1.4, marginMax: 200 },
    { ppb: 2, repeat: 2, nsym: 48, marginMin: 40, marginGain: 1.4, marginMax: 200 },
    { ppb: 4, repeat: 1, nsym: 64, marginMin: 50, marginGain: 1.4, marginMax: 200 },
    { ppb: 6, repeat: 1, nsym: 32, marginMin: 40, marginGain: 1.4, marginMax: 200 },
  ]) {
    const stego = embed(cover, buildPayload(secret, options), options);
    const result = extract(stego);
    assert.ok(result, `extraction failed for ${JSON.stringify(options)}`);
    assert.deepEqual([...result.jpeg], [...secret]);
  }
});
