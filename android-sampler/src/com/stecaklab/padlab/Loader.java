package com.stecaklab.padlab;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes WAV directly, anything else (mp3/aac/ogg/flac...) through MediaCodec. */
final class Loader {
    private static final int MAX_SECONDS = 90;

    static Sample load(Context c, Uri uri) throws IOException {
        String name = displayName(c, uri);
        byte[] head = readAll(c, uri, 12);
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') {
            return wav(name, readAll(c, uri, 80 << 20));
        }
        return decode(c, uri, name);
    }

    private static String displayName(Context c, Uri uri) {
        String n = null;
        Cursor q = null;
        try {
            q = c.getContentResolver().query(uri, null, null, null, null);
            if (q != null && q.moveToFirst()) {
                int ix = q.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0) n = q.getString(ix);
            }
        } catch (Exception ignored) {
        } finally {
            if (q != null) q.close();
        }
        if (n == null) n = "SAMPLE";
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        return n.length() > 12 ? n.substring(0, 12) : n;
    }

    private static byte[] readAll(Context c, Uri uri, int max) throws IOException {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("cannot open");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[1 << 16];
            int r;
            while (out.size() < max && (r = in.read(b, 0, Math.min(b.length, max - out.size()))) > 0) out.write(b, 0, r);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static Sample wav(String name, byte[] d) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        int fmt = 0, ch = 0, rate = 0, bits = 0, dataOff = -1, dataLen = 0;
        int p = 12;
        while (p + 8 <= d.length) {
            int id = bb.getInt(p), len = bb.getInt(p + 4);
            if (id == 0x20746d66) { // "fmt "
                fmt = bb.getShort(p + 8) & 0xffff;
                ch = bb.getShort(p + 10);
                rate = bb.getInt(p + 12);
                bits = bb.getShort(p + 22);
                if (fmt == 0xFFFE && len >= 26) fmt = bb.getShort(p + 32) & 0xffff;
            } else if (id == 0x61746164) { // "data"
                dataOff = p + 8;
                dataLen = (len < 0 || dataOff + len > d.length) ? d.length - dataOff : len;
                break;
            }
            if (len < 0) break;
            p += 8 + len + (len & 1);
        }
        if (dataOff < 0 || ch < 1 || ch > 8 || rate <= 0) throw new IOException("bad wav");
        int bytes = bits / 8;
        if ((fmt != 1 && fmt != 3) || bytes < 1 || bytes > 4) throw new IOException("unsupported wav format");
        int frames = Math.min(dataLen / (bytes * ch), rate * MAX_SECONDS);
        float[] l = new float[frames], r = ch > 1 ? new float[frames] : l;
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < Math.min(ch, 2); c++) {
                int o = dataOff + (i * ch + c) * bytes;
                float v;
                if (fmt == 3 && bytes == 4) v = bb.getFloat(o);
                else if (bytes == 1) v = ((d[o] & 0xff) - 128) / 128f;
                else if (bytes == 2) v = bb.getShort(o) / 32768f;
                else if (bytes == 3) v = (((d[o] & 0xff) | ((d[o + 1] & 0xff) << 8) | (d[o + 2] << 16))) / 8388608f;
                else v = bb.getInt(o) / 2147483648f;
                if (c == 0) l[i] = v; else r[i] = v;
            }
        }
        return Sample.prepare(name, l, r, rate);
    }

    private static Sample decode(Context c, Uri uri, String name) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(c, uri, null);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
            }
            if (track < 0) throw new IOException("no audio track");
            ex.selectTrack(track);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();

            int rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            short[] pcm = new short[1 << 20];
            int n = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false, outDone = false;
            while (!outDone) {
                if (!inDone) {
                    int ii = codec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        int sz = ex.readSampleData(ib, 0);
                        if (sz < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            codec.queueInputBuffer(ii, 0, sz, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                } else if (oi >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    ob.position(info.offset);
                    ob.limit(info.offset + info.size);
                    ShortBufferView sv = new ShortBufferView(ob);
                    int cnt = sv.count();
                    if (n + cnt > pcm.length) {
                        if (pcm.length >= rate * ch * MAX_SECONDS) outDone = true;
                        else pcm = java.util.Arrays.copyOf(pcm, Math.max(pcm.length * 2, n + cnt));
                    }
                    if (!outDone) n = sv.copyTo(pcm, n);
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                }
            }
            int c2 = Math.max(1, ch);
            int frames = n / c2;
            float[] l = new float[frames], r = c2 > 1 ? new float[frames] : l;
            for (int i = 0; i < frames; i++) {
                l[i] = pcm[i * c2] / 32768f;
                if (c2 > 1) r[i] = pcm[i * c2 + 1] / 32768f;
            }
            return Sample.prepare(name, l, r, rate);
        } catch (IllegalStateException e) {
            throw new IOException("decode failed: " + e.getMessage());
        } finally {
            if (codec != null) { try { codec.stop(); } catch (Exception ignored) { } codec.release(); }
            ex.release();
        }
    }

    private static final class ShortBufferView {
        private final java.nio.ShortBuffer sb;
        ShortBufferView(ByteBuffer b) { sb = b.order(ByteOrder.nativeOrder()).asShortBuffer(); }
        int count() { return sb.remaining(); }
        int copyTo(short[] dst, int at) { int c = sb.remaining(); sb.get(dst, at, c); return at + c; }
    }
}
