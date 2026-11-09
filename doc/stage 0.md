# Stage 0: Setup and measuring tools

## Goal
Build a placeholder grid and a repeatable way to measure it, so later stages
can be compared fairly.

## What I built
- `LazyVerticalGrid` with 2 fixed columns and 200 placeholder items
- Fixed-seed URL list (`picsum.photos/seed/{index}/4000/3000`)
- `scripts/scroll.sh`: relaunches the app, scrolls, prints metrics

## Measurement protocol
- Device: Pixel 9 Pro emulator, 2 GB RAM, API 37, 1280x2856. Never changed.
- Build: debug. Compose debug builds are slower than release, so absolute
  numbers look worse than a real app. That is acceptable because every stage
  uses the same build and I compare changes against this baseline.
- Each run: force-stop, relaunch, wait 4 s, reset gfxinfo, 10 swipes down
  and 10 up (150 ms swipes, 1.5 s apart), then read the stats.
- At least 5 runs per stage, median reported.

## Baseline results (two modes)

Identical code and script produced two distinct, stable result clusters.
Across 14 runs, 6 landed in the fast mode and 8 in the slow mode.
Switching between them did not depend on the Profiler being open, a cold
boot, a 60 s rest between runs, or run order. I could not find the cause.

| Mode | Janky % | Slow draw cmds | 99th percentile | End-of-run PSS |
|------|---------|----------------|-----------------|----------------|
| Fast | 2.05 to 2.35 | 31 to 35 | 20 ms | 103 to 109 MB |
| Slow | 9.40 to 9.89 | 118 to 134 | 20 to 21 ms | 96 to 101 MB |

**Method decision:** run each stage at least 5 times, report the median,
and label each run fast or slow. Compare stages only within the same
session. Treat differences smaller than about 0.3 points of janky % within
a mode as noise. This is a known limitation of measuring on an emulator.

## Metric decisions
- **Janky %** is only meaningful over many frames. A hand scroll gave 54%
  over 63 frames; the script gave 2 to 9.5% over about 1700.
- **High input latency** is ignored: scripted `input swipe` events inflate it
  (about 1500 of 1660 frames flagged).
- **GC count** counts only non-"Explicit" Logcat lines. Explicit ones come
  from the Profiler's force-GC button.
- **Memory** is read from `dumpsys meminfo` at the end of each run. The
  Profiler's Graphics row showed 0 B while `gfxinfo` reported about 48 MB of
  GPU textures, and the Profiler froze while the script ran. Limitation:
  this is an end-of-run snapshot, not a peak. Native heap varied by about
  +/-4 MB between identical runs, so memory differences under about 10 MB
  are noise.

## Design decisions
- `GridCells.Fixed(2)` instead of `Adaptive`, so the image size is the same
  on every device. Image box size is cell width minus 16 dp padding.
- Stable `key` per item.

## What I expected (Stage 1 prediction)
A decoded 4000x3000 bitmap is about 48 MB, but each grid cell only needs
about 1.4 MB. With 8 to 12 images on screen, I expect hundreds of MB of
bitmap memory on a 2 GB emulator. Predictions: non-explicit GC count above
0, janky % far above the Stage 0 baseline, images appearing slowly, and
possibly an OutOfMemoryError or the app being killed. These are estimates
to check against the Stage 1 measurements.

## What surprised me
- Hand scrolling and scripted scrolling gave very different jank numbers.
- Every GC line in my first experiment said "Explicit".
- The same code produced two stable timing modes that I could not control.

## Open questions
**Why might a Compose item reload an image it already loaded?**
When an item scrolls off screen it leaves the composition and everything
it remembered is discarded, including its loaded bitmap and any running
load coroutine. When it scrolls back, it is composed again and the loading
code runs again. Without a cache that lives outside the composition
(for example an LruCache at app level), the image is downloaded and
decoded again.