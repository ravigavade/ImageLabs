# Stage 1: Naive loader

## Goal
Load the real images in the simplest possible way and measure what it costs.
"Naive" here means three things on purpose:
- download the full 4000x3000 image
- decode it at full size (`BitmapFactory.decodeStream`, no options, no `inSampleSize`)
- cache nothing, so a cell that scrolls away drops its bitmap and reloads it when it comes back

## What I built
- `NaiveImageLoader.kt`: `loadBitmapNaive(url)` runs on `Dispatchers.IO`, downloads with
  `HttpURLConnection`, decodes with `BitmapFactory.decodeStream`, and returns `null` on failure.
  It rethrows `CancellationException` so a cell leaving the screen can cancel its load.
  It does not catch `OutOfMemoryError`, on purpose.
- `NaiveImageCell.kt`: a composable that uses `produceState` to start the load when the cell
  enters the composition. It shows a gray box until the bitmap arrives or if the load failed.
- The grid in `MainActivity.kt` now uses `NaiveImageCell(url)` for each item.
- `scripts/scroll.sh` now also reports whether the app crashed (`FATAL EXCEPTION` count and
  a `pidof` alive check).

## What I expected
(from stage-0.md) About 48 MB per decoded bitmap against about 1.4 MB actually needed per cell,
hundreds of MB of bitmap memory, GCs above zero, much higher jank, slow image appearance, and
possibly an out-of-memory crash.

## What actually happened

### Decoded size
Logcat printed `decoded 4000x3000 = 45 MB` for every image. 4000 x 3000 x 4 bytes (ARGB_8888)
is 48,000,000 bytes, which is about 45.8 MiB. The "48 MB" in my prediction and the "45 MB"
in the log are the same number, counted in millions of bytes versus 1024-based megabytes.
The compressed JPEG download is only a few MB, so each image grows about 20x when decoded.

### Manual scrolling
- Images took about a second or more to appear, and some took longer.
- Scrolling stuttered a lot.
- The Profiler showed garbage collection on every scroll.
- Flinging fast near the bottom of the list crashed the app. I saw gray cells for a while first.
  I did not capture the crash log, so I do not know whether it was an `OutOfMemoryError` or a
  system kill.

### Scripted measurements
5 runs of `scripts/scroll.sh`, same emulator, Profiler closed.

| Run | Frames | Janky | 50th / 90th / 99th | Slow draw cmds | End PSS (MB) | Native heap (MB) | Crashed |
|---|---|---|---|---|---|---|---|
| 1 | 193 | 100% | 77 / 125 / 150 ms | 193 | 825 | 744 | no |
| 2 | 197 | 100% | 69 / 121 / 150 ms | 197 | 826 | 744 | no |
| 3 | 188 | 100% | 69 / 129 / 150 ms | 188 | 687 | 607 | no |
| 4 | 191 | 100% | 77 / 129 / 150 ms | 191 | 825 | 744 | no |
| 5 | 196 | 100% | 77 / 129 / 150 ms | 196 | 779 | 698 | no |
| **Median** | **193** | **100%** | **77 / 129 / 150** | **193** | **~825** | **~744** | 0 of 5 |

Java heap stayed at about 13 MB in every run.

### Comparison with Stage 0 (placeholder grid)

| Metric | Stage 0 | Stage 1 |
|---|---|---|
| Frames rendered in the scripted scroll | about 1700 | about 193 |
| Janky frames | 2 to 9.5% | 100% |
| 50th percentile frame time | 17 ms | 77 ms |
| 99th percentile frame time | 20 ms | 150 ms |
| End-of-run PSS | about 96 to 109 MB | about 825 MB |
| Java heap | about 12.5 MB | about 13 MB |

## What it means
- **The UI was mostly frozen.** About 9x fewer frames were drawn in the same 30 seconds, and
  every frame missed its deadline.
- **The memory is in native memory, not the Java heap.** On this Android version, bitmap pixels
  are allocated natively. Java heap barely moved, while native heap grew by about 700 MB.
  A check that only watched the Java heap would have missed the whole problem.
- **Memory plateaus around 700 to 825 MB** instead of growing for the entire scroll, so bitmaps
  of cells that left the screen are being reclaimed. This is my inference from the numbers,
  not something I verified directly.
- **The crash only happened on a fast manual fling.** All 5 scripted runs survived
  (0 of 5 crashed), because the script swipes at a fixed pace with pauses.

## Measurement caveats
- **Non-explicit GC count is unreliable and I am dropping it.** The script reported 0 in all
  five runs, yet the Profiler clearly showed GC on every scroll. My Logcat filter
  (`csaim.imagelabs` plus `GC freed`) apparently does not catch these GCs. In Stage 0 it only
  ever matched the "Explicit" ones. GC evidence for this stage is therefore the Profiler
  observation, not a number.
- **High input latency** read 0 here (versus about 1500 in Stage 0). The UI thread was so busy
  that the metric behaved differently, so it stays ignored.
- **Fast/slow emulator mode (Stage 0)** is irrelevant here. The Stage 1 gap is far larger
  than the difference between the two modes.
- Memory is still an end-of-run snapshot, not a peak.
- Run 3 ended at 687 MB, about 140 MB below the others, so run-to-run variation in memory is
  large at this scale.

## What surprised me
- Every decoded image was exactly 45 MB, no matter what was in the picture.
- Java heap did not move at all, even though memory use grew about 8x.
- The scripted scroll survived while a hand fling crashed the app.

## What I learned
- A bitmap is a grid of pixels, so its memory cost is width x height x bytes per pixel and has
  nothing to do with the file size.
- Showing a 12-megapixel image in a roughly 590 px cell wastes about 30x the memory it needs.
- Without a cache outside the composition, a cell that scrolls away throws its bitmap out
  and reloads it on return.
- Cancellation must be rethrown in coroutines, or scrolling cannot cancel loads.

## Open questions
- What exactly kills the app on a fast fling: `OutOfMemoryError` or a system kill?
- Why do some runs end about 140 MB lower in memory than the others?
- Why can't `rememberSaveable` store the bitmap to fix the reload problem?

## Next
Stage 2: decode at the size the cell needs, using `inJustDecodeBounds` and `inSampleSize`.