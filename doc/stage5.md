# Stage 5: Glide

## Goal
Replace the whole hand-built loader (downsampling, memory cache, disk cache,
cancellation) with Glide, and compare against every earlier stage.

## What I built
- Added Glide (core 4.16.0) and the Glide Compose integration
  (`com.github.bumptech.glide:compose`, version: <FILL IN YOUR VERSION>).
- `GlideImageCell.kt`: a `GlideImage(model = url, contentScale = Crop)` with the
  same padding and square shape as the other cells. No `produceState`, no size
  measuring, no cache code, no sample-size maths.
- `MainActivity.kt` uses `GlideImageCell(url)`.
- `scripts/scroll.sh` now also reads the device's received bytes from
  `/proc/net/dev` before and after the scroll, to measure network use.
  (The `reset` line was briefly dropped by mistake and restored.)

## What I expected
Before this stage I guessed Glide would beat my cache and improve loading time.
I did not write numeric predictions. Loading time was never measured in this
project, so that guess could not be tested.

## What actually happened

### Measured runs
4 cold runs (`pm clear` first) and 4 warm runs, same emulator, Profiler closed,
no crashes. An earlier cold run was discarded because the script had no network
reading yet.

| Run | Frames | Janky | 99th (ms) | Slow draw | PSS (MB) | Native (MB) | Network (MB) |
|---|---|---|---|---|---|---|---|
| cold 1 | 1731 | 2.89% | 20 | 49 | 209 | 63 | 187 |
| cold 2 | 1722 | 3.14% | 20 | 51 | 182 | 63 | 186 |
| cold 3 | 1728 | 3.24% | 20 | 55 | 201 | 61 | 187 |
| cold 4 | 1726 | 8.05% | 20 | 128 | 253 | 63 | 186 |
| warm 1 | 1708 | 1.87% | 20 | 28 | 202 | 59 | 1 |
| warm 2 | 1716 | 1.81% | 20 | 31 | 172 | 58 | 0 |
| warm 3 | 1709 | 1.93% | 20 | 28 | 217 | 59 | 1 |
| warm 4 | 1695 | 10.09% | 20 | 145 | 176 | 58 | 0 |

### Medians compared with earlier stages

| Metric | S1 naive | S2 | S3 (64 MB) | S4 cold | S4 warm | S5 cold | S5 warm |
|---|---|---|---|---|---|---|---|
| Frames | ~193 | 1722 | 1681 | 1721 | ~1705 | ~1727 | ~1709 |
| Janky | 100% | 6.50% | 8.13% | 3.82% | ~3.6% | ~3.2% | ~1.9% |
| 99th percentile | 150 ms | 25 ms | 27 ms | 23 ms | 20 ms | 20 ms | 20 ms |
| End PSS | ~825 MB | ~167 MB | ~204 MB | ~166 MB | 150 to 691 MB | ~205 MB | ~189 MB |
| Native heap | ~744 MB | ~79 MB | ~111 MB | ~88 MB | 79 to 620 MB | ~62 MB | ~59 MB |
| Network | not measured | not measured | not measured | ~377 MB | 0 | ~187 MB | ~0 to 1 MB |

## What it means
- **Glide downloaded each image about once.** Cold network was 186 to 187 MB in
  every run, which equals the size of the image files. My own disk cache
  downloaded about 377 MB for the same scroll. My guess is that Glide avoids
  duplicate in-flight requests for the same URL. I did not verify that.
- **Glide's memory was stable.** Native heap stayed at about 60 to 65 MB in all
  8 runs, with none of the 400 to 700 MB spikes seen in Stage 4. I did not test
  why.
- **Median PSS was not lower than my best stage.** It is about the same as or
  slightly above Stage 4's typical runs (150 to 170 MB). The gain is stability,
  not a lower baseline.
- **Frame stats are still in the noise band.** Janky ranged from 1.8% to 10%
  across runs, so I do not claim a smoothness win.

## Measurement caveats
- 4 runs per group instead of 5.
- **Network is measured differently from Stage 4.** Stage 4 counted bytes inside
  my loader. Stage 5 reads total device receive bytes, which include other apps
  and protocol overhead. The ~2x difference is too large to be explained by this,
  but the numbers are not strictly comparable.
- The GC count was 0 in every Glide run, but that counter already failed in
  Stage 1, so it says nothing about Glide.
- Cold and warm runs were not interleaved, so emulator mode may differ.
- Memory is an end-of-run snapshot with large spread (172 to 253 MB).
- Loading time was never measured.

## What surprised me
- Writing Glide's cell took about 10 lines, far fewer than every earlier stage.
- Glide's network use was half of my hand-built disk cache.
- Its median memory was not lower than my downsampled loader, only steadier.

## What I learned
- Glide's Compose integration is a separate artifact with its own version.
- A library handles things easy to miss by hand: request de-duplication,
  reusing bitmap memory, and sizing its own caches.
- Always check which build is installed before trusting a measurement.

## Open questions
- Does Glide really de-duplicate in-flight requests, and is that the cause of
  the lower network total?
- What keeps its native heap steady?
- How long does each image take to appear? (Never measured.)
- Why can't `rememberSaveable` store the bitmap?