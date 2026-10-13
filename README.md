# Vivid Pulse (Android)

Your phone's vitals, live, in a dark neon look with smooth animations
everywhere. Built with the vivo X200 Ultra in mind, works on any Android 8+ phone.

**Install:** on your phone open
**https://github.com/Jambo199/VividPulse/releases/tag/vivid-pulse-latest** and tap
**VividPulse.apk**, open the downloaded file (allow your browser to *install unknown apps* if
asked) and tap **Install**. After that it updates itself: when a new
build is out, a card slides up above the tab bar – tap **Update**, watch it download, confirm the
install – and the first time the new version opens it shows **what's new**. It checks when it
opens and every few hours while it's open (Settings › Updates).

- **Live** – CPU activity with a gauge, a smooth-scrolling chart and a bar per core; RAM in use
  (and vivo's extended RAM+); GPU load and clock where the phone allows; battery level as a
  sloshing liquid tank, charging/discharging watts, temperature; how close the phone is to
  slowing down from heat; network speed up and down; storage used; how long the phone has been
  on and how much of that it slept. Tap any card and it grows
  into its own page with longer history:
  - **Processor** – each cluster (e.g. 2 × Oryon prime cores at 4.32 GHz, 6 performance cores)
    with live clock speeds per core, GPU name, OpenGL / Vulkan versions
  - **RAM** – used, cached and free, extended RAM, Android's low-memory line
  - **Battery** – live charging power chart, temperature chart, current, voltage, health, charge
    cycles, design capacity and an estimate of what it holds now, time to full, and **this
    charge** (time, % gained, % per hour, peak watts, energy) or how fast it's draining since unplugged
  - **Network** – live speeds, Wi-Fi standard (Wi-Fi 7…), band and channel, signal, link speed
  - **Heat** – Android's throttling forecast as a gauge and chart, plus tips for gaming sessions
- **Device** – everything about the phone: name, Android and OriginOS versions (worked out from
  several of vivo's build properties, since phones on OriginOS often still carry the old Funtouch
  names), vivo build number, security update,
  kernel, uptime and deep sleep, chip (e.g. Snapdragon 8 Elite) and its cores, display (resolution,
  refresh rates, HDR formats, size), cameras (megapixels, 35 mm-equivalent lens,
  aperture, stabilisation, 8K / 10-bit / RAW), every sensor with live readings, and features such
  as Wi-Fi 7, NFC, the infrared remote and ultra-wideband. Long-press a row to copy it; share the
  whole spec sheet.
- **Games** – Steam's **top 20 most played** right now with live player counts, today's peak and
  how each moved since last week; this week's **top sellers**; **deals**; and the games **free on
  Epic** now and next. **Search** any game on Steam. Tap a game for its page: live players with a
  chart, price and discount, genres, Metacritic, screenshots, share, and buttons to open it in
  Steam or see its player history. **Star** a game to keep its live player count in *Your games*
  at the top of the page.
- **Free up RAM** – asks Android to make room and shows every step as it happens: how memory
  stands, tidying the app itself, briefly holding a large block of memory so Android drops cached
  files, compresses idle memory into RAM+ and closes apps you haven't used in a while, handing it
  all back, then before/after with what changed. It always keeps a safety margin free, so music
  and anything with a notification keep running. (Since Android 14 apps can't close other apps
  directly; this is how Android itself makes room.) From the Tools tab or the RAM page.
- **Tools** – internet **speed test** (response time, jitter, download and upload on a springy
  speedometer, which Cloudflare server and provider, shareable, with your earlier results), **smoothness** meter (frames per second, late frames,
  refresh rate), **dead pixel** colours at full brightness, multi-finger **touch test**, sensors and a
  **vibration** check.
- Long-press the app icon for shortcuts to the speed test, Steam charts and battery.
- Settings: dark / light theme, how often live readings update, °C / °F, keep the screen on,
  which country's store prices to show, updates.

Android doesn't let apps read true CPU usage any more, so CPU activity is worked out from how fast
each core is clocked (cores speed up as soon as there's work). GPU load and some temperatures are
only shown where the phone lets apps read them.

## Building it yourself

```
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Every push to `main` is built and tested by GitHub Actions (`.github/workflows/android.yml`): unit
tests, then the app is opened on an Android 14 emulator page by page (including the in-app
updater) with screenshots, then the APK is published to the `vivid-pulse-latest` release that the
app updates itself from. Builds are signed with `app/release.jks` so each one installs over the
last; set the `SIGNING_*` environment variables to use your own key.

## Code map

| Where | What |
|---|---|
| `app/.../system/` | Readers for CPU, RAM, GPU, battery, heat, network, storage, display, cameras, sensors; `LiveMonitor` samples them while the app is open |
| `app/.../games/` | Steam charts, search and store pages, Epic free games, starred games |
| `app/.../tools/` | Speed test against Cloudflare |
| `app/.../update/` | The in-app updater |
| `app/.../ui/` | Screens; animated gauges, live charts and cards in `ui/components/` |
| `ci/` | Emulator smoke test, screenshot previews |

Vivid Pulse started out in [PersonalExeMadeByAI](https://github.com/Jambo199/PersonalExeMadeByAI)
next to Vivid Music; its earlier history is there.
