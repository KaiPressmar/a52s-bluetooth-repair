package de.kaipressmar.a52srepair;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout content;
    private TextView statusPill;
    private TextView routeTitle;
    private TextView routeSub;
    private TextView lastCheckTitle;
    private TextView lastCheckSub;
    private TextView monitorTitle;
    private TextView monitorSub;
    private TextView details;
    private TextView technicalToggle;

    private final int GREEN = Color.rgb(15, 122, 82);
    private final int GREEN_DARK = Color.rgb(5, 94, 61);
    private final int INK = Color.rgb(18, 32, 26);
    private final int MUTED = Color.rgb(102, 116, 109);
    private final int BG = Color.rgb(246, 249, 247);
    private final int BORDER = Color.rgb(226, 234, 229);

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(30));
        scroll.addView(content);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomNav());
        setContentView(root);

        buildDashboard();
        requestNeededPermissions();
        refresh(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (details != null) refresh(false);
    }

    private void buildDashboard() {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = label("B", 22, Typeface.BOLD, GREEN_DARK);
        mark.setTextColor(Color.WHITE);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(GREEN_DARK, 18));
        head.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);
        brandBox.addView(label(getString(R.string.app_name), 19, Typeface.BOLD, INK));
        brandBox.addView(label("Version " + appVersion(), 11, Typeface.NORMAL, MUTED));
        boolean profileMatch =
                DeviceProfile.matchesTarget(
                        getString(R.string.device_profile_key), Build.MODEL, Build.DEVICE);
        brandBox.addView(
                label(
                        (profileMatch ? "Optimiert für " : "Build-Profil: ")
                                + getString(R.string.device_profile_name)
                                + " · Android 16 / API 36",
                        10,
                        Typeface.NORMAL,
                        profileMatch ? GREEN_DARK : MUTED));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1);
        bp.leftMargin = dp(14);
        head.addView(brandBox, bp);

        TextView gear = label("⚙", 25, Typeface.NORMAL, INK);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(round(Color.WHITE, 18));
        gear.setElevation(dp(1));
        gear.setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        head.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(head);

        add(heroCard(), 18, -1);

        statusPill = label("●  Status wird geprüft", 13, Typeface.BOLD, GREEN_DARK);
        statusPill.setPadding(dp(12), dp(7), dp(12), dp(7));
        statusPill.setBackground(round(Color.rgb(229, 246, 237), 99));
        add(statusPill, 16, -2);

        LinearLayout route = card();
        TextView icon = label("◉", 24, Typeface.BOLD, GREEN);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(Color.rgb(232, 247, 240), 99));
        route.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout rt = new LinearLayout(this);
        rt.setOrientation(LinearLayout.VERTICAL);
        routeTitle = label("Telefonie-Audio", 16, Typeface.BOLD, INK);
        routeSub = label("Verbindung wird geprüft …", 13, Typeface.NORMAL, MUTED);
        rt.addView(routeTitle);
        rt.addView(routeSub);
        LinearLayout.LayoutParams rtp = new LinearLayout.LayoutParams(0, -2, 1);
        rtp.leftMargin = dp(14);
        route.addView(rt, rtp);
        add(route, 14, -1);

        add(sectionHeader("Schnellaktionen", "Prüfen, erkennen und gezielt reparieren"), 24, -1);
        action(
                "✓",
                "Jetzt prüfen & bei Bedarf reparieren",
                "Erkennt einen festhängenden HFP/SCO-Pfad und wählt Bluetooth-Telefonie neu",
                true,
                v -> manualCheckAndRepair());
        action(
                "⌕",
                "Nur Diagnose ausführen",
                "Zustand prüfen und protokollieren, ohne das Routing zu ändern",
                false,
                v -> manualDiagnosis());
        action(
                "↻",
                "SCO/HFP neu verbinden",
                "Bluetooth-Kommunikationsgerät bewusst neu auswählen",
                false,
                v -> forceRepair());

        add(sectionHeader("Auto-Schutz", "Hintergrunddienst mit periodischer Zustandsprüfung"), 24, -1);
        LinearLayout monitor =
                action(
                        "●",
                        "Automatische Überwachung starten",
                        "Im Hintergrund prüfen und einen bestätigten Routingfehler automatisch reparieren",
                        false,
                        v -> toggleMonitor());
        monitorTitle = (TextView) ((LinearLayout) monitor.getChildAt(1)).getChildAt(0);
        monitorSub = (TextView) ((LinearLayout) monitor.getChildAt(1)).getChildAt(1);

        LinearLayout last = card();
        TextView clock = label("◷", 22, Typeface.BOLD, GREEN_DARK);
        clock.setGravity(Gravity.CENTER);
        clock.setBackground(round(Color.rgb(235, 247, 241), 99));
        last.addView(clock, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout lastCopy = new LinearLayout(this);
        lastCopy.setOrientation(LinearLayout.VERTICAL);
        lastCheckTitle = label("Letzte Prüfung", 15, Typeface.BOLD, INK);
        lastCheckSub = label("Noch keine Hintergrundprüfung", 12, Typeface.NORMAL, MUTED);
        lastCopy.addView(lastCheckTitle);
        lastCopy.addView(lastCheckSub);
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(0, -2, 1);
        lcp.leftMargin = dp(13);
        last.addView(lastCopy, lcp);
        add(last, 10, -1);

        LinearLayout tip = card();
        tip.setBackground(round(Color.rgb(233, 248, 240), 18));
        TextView bulb = label("i", 16, Typeface.BOLD, GREEN_DARK);
        bulb.setGravity(Gravity.CENTER);
        bulb.setBackground(round(Color.WHITE, 99));
        tip.addView(bulb, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView tt =
                label(
                        "Auto-Schutz\nWährend eines Anrufs wird nur repariert, wenn Bluetooth-SCO/HFP verfügbar ist, aber bei zwei Prüfungen hintereinander nicht als Kommunikationspfad ausgewählt wurde. Ein absichtlich aktivierter Lautsprecher wird nicht überschrieben.",
                        13,
                        Typeface.NORMAL,
                        INK);
        tt.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams ttp = new LinearLayout.LayoutParams(0, -2, 1);
        ttp.leftMargin = dp(12);
        tip.addView(tt, ttp);
        add(tip, 12, -1);

        add(sectionHeader("Werkzeuge & Diagnose", "Details, Export und Android-Einstellungen"), 24, -1);
        action(
                "⚙",
                "Bluetooth-Einstellungen",
                "Verbindungen direkt in Android verwalten",
                false,
                v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        action(
                "↗",
                "Diagnoseprotokoll teilen",
                "Zustände und Reparaturversuche exportieren",
                false,
                v -> shareLog());

        details = label("", 11, Typeface.NORMAL, MUTED);
        details.setTypeface(Typeface.MONOSPACE);
        details.setPadding(dp(16), dp(14), dp(16), dp(14));
        details.setBackground(round(Color.WHITE, 16));
        details.setVisibility(View.GONE);
        add(details, 10, -1);

        technicalToggle = label("Technische Details anzeigen", 13, Typeface.BOLD, MUTED);
        technicalToggle.setGravity(Gravity.CENTER);
        technicalToggle.setPadding(0, dp(12), 0, dp(12));
        technicalToggle.setOnClickListener(v -> toggleTechnicalDetails());
        add(technicalToggle, 2, -1);
    }

    private void manualDiagnosis() {
        BluetoothRepair.Probe probe = BluetoothRepair.probe(this);
        RepairStateStore.saveHealth(this, probe.health);
        Diag.log(this, "MANUAL DIAGNOSIS\n" + Diag.snapshot(this));
        refresh(false);
        Toast.makeText(this, probe.health.summary, Toast.LENGTH_LONG).show();
    }

    private void manualCheckAndRepair() {
        BluetoothRepair.Probe probe = BluetoothRepair.probe(this);
        RepairStateStore.saveHealth(this, probe.health);
        Diag.log(this, "MANUAL CHECK\n" + Diag.snapshot(this));

        if (!probe.health.needsRepair()) {
            refresh(false);
            if (probe.health.state == BluetoothHealth.State.HFP_CONNECTED_NO_SCO) {
                showDetails(probe.health.summary + "\n\n" + probe.health.detail);
            }
            Toast.makeText(this, probe.health.summary, Toast.LENGTH_LONG).show();
            return;
        }

        BluetoothRepair.RepairResult result =
                BluetoothRepair.repairCommunicationRoute(this, false);
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> verifyRepair("MANUAL VERIFY"), 1_500L);
    }

    private void forceRepair() {
        BluetoothRepair.RepairResult result =
                BluetoothRepair.repairCommunicationRoute(this, true);
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> verifyRepair("FORCED VERIFY"), 1_500L);
    }

    private void verifyRepair(String logPrefix) {
        BluetoothRepair.Probe verified = BluetoothRepair.probe(this);
        RepairStateStore.saveHealth(this, verified.health);
        Diag.log(this, logPrefix + "\n" + Diag.snapshot(this));
        refresh(false);
    }

    private void toggleMonitor() {
        boolean enabled = RepairStateStore.monitoringEnabled(this);
        if (enabled) {
            RepairStateStore.setMonitoringEnabled(this, false);
            stopService(new Intent(this, MonitorService.class));
            BluetoothRepair.releaseCommunicationRoute(this);
            Toast.makeText(this, "Auto-Schutz beendet.", Toast.LENGTH_SHORT).show();
        } else {
            if (!btPermission()) {
                requestNeededPermissions();
                Toast.makeText(
                                this,
                                "Bluetooth-Berechtigung erteilen und Auto-Schutz erneut starten.",
                                Toast.LENGTH_LONG)
                        .show();
                return;
            }

            RepairStateStore.setMonitoringEnabled(this, true);
            RepairStateStore.setAutoRepairEnabled(this, true);
            try {
                startForegroundService(new Intent(this, MonitorService.class));
                Toast.makeText(
                                this,
                                "Auto-Schutz aktiv. Die dauerhafte Benachrichtigung zeigt den Zustand.",
                                Toast.LENGTH_LONG)
                        .show();
            } catch (RuntimeException e) {
                RepairStateStore.setMonitoringEnabled(this, false);
                showDetails("Auto-Schutz konnte nicht starten: " + e);
            }
        }
        refresh(false);
    }

    private void refresh(boolean log) {
        BluetoothRepair.Probe probe = BluetoothRepair.probe(this);
        BluetoothHealth health = probe.health;
        if (!health.inCommunication
                && !RepairStateStore.monitoringEnabled(this)
                && RepairStateStore.routeOwned(this)) {
            BluetoothRepair.releaseCommunicationRoute(this);
            probe = BluetoothRepair.probe(this);
            health = probe.health;
        }
        if (log) Diag.log(this, "UI SNAPSHOT\n" + Diag.snapshot(this));

        if (details != null) details.setText(Diag.snapshot(this));

        AudioDeviceInfo current = probe.current;
        routeTitle.setText(
                current == null
                        ? "Kein Telefonie-Audiogerät aktiv"
                        : String.valueOf(current.getProductName()));
        routeSub.setText(health.summary);

        switch (health.state) {
            case HEALTHY:
                setStatus("●  " + health.summary, Color.rgb(229, 246, 237), GREEN_DARK);
                break;
            case SUSPECT_ROUTING:
                setStatus(
                        "●  Routingfehler erkannt",
                        Color.rgb(255, 234, 226),
                        Color.rgb(155, 55, 25));
                break;
            case HFP_CONNECTED_NO_SCO:
            case CALL_WITHOUT_SCO:
            case BLUETOOTH_OFF:
            case PERMISSION_REQUIRED:
                setStatus(
                        "●  " + health.summary,
                        Color.rgb(255, 244, 221),
                        Color.rgb(143, 91, 0));
                break;
            case ERROR:
                setStatus("●  Diagnosefehler", Color.rgb(255, 232, 232), Color.rgb(145, 35, 35));
                break;
            case IDLE:
            default:
                setStatus("●  Bereit", Color.rgb(232, 244, 238), GREEN_DARK);
                break;
        }

        boolean monitoring = RepairStateStore.monitoringEnabled(this);
        monitorTitle.setText(
                monitoring ? "Automatische Überwachung stoppen" : "Automatische Überwachung starten");
        monitorSub.setText(
                monitoring
                        ? "Auto-Schutz aktiv · Prüfung alle 30 s, während Anrufen alle 5 s"
                        : "Im Hintergrund prüfen und bestätigte HFP/SCO-Routingfehler reparieren");

        long lastCheck = RepairStateStore.lastCheckAt(this);
        String lastSummary = RepairStateStore.lastSummary(this);
        lastCheckTitle.setText(
                lastCheck == 0L ? "Letzte Prüfung" : "Letzte Prüfung · " + formatTime(lastCheck));
        lastCheckSub.setText(
                lastCheck == 0L
                        ? "Noch keine Hintergrundprüfung"
                        : lastSummary
                                + " · Reparaturen: "
                                + RepairStateStore.repairCount(this));
    }

    private void toggleTechnicalDetails() {
        boolean show = details.getVisibility() != View.VISIBLE;
        details.setVisibility(show ? View.VISIBLE : View.GONE);
        technicalToggle.setText(
                show ? "Technische Details ausblenden" : "Technische Details anzeigen");
    }

    private void showHistory() {
        long check = RepairStateStore.lastCheckAt(this);
        long repair = RepairStateStore.lastRepairAt(this);
        String message =
                RepairStateStore.lastSummary(this)
                        + "\n\nLetzte Prüfung: "
                        + (check == 0L ? "–" : formatTime(check))
                        + "\nLetzte Reparatur: "
                        + (repair == 0L ? "–" : formatTime(repair))
                        + "\nReparaturversuche: "
                        + RepairStateStore.repairCount(this);

        new AlertDialog.Builder(this)
                .setTitle("Auto-Schutz Verlauf")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private LinearLayout action(
            String symbol,
            String title,
            String subtitle,
            boolean primary,
            View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackground(round(primary ? GREEN : Color.WHITE, 18));
        row.setElevation(dp(primary ? 3 : 1));
        row.setOnClickListener(click);
        row.setClickable(true);

        TextView ico = label(symbol, 22, Typeface.BOLD, primary ? Color.WHITE : GREEN_DARK);
        ico.setGravity(Gravity.CENTER);
        ico.setBackground(
                round(
                        primary
                                ? Color.argb(35, 255, 255, 255)
                                : Color.rgb(235, 247, 241),
                        99));
        row.addView(ico, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView t = label(title, 15, Typeface.BOLD, primary ? Color.WHITE : INK);
        TextView sub =
                label(
                        subtitle,
                        12,
                        Typeface.NORMAL,
                        primary ? Color.rgb(222, 244, 235) : MUTED);
        copy.addView(t);
        copy.addView(sub);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
        cp.leftMargin = dp(13);
        row.addView(copy, cp);

        TextView chevron = label("›", 26, Typeface.NORMAL, primary ? Color.WHITE : MUTED);
        row.addView(chevron);
        add(row, 10, -1);
        return row;
    }

    private LinearLayout heroCard() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(20), dp(20), dp(20), dp(20));

        GradientDrawable g =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[] {GREEN_DARK, GREEN});
        g.setCornerRadius(dp(24));
        h.setBackground(g);
        h.setElevation(dp(3));

        h.addView(label("BLUETOOTH TELEFONIE", 11, Typeface.BOLD, Color.rgb(202, 239, 221)));

        TextView title =
                label(
                        "Deine Verbindung.\nOhne Unterbrechungen.",
                        28,
                        Typeface.BOLD,
                        Color.WHITE);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(8);
        h.addView(title, tp);

        TextView sub =
                label(
                        "Optimiert für "
                                + getString(R.string.device_profile_name)
                                + ": HFP/SCO überwachen, Routingfehler erkennen und den Telefoniepfad gezielt neu auswählen.",
                        14,
                        Typeface.NORMAL,
                        Color.rgb(224, 245, 235));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(8);
        h.addView(sub, sp);

        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.addView(chip("HFP"));
        chips.addView(chip("SCO"));
        chips.addView(chip("AUTO"));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(16);
        h.addView(chips, cp);
        return h;
    }

    private TextView chip(String text) {
        TextView v = label(text, 10, Typeface.BOLD, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(6), dp(10), dp(6));
        v.setBackground(round(Color.argb(32, 255, 255, 255), 99));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.rightMargin = dp(8);
        v.setLayoutParams(p);
        return v;
    }

    private LinearLayout sectionHeader(String title, String sub) {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.addView(label(title, 18, Typeface.BOLD, INK));
        x.addView(label(sub, 12, Typeface.NORMAL, MUTED));
        return x;
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(16), dp(15), dp(16), dp(15));
        v.setBackground(round(Color.WHITE, 18));
        v.setElevation(dp(1));
        return v;
    }

    private View bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(8), dp(8), dp(10));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(8));

        nav.addView(
                navItem("⌂", "Start", true, v -> refresh(false)),
                new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(
                navItem("⌕", "Diagnose", false, v -> manualDiagnosis()),
                new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(
                navItem("↻", "Reparatur", false, v -> forceRepair()),
                new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(
                navItem("≡", "Verlauf", false, v -> showHistory()),
                new LinearLayout.LayoutParams(0, dp(58), 1));
        return nav;
    }

    private View navItem(
            String icon, String name, boolean active, View.OnClickListener click) {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setGravity(Gravity.CENTER);
        x.setClickable(true);
        x.setOnClickListener(click);
        TextView i = label(icon, 21, Typeface.BOLD, active ? GREEN_DARK : MUTED);
        TextView n =
                label(
                        name,
                        11,
                        active ? Typeface.BOLD : Typeface.NORMAL,
                        active ? GREEN_DARK : MUTED);
        x.addView(i);
        x.addView(n);
        return x;
    }

    private TextView label(String text, float size, int style, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans", style));
        return v;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (color == Color.WHITE) g.setStroke(dp(1), BORDER);
        return g;
    }

    private void add(View v, int top, int width) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, -2);
        p.topMargin = dp(top);
        content.addView(v, p);
    }

    @SuppressWarnings("deprecation")
    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "–";
        }
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY)
                .format(new Date(millis));
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    private boolean btPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void setStatus(String text, int bg, int fg) {
        statusPill.setText(text);
        statusPill.setTextColor(fg);
        statusPill.setBackground(round(bg, 99));
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31 && !btPermission()) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 10);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        refresh(false);
    }

    private void showDetails(String text) {
        details.setVisibility(View.VISIBLE);
        details.setText(text);
        technicalToggle.setText("Technische Details ausblenden");
    }

    private void shareLog() {
        File f = new File(getFilesDir(), "a52s-bt-repair.log");
        if (!f.exists()) {
            Toast.makeText(this, "Noch kein Diagnoseprotokoll vorhanden.", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        try {
            String text = new String(java.nio.file.Files.readAllBytes(f.toPath()));
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "A52s Bluetooth Repair Log");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Diagnoseprotokoll teilen"));
        } catch (Exception e) {
            showDetails("Protokoll konnte nicht geteilt werden: " + e);
        }
    }
}
