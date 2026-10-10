package de.kaipressmar.a52srepair.diagnostics;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only, bounded init declarations. No hidden APIs, process execution or inferred liveness. */
public final class NativeServiceInventory {
    private static final Pattern SERVICE = Pattern.compile("^service\\s+([A-Za-z0-9_.@-]+)\\s+(/[A-Za-z0-9_./@-]+)(?:\\s|$)");
    private static final int MAX_FILE = 32_768, MAX_TOTAL = 131_072, MAX_FILES = 24;
    private NativeServiceInventory() {}
    public static String capture() {
        return capture(new File[]{new File("/system/etc/init"), new File("/vendor/etc/init"), new File("/odm/etc/init")});
    }
    static String capture(File[] roots) {
        StringBuilder out = new StringBuilder("declaredOnly=true restartPrivilege=false");
        int total = 0, count = 0;
        for (File root : roots) {
            File[] files;
            try { files = root.listFiles(f -> f.getName().endsWith(".rc") && relevant(f.getName())); }
            catch (SecurityException e) { files = null; }
            if (files == null) { out.append("\ninitDirectory=unavailable"); continue; }
            Arrays.sort(files, (a,b) -> a.getName().compareTo(b.getName()));
            for (File file : files) {
                if (count >= MAX_FILES || total >= MAX_TOTAL) { out.append("\ninventoryLimitReached=true"); return out.toString(); }
                count++;
                try (FileInputStream input = new FileInputStream(file)) {
                    byte[] buffer = new byte[Math.min(MAX_FILE, MAX_TOTAL - total)];
                    int offset = 0, read;
                    while (offset < buffer.length && (read = input.read(buffer, offset, buffer.length - offset)) > 0) offset += read;
                    byte[] bytes = Arrays.copyOf(buffer, offset);
                    total += bytes.length;
                    out.append(declarations(new String(bytes, StandardCharsets.UTF_8)));
                } catch (IOException | SecurityException e) { out.append("\ninitFile=unavailable"); }
            }
        }
        return out.append("\nfilesExamined=").append(count).toString();
    }
    private static boolean relevant(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("audio") || lower.contains("bluetooth") || lower.contains("adsprpc") || lower.contains("cdsprpc");
    }
    static String declarations(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            Matcher matcher = SERVICE.matcher(line);
            if (matcher.find() && (relevant(matcher.group(1)) || relevant(matcher.group(2)))) {
                out.append("\nservice=").append(matcher.group(1)).append(" binary=").append(matcher.group(2));
                if (out.length() >= 4096) break;
            }
        }
        return out.toString();
    }
}
