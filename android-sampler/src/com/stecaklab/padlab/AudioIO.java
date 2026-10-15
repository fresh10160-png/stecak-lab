package com.stecaklab.padlab;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Process;

import java.lang.reflect.Method;

/**
 * Pumps Engine blocks into an AudioTrack on a dedicated thread. Runs at the device's native
 * sample rate and burst size with the low-latency path (API 26+) so pad taps are heard quickly.
 */
final class AudioIO implements Runnable {
    private final Engine engine;
    private final int block;
    private volatile boolean running;
    private Thread thread;

    AudioIO(Engine e, int block) {
        engine = e;
        this.block = Math.max(64, Math.min(512, block));
    }

    static int nativeRate(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            int r = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
            if (r >= 22050 && r <= 96000) return r;
        } catch (Exception ignored) { }
        return 48000;
    }

    static int nativeBurst(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            int b = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
            if (b >= 32 && b <= 2048) return b;
        } catch (Exception ignored) { }
        return 256;
    }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "padlab-audio");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(800); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    private AudioTrack create() {
        int min = AudioTrack.getMinBufferSize(Engine.SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int bytes = Math.max(min, block * 4 * 4);
        if (Build.VERSION.SDK_INT >= 23) {
            AudioTrack.Builder b = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(Engine.SR)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(bytes)
                    .setTransferMode(AudioTrack.MODE_STREAM);
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    Method m = AudioTrack.Builder.class.getMethod("setPerformanceMode", int.class);
                    m.invoke(b, 1); // PERFORMANCE_MODE_LOW_LATENCY
                } catch (Throwable ignored) { }
            }
            return b.build();
        }
        return new AudioTrack(AudioManager.STREAM_MUSIC, Engine.SR, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, bytes, AudioTrack.MODE_STREAM);
    }

    private static void setBufferFrames(AudioTrack t, int frames) {
        if (Build.VERSION.SDK_INT < 24) return;
        try {
            Method m = AudioTrack.class.getMethod("setBufferSizeInFrames", int.class);
            m.invoke(t, frames);
        } catch (Throwable ignored) { }
    }

    private static int underruns(AudioTrack t) {
        if (Build.VERSION.SDK_INT < 24) return 0;
        try {
            Method m = AudioTrack.class.getMethod("getUnderrunCount");
            return (Integer) m.invoke(t);
        } catch (Throwable e) { return 0; }
    }

    @Override public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        AudioTrack track = create();
        int frames = block * 2;
        setBufferFrames(track, frames);
        short[] buf = new short[block * 2];
        track.play();
        int lastUnder = 0, tick = 0;
        while (running) {
            engine.render(buf, block);
            track.write(buf, 0, buf.length);
            // Grow the buffer a burst at a time if the device can't keep up.
            if ((++tick & 63) == 0) {
                int u = underruns(track);
                if (u > lastUnder && frames < block * 8) {
                    frames += block;
                    setBufferFrames(track, frames);
                }
                lastUnder = u;
            }
        }
        track.stop();
        track.release();
    }
}
