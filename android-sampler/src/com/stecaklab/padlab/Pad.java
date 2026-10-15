package com.stecaklab.padlab;

final class Pad {
    static final int ONE_SHOT = 0, GATE = 1, LOOP = 2;
    static final String[] MODES = {"ONE-SHOT", "GATE", "LOOP"};
    static final int[] FIT = {0, 1, 2, 4, 8, 16};

    volatile Sample sample;
    volatile String name = "";
    volatile float vol = 0.9f, pan;   // pan -1..1
    volatile int pitch;               // semitones
    volatile int mode;
    volatile boolean reverse;
    volatile float start = 0f, end = 1f;
    volatile int choke;               // 0 = off, 1..4 = choke group

    // time-stretch (WSOLA, rendered off-thread)
    volatile float tempo = 1f;        // playback speed factor without changing pitch (if keyLock)
    volatile boolean keyLock = false;  // pitch shifts keep the original duration
    volatile int fitBeats;            // 0 = off, else stretch trimmed region to N beats at project BPM
    volatile Sample rendered;
    volatile String renderedKey, pendingKey;

    // per-pad insert FX (created on first use)
    volatile Fx[] chain;
    boolean fxActive; // audio thread only

    String label() {
        if (name.length() > 0) return name;
        Sample s = sample;
        return s == null ? "" : s.name;
    }

    boolean hasFx() {
        Fx[] c = chain;
        if (c == null) return false;
        for (Fx f : c) if (f.on) return true;
        return false;
    }

    boolean warped() { return fitBeats > 0 || Math.abs(tempo - 1f) > 0.005f || (keyLock && pitch != 0); }
}
