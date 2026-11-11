# Stage 2: Manual downsampling

## Goal
Decode each image at roughly the size its grid cell needs, instead of the full
4000x3000. Stage 1 held a 45 MB bitmap to show about 1.4 MB worth of pixels.

## What I built
- `DownsampledImageLoader.kt`
    - `calculateSampleSize(width, height, targetWidth, targetHeight)` returns the
      largest power of 2 that keeps the shrunk image at least as big as the target.
    - `loadBitmapDownsampled(url, targetWidth, targetHeight)` downloads the bytes
      once, reads only the dimensions (`inJustDecodeBounds = true`), computes
      `inSampleSize`, then decodes again at that size.
    - It uses `decodeByteArray` instead of `decodeStream`, because the file has to
      be read twice (once for the size, once for the pixels) and a stream can only
      be read once.
- `DownsampledImageCell.kt`: measures the real pixel size of the image box with
  `onSizeChanged`, and starts the load only once that size is known. No
  hard-coded target size anywhere in the code.
- The grid in `MainActivity.kt` now uses `DownsampledImageCell(url)`. The Stage 1
  naive files are kept as evidence.

## What I expected
- Per image: sample size 4, a 1000x750 bitmap, about 2.9 MB instead of 45 MB
  (about 15x smaller). This was written down before running.
- Frames, jank and memory: I only predicted the direction (better than Stage 1),
  not numbers. Next time I will write numeric predictions first.

## What actually happened

### Per-image size
Logcat printed `sample 4: 1000x750 = 2929 KB` for every image, which matches
the prediction exactly.

### Manual scrolling
- Noticeably smoother than Stage 1, less stutter, no crash when flinging fast.
- The Profiler still showed garbage collection on scrolling. Bitmaps of cells
  that leave the screen are still thrown away and reloaded when they return,
  so the churn is smaller per image but still there.
- Profiler total reached about 260 MB (Java about 29 MB). This is a Profiler
  number, not comparable with the `dumpsys meminfo` numbers below.

### Scripted measurements
5 runs of `scripts/scroll.sh`, same emulator, Profiler closed.

| Run | Frames | Janky | 50th / 90th / 99th (ms) | Slow draw cmds | PSS (MB) | Native (MB) | Java (MB) | Non-Explicit GC lines | Crashed |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 1717 | 7.16% | 16 / 17 / 23 | 97 | 177 | 94 | 16 | 10 | no |
| 2 | 1656 | 8.39% | 16 / 20 / 25 | 113 | 169 | 79 | 23 | 3 | no |
| 3 | 1764 | 4.48% | 16 / 17 / 25 | 62 | 167 | 77 | 23 | 21 | no |
| 4 | 1722 | 6.50% | 16 / 17 / 18 | 91 | 137 | 53 | 16 | 13 | no |
| 5 | 1763 | 5.28% | 16 / 17 / 32 | 61 | 163 | 79 | 16 | 21 | no |
| **Median** | **1722** | **6.50%** | **16 / 17 / 25** | **91** | **~167** | **~79** | **~16** | **13** | 0 of 5 |

### Comparison across stages

| Metric | Stage 0 | Stage 1 (naive) | Stage 2 (downsampled) |
|---|---|---|---|
| Frames rendered | ~1700 | ~193 | ~1722 |
| Janky frames | 2 to 9.5% | 100% | 4.5 to 8.4% |
| 50th percentile | 17 ms | 77 ms | 16 ms |
| 99th percentile | 20 ms | 150 ms | 18 to 32 ms |
| End-of-run PSS | ~96 to 109 MB | ~825 MB | ~167 MB |
| Native heap | ~30 to 42 MB | ~744 MB | ~79 MB |

## What it means
- **The UI is no longer frozen.** Frames rendered went from about 193 to about
  1722, the same level as the placeholder grid. Janky dropped from 100% to about
  6.5%.
- **Memory is about 5x lower than Stage 1** (about 825 to about 167 MB PSS),
  almost all from native heap (about 744 to about 79 MB). It is still about
  60 MB above the placeholder grid, which fits: real bitmaps are in memory now.
- **Why not 15x lower?** Each bitmap is 15x smaller, but memory is not just
  bitmaps. Several images are alive at once, plus the downloaded byte arrays and
  the app's baseline. So the total fell by about 5x, not 15x.
- **Java heap rose a little** (about 13 MB to about 16 to 23 MB). My guess is the
  compressed byte arrays kept by `decodeByteArray`. I did not verify this.

## Measurement caveats
- **The GC counter produced numbers this time** (3 to 21 non-Explicit lines per
  run), while Stage 1 reported 0 even though the Profiler showed GC constantly.
  So the Stage 1 zero was wrong, and I do not know why the filter missed it
  there. **Do not compare the GC column across Stages 1 and 2.** Within Stage 2
  it varies a lot (3 to 21), so it is a rough signal only.
- **Janky % spans 4.5 to 8.4%**, which overlaps both Stage 0 modes (about 2.2%
  and about 9.5%). I cannot say whether Stage 2 equals Stage 0 or is slightly
  worse, only that it is in the same range.
- **Memory noise:** run 4 ended about 30 MB lower than the rest, so memory
  differences under about 30 MB are noise at this scale.
- **High input latency** is still ignored (scripted input inflates it).
- Memory is an end-of-run snapshot, not a peak.

## What surprised me
- The per-image saving (15x) did not translate into a 15x saving in total memory.
- The GC counter suddenly worked, which showed my Stage 1 reading was wrong.
- The shrink factor must be a power of 2, so I cannot hit the cell size exactly.
  The decoded 1000x750 image is still larger than the cell (about 590 px).

## What I learned
- `inSampleSize` is a shrink factor, not a size. 4 means keep every 4th pixel in
  each direction, so 1/16 of the pixels and 1/16 of the memory.
- JPEG decoding can skip pixels cheaply only in power-of-2 steps, so Android
  rounds the value down to a power of 2.
- `inJustDecodeBounds` reads the image size without allocating pixel memory.
- Compose only knows a cell's size after layout, so the loader has to wait for
  `onSizeChanged` instead of guessing a number.
- Downsampling fixes the size of each bitmap but not the reloading: a cell that
  scrolls away still throws its bitmap out.

## Open questions
- Why did the Stage 1 GC filter report 0 when the Profiler showed constant GC?
- Is the Java heap increase really the byte arrays?
- Why can't `rememberSaveable` store the bitmap to fix reloads?

## Next
Stage 3: keep decoded bitmaps in an `LruCache` that lives outside the
composition, sized in bytes, with a hit/miss counter.