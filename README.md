# ImageLabs

**See exactly how much memory, jank, and bandwidth each image-loading
optimization saves on Android.**

The same 200-image grid, built with Jetpack Compose, is loaded five different
ways. Each strategy is measured with the same script on the same device, so
the numbers are comparable.

## Strategies compared

| Stage | Strategy | Status |
|-------|----------|--------|
| 0 | Setup and measuring tools | Done |
| 1 | Naive: full-size decode, no cache | Done |
| 2 | Manual downsampling (`inSampleSize`) | Done |
| 3 | In-memory `LruCache` | Done |
| 4 | Disk cache | Done |
| 5 | Glide | Done |

Each stage has a git tag (`stage 0` to `stage 5`) and a write-up in
[docs/stages](docs/stages).

## Results

Medians of 4 to 5 runs per stage. Stage 0 is the placeholder grid with no
image loading (about 1700 frames, 96 to 109 MB), so it is the floor.

| Stage | Frames in scripted scroll | End-of-run memory (PSS) | Network, cold run |
|---|---|---|---|
| 1 Naive | ~193 (100% janky) | ~825 MB | not measured |
| 2 Downsampled | ~1722 | ~167 MB | not measured |
| 3 LruCache (64 MB) | ~1681 | ~204 MB | not measured |
| 4 Disk cache | ~1721 | ~166 MB (spikes up to ~690 MB) | ~377 MB |
| 5 Glide | ~1727 | ~205 MB (stable) | ~187 MB |

### Key findings

- **A decoded bitmap costs width x height x 4 bytes.** A 4000x3000 image is
  about 45 MB in memory, even though the downloaded JPEG is about 1 MB. The
  naive loader froze the UI and used about 825 MB.
- **Downsampling fixed most of the damage.** Decoding at the size the cell
  needs (`inSampleSize = 4`, 2.9 MB per image) brought frames back to normal
  and memory down about 5x.
- **The memory cache barely helped in the scripted scroll.** The hit rate was
  only about 2 to 3%, because the script scrolls far enough to evict everything
  before returning. It did work in a short manual scroll-back. A cache has to
  match the scrolling pattern.
- **The disk cache removed warm network use** (0 MB), but my loader downloaded
  about twice the size of the files on disk on cold runs.
- **Glide downloaded each image about once** (~187 MB) and kept native memory
  steady at about 60 MB. Its median memory was not lower than my downsampled
  loader, only more stable.
- **Frame stats from Stage 2 onward are inside emulator noise.** Janky %
  ranged from about 2% to 10% for identical code, so I make no smoothness
  claims between those stages.

Full numbers, caveats and open questions are in each stage's doc.

## Test setup

- **Device:** Pixel 9 Pro emulator, 2 GB RAM, API 37, 1280x2856
- **IDE:** Android Studio Quail 3 | 2026.1.3
- **Build:** debug (used consistently across all stages)
- **Images:** `https://picsum.photos/seed/{0..199}/4000/3000` (fixed seeds, so
  every run loads the same images)
- **Layout:** `LazyVerticalGrid`, 2 fixed columns, 200 items
- **Glide:** 4.16.0 core, plus the Glide Compose integration

## How it is measured

```bash
bash scripts/scroll.sh          # warm run: keeps the disk cache
bash scripts/scroll.sh cold     # cold run: clears app data first
```

The script force-stops and relaunches the app, scrolls down 10 times and up
10 times (about 30 s), then prints:

- total frames, janky frame percentage and 50th / 90th / 99th percentile
  frame times
- slow draw commands
- end-of-run memory (`dumpsys meminfo`)
- whether the app crashed
- cache hit counters (Stages 3 and 4) and network received (Stage 5)

Each stage is run 4 to 5 times and the median is reported. Emulator timing
turned out to be bimodal, so the Stage 0 write-up explains how I handled it:
[docs/stages/stage-0.md](docs/stages/stage-0.md).

## Known limitations

- Debug build on an emulator, so absolute numbers are worse than a real device.
- Memory is an end-of-run snapshot, not a peak.
- Network is counted differently in Stage 4 (inside my loader) and Stage 5
  (device receive bytes), so those two numbers are not strictly comparable.
- The GC count was unreliable and is not used for conclusions.
- Image loading time was never measured.
