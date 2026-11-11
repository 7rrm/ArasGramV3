package org.telegram.messenger.voip;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes a local audio file to 48 kHz mono signed 16-bit PCM for the group call engine. */
final class VoiceBroadcastDecoder {
    interface Cancellation { boolean isCancelled(); }
    private static final int OUTPUT_RATE = 48000;
    private static final int MAX_OUTPUT = 200 * 1024 * 1024;
    private static final long TIMEOUT_US = 10000;

    static byte[] decode(String path, Cancellation cancellation) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        boolean started = false;
        try {
            extractor.setDataSource(path);
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; break; }
            }
            if (track < 0) throw new IOException("No audio track");
            extractor.selectTrack(track);
            MediaFormat input = extractor.getTrackFormat(track);
            String mime = input.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(input, null, null, 0);
            codec.start();
            started = true;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            boolean inputEnded = false, outputEnded = false;
            int sampleRate = 0, channels = 0, encoding = AudioFormat.ENCODING_PCM_16BIT;
            long inputFrames = 0, outputFrames = 0;
            long idleStart = android.os.SystemClock.elapsedRealtime();
            while (!outputEnded && !cancellation.isCancelled() && result.size() < MAX_OUTPUT) {
                if (!inputEnded) {
                    int index = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (index >= 0) {
                        ByteBuffer in = codec.getInputBuffer(index);
                        if (in == null) throw new IOException("No decoder input buffer");
                        in.clear();
                        int size = extractor.readSampleData(in, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            codec.queueInputBuffer(index, 0, size, Math.max(0, extractor.getSampleTime()), 0);
                            extractor.advance();
                        }
                        idleStart = android.os.SystemClock.elapsedRealtime();
                    }
                }
                int index = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                    int nextRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int nextChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (sampleRate != 0 && (sampleRate != nextRate || channels != nextChannels)) throw new IOException("Audio format changed");
                    sampleRate = nextRate;
                    channels = nextChannels;
                    if (Build.VERSION.SDK_INT >= 24 && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    if (sampleRate <= 0 || channels < 1 || channels > 2 || encoding != AudioFormat.ENCODING_PCM_16BIT) throw new IOException("Unsupported PCM format");
                    idleStart = android.os.SystemClock.elapsedRealtime();
                } else if (index >= 0) {
                    try {
                        if (sampleRate == 0 || channels == 0) {
                            MediaFormat format = codec.getOutputFormat();
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                            if (Build.VERSION.SDK_INT >= 24 && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING);
                            if (sampleRate <= 0 || channels < 1 || channels > 2 || encoding != AudioFormat.ENCODING_PCM_16BIT) throw new IOException("Unsupported PCM format");
                        }
                        ByteBuffer out = codec.getOutputBuffer(index);
                        if (out != null && info.size > 0) {
                            out.order(ByteOrder.LITTLE_ENDIAN);
                            out.position(info.offset);
                            out.limit(info.offset + info.size);
                            while (out.remaining() >= channels * 2 && result.size() < MAX_OUTPUT) {
                                int mono = out.getShort();
                                if (channels == 2) mono = (mono + out.getShort()) / 2;
                                inputFrames++;
                                while (outputFrames * (long) sampleRate < inputFrames * OUTPUT_RATE && result.size() < MAX_OUTPUT) {
                                    result.write(mono & 0xff); result.write((mono >> 8) & 0xff); outputFrames++;
                                }
                            }
                        }
                        outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    } finally { codec.releaseOutputBuffer(index, false); }
                    idleStart = android.os.SystemClock.elapsedRealtime();
                }
                if (android.os.SystemClock.elapsedRealtime() - idleStart > 30000) throw new IOException("Decoder stalled");
            }
            if (cancellation.isCancelled()) return null;
            byte[] pcm = result.toByteArray();
            if (pcm.length == 0) throw new IOException("No decoded audio");
            return pcm;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new IOException("Audio decoding failed", e);
        } finally {
            if (codec != null) {
                if (started) { try { codec.stop(); } catch (Exception ignored) {} }
                codec.release();
            }
            extractor.release();
        }
    }
}
