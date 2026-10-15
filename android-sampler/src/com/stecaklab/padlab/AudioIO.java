package com.stecaklab.padlab;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;

/** Pumps Engine blocks into an AudioTrack on a dedicated high-priority thread. */
final class AudioIO implements Runnable {
    private final Engine engine;
    private volatile boolean running;
    private Thread thread;

    AudioIO(Engine e) { engine = e; }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "padlab-audio");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    @Override public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        int min = AudioTrack.getMinBufferSize(Engine.SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int bytes = Math.max(min, Engine.BLOCK * 4 * 2);
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, Engine.SR, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, bytes, AudioTrack.MODE_STREAM);
        short[] buf = new short[Engine.BLOCK * 2];
        track.play();
        while (running) {
            engine.render(buf);
            track.write(buf, 0, buf.length);
        }
        track.stop();
        track.release();
    }
}
