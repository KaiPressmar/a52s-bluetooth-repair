package de.kaipressmar.a52srepair.diagnostics;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Standard exports accept enum events and numeric measurements only. No platform strings. */
public final class RecoveryEventLog {
    public enum Event { START, DISPATCH, END, INTERRUPTED }
    private static final int MAX_EVENTS = 200;
    private RecoveryEventLog() {}
    public static synchronized void record(Context context, Event event, int sequence, long elapsed) {
        File file = file(context);
        List<String> lines = readLines(file);
        lines.add(event.name() + ";" + Math.max(0, sequence) + ";" + Math.max(0, elapsed));
        save(file, lines);
    }
    public static synchronized void observation(Context context,
            de.kaipressmar.a52srepair.core.model.CallAudioSnapshot state, int sequence, long elapsed) {
        File file = file(context);
        List<String> lines = readLines(file);
        int sco = state.scoAudioConnected == null ? -1 : state.scoAudioConnected ? 1 : 0;
        lines.add("OBSERVE;" + Math.max(0, sequence) + ";" + Math.max(0, elapsed) + ";"
                + state.route.name() + ";" + state.phase.name() + ";" + sco + ";"
                + (state.bluetoothRouteAvailable ? 1 : 0) + ";" + (state.repairAllowed ? 1 : 0));
        save(file, lines);
    }
    private static void save(File file, List<String> lines) {
        while (lines.size() > MAX_EVENTS) lines.remove(0);
        try { Files.write(file.toPath(), lines, StandardCharsets.UTF_8); }
        catch (IOException ignored) { /* Logging cannot interfere with a call. */ }
    }
    public static synchronized String read(Context context) {
        return "schema=2 event;sequence;relativeMs;optionalRoute;phase;sco;available;eligible\n" + String.join("\n", readLines(file(context)));
    }
    public static synchronized void clear(Context context) { file(context).delete(); }
    private static List<String> readLines(File file) {
        List<String> result = new ArrayList<>();
        try {
            // Reject arbitrary old or modified text instead of trying to redact identifiers later.
            if (file.length() > 32_768L) return result;
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.matches("(START|DISPATCH|END|INTERRUPTED);[0-9]{1,10};[0-9]{1,19}")
                        || line.matches("OBSERVE;[0-9]{1,10};[0-9]{1,19};(UNKNOWN|BLUETOOTH|EARPIECE|SPEAKER|WIRED_HEADSET|STREAMING);(RINGING|DIALING|ACTIVE|HOLDING|ENDED);(-1|0|1);[01];[01]")) result.add(line);
            }
        } catch (IOException ignored) { }
        return result;
    }
    private static File file(Context context) { return new File(context.getFilesDir(), "recovery-events-v1.txt"); }
}
