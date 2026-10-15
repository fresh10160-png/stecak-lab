# PadLab — Android sampler

MPC-style 4×16 pad sampler (banks A–D) with an SP-303 / SP-202 inspired master effects rack,
16-step sequencer, microphone sampling and file loading. Java, no dependencies, ~45 KB APK.

Install: copy `dist/padlab-debug.apk` to the phone and open it (allow "install unknown apps").
Android 5.0+ (API 21).

## Features
- **Pads** — 16 pads × 4 banks, multi-touch, MPC layout (pad 1 bottom-left), bank-coloured glow,
  LOOP/GATE/FX/TS tags on each pad. Built-in synthesised 808-ish kit + demo beat. Full-level or
  velocity-from-touch-height.
- **Edit** — waveform with draggable start/end handles, volume, pan, pitch (±24 st), reverse,
  play mode (one-shot / gate / latched loop), choke groups.
- **Time-stretch** — pitch-preserving WSOLA, rendered off the audio thread: *Tempo* knob (50–200 %),
  *Key lock* (pitch changes keep the original length) and *Fit N beats* (stretch the trimmed region to
  exactly 1/2/4/8/16 beats at the project BPM; re-fits when BPM changes).
- **FX** — the same 11-effect rack works on the **master bus** and as an **insert per pad** (each pad
  has its own chain, tails included). Effects: Isolator, Filter+Drive, Lo-Fi, Ring Mod, Resonator,
  Flanger, Slicer, Sync Delay, Reverb, Compressor, Vinyl Sim. Tap a tile to select, tap again to
  switch on/off; knobs plus an XY pad for performing. 10 presets (303 Vinyl, Dusty Lo-Fi, Tape Echo,
  Isolator breaks, Ring Drone, 202 Space, Gate Chop, Filter Sweep…).
- **Voice → instrument (VOICE tab)** — sing or hum into the mic; pitch is tracked with YIN, split
  into notes and shown in a piano roll. Pick an instrument (sine, saw lead, square, pluck, bass, pad,
  e-piano, bell, organ, strings, flute, choir, or **SAMPLE** = any other pad's sample played
  chromatically, or ORIGINAL = your raw take). Constrain to a key/scale, shift octaves, snap to the
  1/16 grid at the project BPM (length rounds up to whole bars), tap a note to nudge ±1 semitone or
  delete it. The result is written into a pad, so trim/stretch/pad FX/sequencer all still apply.
- **Load / MIC** — WAV natively; mp3/aac/ogg/flac via MediaCodec; record from the mic into a pad.
- **Sequencer** — 16 steps × all pads, BPM knob + tap tempo, swing, drag-to-paint, live OVERDUB.
- **Latency** — runs at the device's native sample rate and burst size, low-latency AudioTrack path on
  API 26+, adaptive buffer that grows only if underruns are detected.

The effects are *inspired by* the Roland units (Vinyl Sim wow/flutter/crackle, 3-band Isolator kill,
Lo-Fi, Sustain/Attack/Level compressor, …) — they are not sample-accurate emulations.

## Build
`./build.sh` — no Gradle needed. Requires JDK 8+, `aapt`, `dx`, `zipalign`, `apksigner` and an
`android.jar` (Debian/Ubuntu: `apt install aapt dalvik-exchange zipalign apksigner libandroid-23-java`).
Output is signed with a throw-away debug key generated into `build/`.

`test/VoiceCheck.java` sings synthetic melodies (clean, vibrato, noisy) into the pitch tracker and
renders every instrument. `test/DspCheck.java` renders the engine headlessly on a JVM (every effect, extremes, pad modes) and
fails on NaN / clipping / silence:
```
javac -d build/test src/com/stecaklab/padlab/{Sample,Pad,Engine,Fx,DemoKit,Stretch,Melody,Synth}.java test/*.java
java -cp build/test com.stecaklab.padlab.DspCheck
java -cp build/test com.stecaklab.padlab.VoiceCheck
```

## Not done yet
Projects/samples are not saved between launches, no bounce/export, no MIDI.
Built and tested off-device (DSP on a JVM, UI smoke-tested under Robolectric); not run on a physical phone yet, so latency figures are unmeasured.
