# Stage 3: In-memory LruCache

## Goal
Stop reloading images that were already loaded. Keep decoded bitmaps in an
`LruCache` that lives outside the composition, so scrolling back can skip the
download and decode.

## What I built
- `BitmapCache.kt`: a single `object` holding an `LruCache<String, Bitmap>`.
    - `sizeOf` returns the bitmap size in KB, so the limit is a memory budget,
      not an item count. (Limit and entry size must use the same unit.)
    - `get` and `put`, plus hit and miss counters that are logged on every lookup.
    - The URL is the cache key. That is enough here because every cell asks for
      the same size; it would break if one URL were requested at two sizes.
- `CachedImageCell.kt`: the Stage 2 cell, but it asks the cache first. On a
  miss it loads with `loadBitmapDownsampled` and stores the result.
- `MainActivity.kt` now uses `CachedImageCell(url)`.
- `scripts/scroll.sh` now also prints the last `hits=... misses=...` line.

## What I expected
- With a larger cache, scrolling back should hit the cache and images should
  appear instantly. My prediction was a hit rate above 50% for a 128 MB cache
  during the scripted scroll. (I did not write numeric predictions for the
  other sizes.)
- Check: 24 MB / 2.9 MB per image is about 8 images. The 9th image evicts the
  least recently used one.

## What actually happened

### Cache size
`maxMemory / 8` gave `maxKb = 24576` (24 MB), which holds only about 8 images.
That is fewer than the roughly 9 to 10 cells visible at once, so it was too small
to be useful. I then tried 64 MB (about 22 images). 128 MB was not run.

### Manual scrolling
- With 24 MB: hits 34, misses 294 at the end of a session. It did not feel much
  different from Stage 2.
- With 64 MB: during a short scroll-back, hits climbed (130 to 154) while misses
  stayed frozen at 176, so the cache served those images.

### Scripted measurements
5 runs per size, same emulator, Profiler closed, no crashes in any run.

| Run | Size | Frames | Janky | 99th (ms) | Slow draw | PSS (MB) | Native (MB) | Java (MB) | GC lines | Hits / misses |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 24 MB | 1702 | 5.58% | 18 | 79 | 174 | 85 | 20 | 18 | 18 / 665 |
| 2 | 24 MB | 1671 | 8.74% | 29 | 113 | 269 | 183 | 19 | 5 | 11 / 456 |
| 3 | 24 MB | 1714 | 7.00% | 19 | 91 | 290 | 201 | 20 | 7 | 14 / 558 |
| 4 | 24 MB | 1770 | 5.08% | 19 | 70 | 205 | 106 | 32 | 16 | 18 / 691 |
| 5 | 24 MB | 1760 | 5.28% | 32 | 70 | 178 | 88 | 22 | 23 | 20 / 670 |
| 1 | 64 MB | 1661 | 8.13% | 27 | 113 | 203 | 111 | 24 | 4 | 9 / 462 |
| 2 | 64 MB | 1685 | 8.31% | 22 | 106 | 211 | 126 | 17 | 12 | 15 / 489 |
| 3 | 64 MB | 1664 | 8.17% | 22 | 109 | 216 | 128 | 20 | 6 | 17 / 470 |
| 4 | 64 MB | 1681 | 5.65% | 32 | 68 | 176 | 83 | 22 | 23 | 18 / 658 |
| 5 | 64 MB | 1702 | 7.99% | 27 | 112 | 204 | 101 | 36 | 10 | 17 / 505 |

### Medians compared with Stage 2

| Metric | S2 (no cache) | S3, 24 MB | S3, 64 MB |
|---|---|---|---|
| Frames rendered | 1722 | 1714 | 1681 |
| Janky | 6.50% | 5.58% | 8.13% |
| 99th percentile | 25 ms | 19 ms | 27 ms |
| Slow draw commands | 91 | 79 | 109 |
| End-of-run PSS | ~167 MB | ~205 MB | ~204 MB |
| Native heap | ~79 MB | ~106 MB | ~111 MB |
| Java heap | ~16 MB | ~20 MB | ~22 MB |
| Non-Explicit GC lines | 13 | 16 | 10 |
| Hit rate | n/a | ~2.5% | ~3.0% |

## What it means
- **In the scripted scroll, the cache did not make a measurable difference.**
  Hit rate was about 2 to 3.5% in every run at both sizes. Frames, janky % and
  GC count stay inside the run-to-run spread, so I cannot claim an improvement.
- **Each image was still loaded about 2 to 3 times** (456 to 691 misses for 200
  images). Hypothesis (not verified): the script flings far down the list and
  back, so by the time it returns, the 8 or 22 most recent images are all the
  cache holds and everything else was evicted.
- **The cache costs memory.** End-of-run PSS rose from about 167 MB to about
  205 MB. That is only slightly above the roughly 30 MB noise level, so
  "probably higher" is the honest reading.
- **The cache does work when the scroll pattern fits inside it.** In the manual
  test with 64 MB, a short scroll-back produced a long run of hits with no new
  misses.
- A cache is only as useful as the match between its size and how users scroll.
  The `maxMemory / 8` rule of thumb gave 24 MB, which is less than one screen of
  2.9 MB bitmaps.

## Measurement caveats
- **128 MB was not run.** My prediction for it (hit rate above 50%) is untested.
- **The 24 MB and 64 MB groups were not interleaved.** The process IDs dropped
  from about 32000 to about 1300 between them, which suggests the emulator
  restarted. They may sit in different emulator sessions, and Stage 0 showed
  those can differ (fast and slow mode). Comparing the two sizes with each other
  is therefore weaker than comparing either to "no real gain".
- **The per-lookup log line** stays in for all runs. It adds a little time, equally
  for both sizes.
- The GC count varies a lot (4 to 23) and is only a rough signal.
- Memory is an end-of-run snapshot. Native heap swung from 83 to 201 MB between
  identical runs, so only large differences mean anything.
- I first guessed that the paired log lines were double lookups. They are more
  likely the two grid columns of one row, so the counters are probably accurate.

## What surprised me
- A working cache gave almost no benefit in the scripted run.
- The default cache size (24 MB) held fewer images than fit on one screen.
- Each image was loaded 2 to 3 times even with caching.

## What I learned
- `LruCache` counts entries as 1 each unless `sizeOf` is overridden. Overriding
  it in KB makes the limit a memory budget.
- The cache must live outside the composition, or it dies with each cell.
- LRU evicts the entry not used for the longest time, not the oldest.
- A bigger cache needs more memory. Hit rate depends on the scroll pattern.
- The URL as key is fine for one size, but real libraries put the size in the key.

## Open questions
- Would 128 MB, or a shorter scripted scroll, show a real hit-rate gain?
- Why were misses so high (2 to 3 loads per image)?
- Why can't `rememberSaveable` store the bitmap to fix reloads?

## Next
Stage 4: a disk cache, so images evicted from memory do not need to be
downloaded again.