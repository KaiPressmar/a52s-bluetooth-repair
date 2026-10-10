package de.kaipressmar.a52srepair.audio;

import android.annotation.SuppressLint;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import java.util.Arrays;
import java.util.function.Consumer;

/** Own short-lived capture stream. Discards samples; no files, network, blocking reads or analysis. */
final class DuplexChannel {
    interface Capture {
        boolean initialized(); boolean prefer(AudioDeviceInfo input); void start(); boolean recording();
        int session(); AudioDeviceInfo routed(); Boolean silenced(); int read(byte[] buffer); void stop(); void release();
    }
    private final Capture record;
    private final AudioTrack track;
    private final AudioDeviceInfo input, output;
    private final Consumer<String> log;
    private final byte[] discard = new byte[320];
    private String failure, lastState = "";
    private int lastRead = Integer.MIN_VALUE;
    private boolean closed;
    @SuppressLint("MissingPermission") // Caller verifies RECORD_AUDIO immediately before construction.
    static DuplexChannel create(AudioTrack track, AudioDeviceInfo input, AudioDeviceInfo output, Consumer<String> log) {
        int minimum = AudioRecord.getMinBufferSize(8_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IllegalStateException("capture buffer unavailable");
        AudioRecord record = new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(8_000)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(Math.max(minimum, 3_200)).build();
        // Assign ownership before any Binder request can fail after acceptance.
        return new DuplexChannel(wrap(record), track, input, output, log);
    }
    private static Capture wrap(AudioRecord record) {
        return new Capture() {
            public boolean initialized() { return record.getState() == AudioRecord.STATE_INITIALIZED; }
            public boolean prefer(AudioDeviceInfo input) { return record.setPreferredDevice(input); }
            public void start() { record.startRecording(); }
            public boolean recording() { return record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING; }
            public int session() { return record.getAudioSessionId(); }
            public AudioDeviceInfo routed() { return record.getRoutedDevice(); }
            public Boolean silenced() {
                AudioRecordingConfiguration config = record.getActiveRecordingConfiguration();
                return config == null ? null : config.isClientSilenced();
            }
            public int read(byte[] buffer) { return record.read(buffer, 0, buffer.length, AudioRecord.READ_NON_BLOCKING); }
            public void stop() { record.stop(); }
            public void release() { record.release(); }
        };
    }
    DuplexChannel(Capture record, AudioTrack track, AudioDeviceInfo input, AudioDeviceInfo output, Consumer<String> log) {
        this.record = record; this.track = track; this.input = input; this.output = output; this.log = log;
    }
    boolean start() {
        try {
            if (!record.initialized() || !record.prefer(input)) {
                failure = "input preference rejected"; log.accept("DUPLEX_FAILURE " + failure); return false;
            }
            record.start();
            boolean active = record.recording();
            if (!active) { failure = "input did not start"; log.accept("DUPLEX_FAILURE " + failure); }
            log.accept("DUPLEX_START active=" + active + " inputId=" + input.getId() + " outputId=" + output.getId()
                    + " session=" + record.session() + " sampleRate=8000 source=VOICE_COMMUNICATION");
            return active;
        } catch (RuntimeException e) { failure = "input start=" + e.getClass().getSimpleName(); log.accept("DUPLEX_FAILURE " + failure); return false; }
    }
    boolean ready() {
        if (closed || failure != null) return false;
        try {
            AudioDeviceInfo routedIn = record.routed(), routedOut = track.getRoutedDevice();
            Boolean silenced = record.silenced();
            if (Boolean.TRUE.equals(silenced)) {
                failure = "capture silenced by Android"; log.accept("DUPLEX_FAILURE " + failure); return false;
            }
            boolean matched = matches(routedIn, input) && matches(routedOut, output);
            String state = "input=" + (routedIn == null ? 0 : routedIn.getId())
                    + " output=" + (routedOut == null ? 0 : routedOut.getId()) + " exactTarget=" + matched
                    + " captureStatusKnown=" + (silenced != null);
            if (!state.equals(lastState)) { lastState = state; log.accept("DUPLEX_ROUTE " + state); }
            // Only read after exact-route evidence; the OS can still change routing between calls.
            if (!matched || silenced == null) return false;
            int read = record.read(discard);
            if (read != lastRead) { lastRead = read; log.accept("DUPLEX_FRAMES bytes=" + read + " samplesDiscarded=true"); }
            if (read < 0) { failure = "input read error=" + read; log.accept("DUPLEX_FAILURE " + failure); }
            // Do not count frames across a route/silencing change as a successful BT check.
            return read > 0 && matches(record.routed(), input) && matches(track.getRoutedDevice(), output)
                    && Boolean.FALSE.equals(record.silenced());
        } catch (RuntimeException e) { failure = "input observation=" + e.getClass().getSimpleName(); log.accept("DUPLEX_FAILURE " + failure); return false; }
        finally { Arrays.fill(discard, (byte) 0); }
    }
    static boolean matches(AudioDeviceInfo actual, AudioDeviceInfo expected) {
        return actual != null && expected != null && actual.getId() != 0 && actual.getId() == expected.getId()
                && actual.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                && actual.isSource() == expected.isSource() && !actual.getAddress().isEmpty()
                && actual.getAddress().equalsIgnoreCase(expected.getAddress());
    }
    String failure() { return failure; }
    int session() { return record.session(); }
    boolean close() {
        if (closed) return true;
        try { if (record.recording()) record.stop(); }
        catch (RuntimeException e) { log.accept("DUPLEX_STOP_ERROR=" + e.getClass().getSimpleName()); }
        try {
            record.release(); closed = true; Arrays.fill(discard, (byte) 0);
            log.accept("DUPLEX_RELEASED"); return true;
        } catch (RuntimeException e) { log.accept("DUPLEX_RELEASE_ERROR=" + e.getClass().getSimpleName()); return false; }
    }
}
