/**
 * Reed–Solomon forward error correction over GF(256).
 *
 * Field: GF(2^8) with primitive polynomial `0x11d` and generator `2`.
 * A codeword is 255 bytes; `nsym` of them are parity, so up to `floor(nsym/2)`
 * symbol errors can be corrected (twice that many if erasure positions are
 * known, which this implementation does not expose).
 *
 * The implementation follows the classic syndrome → error-locator
 * (Berlekamp–Massey) → Chien search → Forney pipeline.
 *
 * @module reed-solomon
 */

const PRIMITIVE = 0x11d;
const FIELD_SIZE = 256;
const CODEWORD_BYTES = 255;

const EXP = new Uint8Array(512);
const LOG = new Uint8Array(FIELD_SIZE);

(function buildTables() {
  let x = 1;
  for (let i = 0; i < CODEWORD_BYTES; i++) {
    EXP[i] = x;
    LOG[x] = i;
    x <<= 1;
    if (x & 0x100) x ^= PRIMITIVE;
  }
  for (let i = CODEWORD_BYTES; i < 512; i++) EXP[i] = EXP[i - CODEWORD_BYTES];
})();

function gfMul(a, b) {
  if (a === 0 || b === 0) return 0;
  return EXP[LOG[a] + LOG[b]];
}

function gfDiv(a, b) {
  if (a === 0) return 0;
  return EXP[(LOG[a] - LOG[b] + CODEWORD_BYTES) % CODEWORD_BYTES];
}

function gfInv(a) {
  return EXP[CODEWORD_BYTES - LOG[a]];
}

function gfPow(a, n) {
  const e = ((n % CODEWORD_BYTES) + CODEWORD_BYTES) % CODEWORD_BYTES;
  return EXP[(LOG[a] * e) % CODEWORD_BYTES];
}

function polyScale(p, x) {
  return p.map((c) => gfMul(c, x));
}

function polyAdd(p, q) {
  const r = new Array(Math.max(p.length, q.length)).fill(0);
  for (let i = 0; i < p.length; i++) r[i + r.length - p.length] = p[i];
  for (let i = 0; i < q.length; i++) r[i + r.length - q.length] ^= q[i];
  return r;
}

function polyMul(p, q) {
  const r = new Array(p.length + q.length - 1).fill(0);
  for (let j = 0; j < q.length; j++) {
    for (let i = 0; i < p.length; i++) r[i + j] ^= gfMul(p[i], q[j]);
  }
  return r;
}

function polyEval(p, x) {
  let y = p[0];
  for (let i = 1; i < p.length; i++) y = gfMul(y, x) ^ p[i];
  return y;
}

function polyDiv(dividend, divisor) {
  const out = dividend.slice();
  const limit = dividend.length - (divisor.length - 1);
  for (let i = 0; i < limit; i++) {
    const c = out[i];
    if (c !== 0) {
      for (let j = 1; j < divisor.length; j++) {
        if (divisor[j] !== 0) out[i + j] ^= gfMul(divisor[j], c);
      }
    }
  }
  const sep = -(divisor.length - 1);
  return [out.slice(0, sep), out.slice(sep)];
}

function generatorPoly(nsym) {
  let g = [1];
  for (let i = 0; i < nsym; i++) g = polyMul(g, [1, gfPow(2, i)]);
  return g;
}

function syndromes(msg, nsym) {
  const s = new Array(nsym + 1).fill(0);
  const a = Array.from(msg);
  for (let i = 0; i < nsym; i++) s[i + 1] = polyEval(a, gfPow(2, i));
  return s;
}

function findErrorLocator(synd, nsym) {
  let errLoc = [1];
  let oldLoc = [1];
  const shift = synd.length - nsym;
  for (let i = 0; i < nsym; i++) {
    const K = i + shift;
    let d = synd[K];
    for (let j = 1; j < errLoc.length; j++) {
      d ^= gfMul(errLoc[errLoc.length - 1 - j], synd[K - j]);
    }
    oldLoc = oldLoc.concat([0]);
    if (d !== 0) {
      if (oldLoc.length > errLoc.length) {
        const next = polyScale(oldLoc, d);
        oldLoc = polyScale(errLoc, gfInv(d));
        errLoc = next;
      }
      errLoc = polyAdd(errLoc, polyScale(oldLoc, d));
    }
  }
  let i = 0;
  while (i < errLoc.length && errLoc[i] === 0) i++;
  errLoc = errLoc.slice(i);
  if ((errLoc.length - 1) * 2 > nsym) return null;
  return errLoc;
}

function findErrorPositions(errLoc, messageLength) {
  const errorCount = errLoc.length - 1;
  const positions = [];
  for (let i = 0; i < messageLength; i++) {
    if (polyEval(errLoc, gfPow(2, -i)) === 0) positions.push(messageLength - 1 - i);
  }
  if (positions.length !== errorCount) return null;
  return positions;
}

function correctErrors(msg, synd, errPos) {
  const m = msg.length;
  const coefPos = errPos.map((p) => m - 1 - p);
  let errLoc = [1];
  for (let i = 0; i < coefPos.length; i++) {
    errLoc = polyMul(errLoc, polyAdd([1], [gfPow(2, coefPos[i]), 0]));
  }
  let errEval = polyDiv(
    polyMul(synd.slice().reverse(), errLoc),
    [1].concat(new Array(errLoc.length).fill(0)),
  )[1];
  errEval = errEval.slice().reverse();
  const X = coefPos.map((cp) => gfPow(2, cp));
  const out = Array.from(msg);
  for (let i = 0; i < X.length; i++) {
    const Xi = X[i];
    const XiInv = gfInv(Xi);
    let den = 1;
    for (let j = 0; j < X.length; j++) {
      if (j !== i) den = gfMul(den, 1 ^ gfMul(XiInv, X[j]));
    }
    let y = polyEval(errEval.slice().reverse(), XiInv);
    y = gfMul(gfPow(Xi, 1), y);
    out[errPos[i]] ^= gfDiv(y, den);
  }
  return out;
}

/**
 * Reed–Solomon codec for a fixed number of parity symbols.
 */
export class ReedSolomon {
  /** @param {number} nsym parity symbols per 255-byte codeword (1..254) */
  constructor(nsym) {
    if (!Number.isInteger(nsym) || nsym <= 0 || nsym >= CODEWORD_BYTES) {
      throw new RangeError(`nsym must be in 1..${CODEWORD_BYTES - 1}, got ${nsym}`);
    }
    this.nsym = nsym;
    this.generator = generatorPoly(nsym);
  }

  /** Payload bytes per coded byte. */
  get rate() {
    return (CODEWORD_BYTES - this.nsym) / CODEWORD_BYTES;
  }

  /**
   * Encode one message (<= 255 - nsym bytes) into a 255-byte codeword.
   * @param {Uint8Array|ArrayLike<number>} message
   * @returns {Uint8Array}
   */
  encode(message) {
    const nsym = this.nsym;
    const gen = this.generator;
    const work = new Uint8Array(message.length + nsym);
    work.set(message, 0);
    for (let i = 0; i < message.length; i++) {
      const c = work[i];
      if (c !== 0) {
        for (let j = 0; j < gen.length; j++) work[i + j] ^= gfMul(gen[j], c);
      }
    }
    const out = new Uint8Array(message.length + nsym);
    out.set(message, 0);
    out.set(work.subarray(message.length), message.length);
    return out;
  }

  /**
   * Decode a codeword, correcting up to `floor(nsym/2)` symbol errors.
   * @param {Uint8Array|ArrayLike<number>} received
   * @returns {Uint8Array|null} corrected codeword, or null if uncorrectable
   */
  decode(received) {
    const nsym = this.nsym;
    const msg = Array.from(received);
    const synd = syndromes(msg, nsym);
    if (synd.every((s) => s === 0)) return Uint8Array.from(msg);

    const errLoc = findErrorLocator(synd, nsym);
    if (!errLoc) return null;
    const errPos = findErrorPositions(errLoc, msg.length);
    if (!errPos) return null;

    const corrected = correctErrors(msg, synd, errPos);
    const check = syndromes(corrected, nsym);
    if (!check.every((s) => s === 0)) return null;
    return Uint8Array.from(corrected);
  }
}
