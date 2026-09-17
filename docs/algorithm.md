# RAC-Hide — algorithm notes

This document describes the complete embedding and extraction pipeline. It is
intended to be read alongside [`src/`](../src); every section names the module
that implements it.

## 1. Problem statement

A stego image is assumed to travel through an *honest but brutal* channel: the
platform re-encodes it with a lossy codec (JPEG or WebP) and may rescale it to
fit a screen. It is not assumed that anyone is actively trying to detect or
remove the payload.

Under that model a useful scheme must satisfy four requirements:

1. **Perceptual fidelity** — the modification is below the visible threshold.
2. **Robustness** — the payload survives re-compression and proportional
   rescaling.
3. **Blind extraction** — no original cover, no key and no external metadata are
   needed.
4. **Client-side execution** — the whole computation runs in the browser.

## 2. Notation

For an 8×8 block, `F(u, v)` denotes the orthonormal DCT-II coefficient at
horizontal frequency `u` and vertical frequency `v`. `Q(u, v)` is the JPEG
quantisation step applied to that coefficient. A *carrier* is one
`(block, coefficient pair)` slot and stores one bit.

## 3. Coefficient pairs

`src/constants.js`

JPEG reconstruction replaces each coefficient by
`F'(u,v) = round(F(u,v) / Q(u,v)) · Q(u,v)`, so a single coefficient is not
stable. The scheme therefore compares **two coefficients of a pair** and reads
their difference sign:

```
bit = 1   ⇔   F(u1,v1) - F(u2,v2) > 0
bit = 0   ⇔   F(u1,v1) - F(u2,v2) < 0
```

The pairs in `COEFFICIENT_PAIRS` are ordered low → mid frequency and chosen so
that both members receive similar quantisation steps, making the difference sign
far more stable than either magnitude. The pairs are mutually disjoint, so a
block can host several of them without two carriers overwriting the same
coefficient.

## 4. Adaptive-margin sign modulation

`src/core.js`

A bare sign decision is fragile near zero, so the encoder pushes the difference
away from the decision boundary by a margin:

```
d      = C1 - C2
target = bit ? +margin : -margin
margin = clamp(marginMin, marginGain · (|C1| + |C2|) / 2, marginMax)

if the current d already satisfies the target: leave the pair untouched
otherwise:  delta = (target - d) / 2
            C1 += delta
            C2 -= delta
```

The margin grows with the local coefficient magnitude, so flat regions are
modified gently (invisible) while textured regions get a large, masked margin
(robust). The encoder never moves a coefficient more than necessary.

Before the DCT, high-contrast blocks are compressed towards mid-grey into a
`255 - 2·marginMin` window. This reserves headroom so that later modulation
cannot clip, without visibly altering the block.

## 5. Luma-only embedding and chroma-preserving reconstruction

`src/color.js`

Only the luma channel `Y` carries the payload. After modulation the block is
inverse-transformed and every pixel must be turned back into RGB with a luma as
close as possible to the modulated value. A naive per-channel offset clips
highlights and shifts the colour.

`reconstructRgb` instead:

1. distributes the luma error across channels in sensitivity order
   (G → R → B),
2. rounds to integers and searches the 3×3×3 integer neighbourhood for the
   triple whose luma is nearest the target.

This keeps the chroma — and therefore the perceived colour — intact while the
luma matches the encoder's intent to within a fraction of a level, which is what
makes blind extraction reliable.

## 6. Channel coding

`src/reed-solomon.js`, `src/interleave.js`, `src/core.js`

Three layers turn a very noisy channel into a usable one:

**Reed–Solomon.** The payload (header + JPEG) is split into blocks of
`255 - nsym` bytes and each block becomes a 255-byte RS codeword over GF(2⁸)
with primitive polynomial `0x11d`. Up to `floor(nsym/2)` symbol errors per
codeword are corrected.

**Bit repetition.** Each coded bit is written to `repeat` independent carriers.
The decoder reads them back and takes a majority vote.

**Global interleaving.** Logical bit `k` is placed at physical slot

```
slot(k) = (k mod 2040) · rmax + floor(k / 2040),   rmax = floor(totalSlots / 2040)
```

so that a locally damaged region spreads its errors thinly across many
codewords instead of destroying one. The decoder applies the inverse mapping.

## 7. Self-describing payload and blind search

`src/core.js`

The payload starts with an 11-byte header:

```
magic "STG1" (4) | ppb (1) | repeat (1) | nsym (1) | JPEG length (4) | JPEG bytes
```

The extractor knows none of the parameters in advance. It enumerates the small
space of `ppb ∈ [1,12]`, `repeat ∈ [1,5]`, `nsym ∈ [8,64]`, decodes the first
codeword and accepts a combination only when

- the RS decode succeeds,
- the magic matches, and
- the header parameters agree with the combination being tried.

This self-consistency check makes false positives negligible, and the payload is
then decoded in full. No side channel is required.

## 8. Spatial ruler (anti-rescale)

`src/ruler.js`

The payload lives on an absolute 8×8 grid, so a proportional rescale breaks grid
alignment. Writing the cover dimensions into the payload does not help: reading
them already requires the grid. This is a circular dependency.

The ruler breaks it:

- The image is divided into a fixed `112 × 112` lattice. Cell *relative*
  positions are invariant under scaling.
- The cover width and height (two 16-bit values) are packed into 6 bytes with a
  two-byte magic, protected by a short RS(10,6) code, giving 80 bits.
- Each bit is written as a signed luminance offset over ~156 lattice cell
  pairs, then the whole offset field is **bilinearly interpolated** so it has no
  block structure.
- The field is added to the **chroma Cb** channel, while the payload uses luma,
  so the two never interfere. The decoder tries Cb first, then luma (for
  backward compatibility).

On extraction the ruler is read at whatever resolution the image arrives,
recovering the original dimensions; the image is then resampled back to that
size and the normal decoder runs. An aspect-ratio check rejects false positives,
and the original size can be entered manually as a fallback.

## 9. Capacity

```
totalSlots = (W/8) · (H/8) · ppb
rmax       = floor(totalSlots / 2040)
capacity   = floor(rmax / repeat) · (255 - nsym) - 11   bytes
```

Capacity, robustness and invisibility trade off against each other; the three
presets in the README are points on that curve.

## 10. Limitations

- Cropping shifts the ruler lattice and breaks block alignment — not
  recoverable.
- Watermarks and redrawing destroy the signal.
- Robustness depends on image content: flat covers lose small coefficients to
  quantisation more easily than textured ones.
- This is steganography, not encryption. The payload is obfuscated, not
  cryptographically protected; encrypt before embedding if confidentiality
  matters.

## References

- N. Ahmed, T. Natarajan, K. R. Rao, *Discrete cosine transform*, IEEE Trans.
  Computers, 1974.
- I. S. Reed, G. Solomon, *Polynomial codes over certain finite fields*,
  J. SIAM, 1960.
- F. A. P. Petitcolas, R. J. Anderson, M. G. Kuhn, *Information hiding — a
  survey*, Proceedings of the IEEE, 1999.
