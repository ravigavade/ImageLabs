# ImageLabs

**See exactly how much memory, jank, and bandwidth
each image-loading optimization saves on Android.**

The same 200-image grid, built with Jetpack Compose, is loaded five different
ways. Each strategy is measured with the same script on the same device, so
the numbers are comparable.

## Strategies compared

| Stage | Strategy | Status |
|-------|----------|--------|
| 0 | Setup and measuring tools | Done |
| 1 | Naive: full-size decode, no cache | Done |
| 2 | Manual downsampling | Done |
| 3 | In-memory `LruCache` | Todo |
| 4 | Disk cache | Todo |
| 5 | Glide | Todo |

## Test setup

- **Device:** Pixel 9 Pro emulator, 2 GB RAM, API 37, 1280x2856
- **IDE:** Android Studio Quail 3 | 2026.1.3
- **Build:** debug (used consistently across all stages)
- **Images:** `https://picsum.photos/seed/{0..199}/4000/3000` (fixed seeds, so every run loads the same images)
- **Layout:** `LazyVerticalGrid`, 2 fixed columns, 200 items

## How it is measured

```bash
bash scripts/scroll.sh
```

The script force-stops and relaunches the app, scrolls down 10 times and up
10 times (about 30 s), then prints:

- total frames and janky frame percentage
- 50th / 90th / 99th percentile frame times
- slow draw commands
- number of system-triggered garbage collections
- end-of-run memory (`dumpsys meminfo`)

Each stage is run at least 5 times and the median is reported. Emulator
timing turned out to be bimodal, see
[docs/stages/stage-0.md](docs/stages/stage-0.md).

## Progress and results

See [docs/PROJECT_TRACKER.md](docs/PROJECT_TRACKER.md).