# PadLab — Android sampler

MPC-style 4×16 pad sampler (banks A–D) with an SP-303 / SP-202 inspired master effects rack,
16-step sequencer, microphone sampling and file loading. Java, no dependencies, ~45 KB APK.

Install: copy `dist/padlab-debug.apk` to the phone and open it (allow "install unknown apps").
Android 5.0+ (API 21).

## Features
- **Pads** — 16 pads × 4 banks, multi-touch, MPC layout (pad 1 bottom-left). Built-in synthesised
  808-ish kit + demo beat so it makes sound on first launch. Full-level or velocity-from-touch-height.
- **Edit** — per pad: volume, pitch (±24 st, resampled), start/end trim with waveform, reverse,
  play mode (one-shot / gate / latched loop), choke groups (closed/open hat pre-set).
- **Load** — WAV (8/16/24/32-bit, float, mono/stereo) natively; mp3/aac/ogg/flac via MediaCodec.
- **MIC** — record straight into the selected pad (up to 60 s, leading silence trimmed).
- **Sequencer** — 16 steps × all pads, BPM, swing, drag-to-paint, live OVERDUB (quantised to steps).
- **FX rack** (master bus, signal order): Isolator → Filter+Drive → Lo-Fi → Ring Mod → Resonator →
  Flanger → Slicer → Sync Delay → Reverb → Compressor → Vinyl Sim. Presets: 303 Vinyl, Dusty Lo-Fi,
  Tape Echo, Isolator breaks, Ring Drone, 202 Space, Gate Chop, Filter Sweep.

The effects are *inspired by* the Roland units (Vinyl Sim wow/flutter/crackle, 3-band Isolator kill,
Lo-Fi, Sustain/Attack/Level compressor, …) — they are not sample-accurate emulations.

## Build
`./build.sh` — no Gradle needed. Requires JDK 8+, `aapt`, `dx`, `zipalign`, `apksigner` and an
`android.jar` (Debian/Ubuntu: `apt install aapt dalvik-exchange zipalign apksigner libandroid-23-java`).
Output is signed with a throw-away debug key generated into `build/`.

`test/DspCheck.java` renders the engine headlessly on a JVM (every effect, extremes, pad modes) and
fails on NaN / clipping / silence:
```
javac -d build/test src/com/stecaklab/padlab/{Sample,Pad,Engine,Fx,DemoKit}.java test/DspCheck.java
java -cp build/test com.stecaklab.padlab.DspCheck
```

## Not done yet
Projects/samples are not saved between launches, no per-pad FX sends or bounce/export, no MIDI.
Built and DSP-tested off-device; it has not been run on a physical phone yet.
