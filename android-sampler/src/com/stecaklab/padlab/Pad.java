package com.stecaklab.padlab;

final class Pad {
    static final int ONE_SHOT = 0, GATE = 1, LOOP = 2;
    static final String[] MODES = {"ONE-SHOT", "GATE", "LOOP"};

    volatile Sample sample;
    volatile String name = "";
    volatile float vol = 0.9f;
    volatile int pitch;          // semitones
    volatile int mode;
    volatile boolean reverse;
    volatile float start = 0f, end = 1f;
    volatile int choke;          // 0 = off, 1..4 = choke group

    String label() {
        if (name.length() > 0) return name;
        Sample s = sample;
        return s == null ? "" : s.name;
    }
}
