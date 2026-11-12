# Stage 4: Disk cache

## Goal
Keep the downloaded (compressed) image files on disk, so an image evicted from
memory, or an app restart, does not need the network again.

## What I built
- `DiskCache.kt`: one `object` that saves and reads the raw downloaded bytes
  in `cacheDir/images`. The file name is the SHA-256 hash of the URL (URLs
  contain `:` and `/`, which are not valid in file names). It counts disk hits
  and total bytes downloaded (`networkKB`), and logs both on every lookup.
- `DiskCachedImageLoader.kt`: `loadBitmapWithDisk` asks the disk first and
  downloads (then saves) only on a miss. The decode is the same two-pass
  downsampled decode as Stage 2.
- `CachedImageCell.kt` now calls `loadBitmapWithDisk`, so the chain is
  memory cache, then disk, then network.
- `MainActivity.kt` calls `DiskCache.init(applicationContext)` before `setContent`.
- `scripts/scroll.sh`: `cold` argument runs `pm clear` first (empty disk), and the
  script prints the last `BitmapCache` and `DiskCache` log lines.

## What I expected
- I stored the compressed file on disk, not the decoded bitmap: it is much
  smaller, and it can be decoded at any size later.
- After a restart, images should come from disk and network use should drop
  to near zero. I did not write numeric predictions.

## What actually happened

### File size
After a cold run, the folder held 182 files and 195,056 KB, about 1 MB per
image. I had first assumed about 150 KB per image, which was wrong.

### Measured runs
5 cold runs (`pm clear` first) and 4 warm runs (disk already full), same
emulator, Profiler closed, memory cache 64 MB, no crashes.

| Run | Frames | Janky | 99th (ms) | Slow draw | PSS (MB) | Native (MB) | Mem hits / misses | Disk hits | Network (MB) |
|---|---|---|---|---|---|---|---|---|---|
| cold 1 | 1721 | 7.73% | 26 | 113 | 407 | 328 | 6 / 574 | 200 | 386 |
| cold 2 | 1597 | 4.51% | 24 | 57 | 166 | 88 | 14 / 611 | 302 | 318 |
| cold 3 | 1745 | 3.21% | 19 | 42 | 163 | 85 | 12 / 700 | 332 | 382 |
| cold 4 | 1701 | 3.82% | 23 | 53 | 167 | 88 | 10 / 658 | 329 | 338 |
| cold 5 | 1736 | 3.57% | 23 | 53 | 163 | 85 | 12 / 693 | 331 | 377 |
| warm 1 | 1713 | 1.93% | 22 | 30 | 153 | 82 | 20 / 686 | 680 | 0 |
| warm 2 | 1707 | 5.33% | 20 | 82 | 394 | 322 | 20 / 577 | 573 | 0 |
| warm 3 | 1702 | 1.94% | 20 | 31 | 151 | 79 | 16 / 690 | 689 | 0 |
| warm 4 | 1693 | 10.04% | 20 | 144 | 691 | 620 | 12 / 435 | 430 | 0 |

### Medians compared with earlier stages

| Metric | S2 | S3 (64 MB) | S4 cold | S4 warm (4 runs) |
|---|---|---|---|---|
| Frames | 1722 | 1681 | 1721 | ~1705 |
| Janky | 6.50% | 8.13% | 3.82% | ~3.6% |
| 99th percentile | 25 ms | 27 ms | 23 ms | 20 ms |
| Slow draw commands | 91 | 109 | 53 | ~57 |
| Network downloaded | not measured | not measured | ~377 MB | 0 |

## What it means
- **The disk cache removes the network entirely on warm runs:** 0 KB downloaded
  in all 4 warm runs, with disk hits roughly equal to memory misses.
- **Frame stats are too noisy to separate the stages.** Warm janky % ranged
  from 1.9% to 10%, which spans both Stage 0 modes. I do not claim a smoothness gain.
- **The memory cache hit rate is still only about 2 to 3%.** The scripted scroll
  evicts nearly everything before returning, as in Stage 3. The disk cache is
  what catches those misses.
- **Cold runs downloaded about 2x the data that ended up on disk** (about
  377 MB against about 190 MB of files), so some images were downloaded more
  than once.
- **Memory was erratic.** Most runs ended at 150 to 170 MB PSS, but one cold
  run and two warm runs ended at 394 to 691 MB, almost all native heap. My
  guess is that fast disk loads let many decodes pile up before the GC frees
  old bitmaps. I did not verify this.

## Measurement caveats
- 4 warm runs instead of 5.
- **Invalid earlier runs:** the first 6 attempts ran a stale Stage 1 build
  (Logcat showed `NaiveLoader`, and the install date was a day old). They are
  discarded. Lesson: check which build is installed before measuring.
- Cold and warm runs are in order, not interleaved, so emulator mode may differ.
- Memory is an end-of-run snapshot, and its spread (150 to 691 MB) is huge, so only
  large differences mean anything.
- `du` counts disk blocks, so the 1 MB per file figure is slightly generous.
- The per-lookup log lines stay on for all runs.

## What surprised me
- My estimate of 150 KB per image was wrong by about 7x.
- The network total was about double the size of the files on disk.
- Memory spiked to 691 MB in a warm run, even with a 64 MB cache.

## What I learned
- Store the compressed bytes on disk: smaller, and decodable at any size.
- Hash the URL to get a safe, unique file name.
- Check which build is actually installed before trusting a measurement.
- `pm clear` gives a cold start, plain relaunch gives a warm one.

## Open questions
- Why was network traffic about 2x the file total? My hypothesis: cells that scroll
  away and back start a second download of the same URL before the first is saved.
  Glide tracks in-flight requests, which Stage 5 may show.
- Why did memory spike to 400 to 700 MB in some runs?
- Why can't `rememberSaveable` store the bitmap?

## Next
Stage 5: replace the whole loader with Glide and compare against every number above.