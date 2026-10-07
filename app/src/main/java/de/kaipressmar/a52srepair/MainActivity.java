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
import android.net.Uri;
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
    private TextView signatureSub;
    private TextView metricAutoValue;
    private TextView metricHfpValue;
    private TextView metricScoValue;
    private TextView lastCheckTitle;
    private TextView lastCheckSub;
    private TextView monitorTitle;
    private TextView monitorSub;
    private TextView details;
    private TextView technicalToggle;
    private HealthHistoryChart historyChart;
    private TextView historySummary;
    private LinearLayout recoveryCard;
    private LinearLayout updateCard;
    private TextView updateTitle;
    private TextView updateSub;
    private TextView updateAction;
    private UpdateRelease availableUpdate;
    private boolean pendingUpdateInstall;

    private final int GREEN = AppPalette.PRIMARY;
    private final int GREEN_DARK = AppPalette.PRIMARY_DARK;
    private final int INK = AppPalette.INK;
    private final int MUTED = AppPalette.MUTED;
    private final int BG = AppPalette.BACKGROUND;
    private final int BORDER = AppPalette.BORDER;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        View decor = getWindow().getDecorView();
        WindowInsetsController bars = decor.getWindowInsetsController();
        if (bars != null) {
            bars.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }

        LinearLayout root = new LinearLayout(this);
        root.setTag("app-root");
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(26));
        scroll.addView(content);

        View navigation = bottomNav();
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(navigation);
        root.setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    android.graphics.Insets systemBars =
                            insets.getInsets(
                                    WindowInsets.Type.systemBars()
                                            | WindowInsets.Type.displayCutout());
                    view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
                    navigation.setPadding(
                            dp(8),
                            dp(6),
                            dp(8),
                            dp(8) + Math.max(0, systemBars.bottom));
                    return insets;
                });
        setContentView(root);
        root.requestApplyInsets();

        buildDashboard();
        requestNeededPermissions();
        refresh(false);
        renderUpdateState();
        checkForUpdates(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (details != null) {
            refresh(false);
            renderUpdateState();
        }
        if (pendingUpdateInstall
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                        || getPackageManager().canRequestPackageInstalls())) {
            pendingUpdateInstall = false;
            onUpdateAction();
        }
    }

    private void buildDashboard() {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = label("B", 22, Typeface.BOLD, GREEN_DARK);
        mark.setTextColor(Color.WHITE);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(AppPalette.PRIMARY_DARK, 18));
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
        gear.setBackground(round(AppPalette.SURFACE, 18));
        gear.setElevation(dp(1));
        gear.setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        head.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(head);

        add(heroCard(), 18, -1);

        statusPill = label("●  Status wird geprüft", 13, Typeface.BOLD, GREEN_DARK);
        statusPill.setPadding(dp(12), dp(7), dp(12), dp(7));
        statusPill.setBackground(round(AppPalette.PRIMARY_SOFT, 99));
        add(statusPill, 16, -2);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout autoMetric = metricCard("Auto-Schutz", "Aus");
        LinearLayout hfpMetric = metricCard("HFP", "–");
        LinearLayout scoMetric = metricCard("SCO", "–");
        metricAutoValue = (TextView) autoMetric.getChildAt(1);
        metricHfpValue = (TextView) hfpMetric.getChildAt(1);
        metricScoValue = (TextView) scoMetric.getChildAt(1);
        metrics.addView(autoMetric, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams midMetric = new LinearLayout.LayoutParams(0, -2, 1);
        midMetric.leftMargin = dp(8);
        midMetric.rightMargin = dp(8);
        metrics.addView(hfpMetric, midMetric);
        metrics.addView(scoMetric, new LinearLayout.LayoutParams(0, -2, 1));
        add(metrics, 12, -1);

        LinearLayout route = card();
        TextView icon = label("◉", 24, Typeface.BOLD, GREEN);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(AppPalette.TEAL_SOFT, 99));
        route.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout rt = new LinearLayout(this);
        rt.setOrientation(LinearLayout.VERTICAL);
        routeTitle = label("Telefonie-Audio", 16, Typeface.BOLD, INK);
        routeSub = label("Verbindung wird geprüft …", 13, Typeface.NORMAL, MUTED);
        signatureSub = label("Fehlersignatur wird bewertet …", 11, Typeface.BOLD, GREEN_DARK);
        rt.addView(routeTitle);
        rt.addView(routeSub);
        LinearLayout.LayoutParams signatureParams = new LinearLayout.LayoutParams(-1, -2);
        signatureParams.topMargin = dp(4);
        rt.addView(signatureSub, signatureParams);
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

        add(sectionHeader("Auto-Schutz", "Ereignisbasiert prüfen, mit sparsamer Sicherheitskontrolle"), 24, -1);
        LinearLayout monitor =
                action(
                        "●",
                        "Automatische Überwachung starten",
                        "Bei Anruf- und Audioänderungen sofort prüfen; im Leerlauf nur selten kontrollieren",
                        false,
                        v -> toggleMonitor());
        monitorTitle = (TextView) ((LinearLayout) monitor.getChildAt(1)).getChildAt(0);
        monitorSub = (TextView) ((LinearLayout) monitor.getChildAt(1)).getChildAt(1);

        LinearLayout last = card();
        TextView clock = label("◷", 22, Typeface.BOLD, GREEN_DARK);
        clock.setGravity(Gravity.CENTER);
        clock.setBackground(round(AppPalette.PRIMARY_SOFT, 99));
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
        tip.setBackground(round(AppPalette.SURFACE_TINT, 18));
        TextView bulb = label("i", 16, Typeface.BOLD, GREEN_DARK);
        bulb.setGravity(Gravity.CENTER);
        bulb.setBackground(round(AppPalette.SURFACE, 99));
        tip.addView(bulb, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView tt =
                label(
                        "Auto-Schutz\nDie Überwachung reagiert hauptsächlich auf Anruf- und Audioereignisse. Im Leerlauf erfolgt nur etwa stündlich eine Sicherheitsprüfung. Ein verdächtiger HFP/SCO-Zustand wird nach kurzer Wartezeit bestätigt, bevor repariert wird. Ein absichtlich aktivierter Lautsprecher wird nicht überschrieben.",
                        13,
                        Typeface.NORMAL,
                        INK);
        tt.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams ttp = new LinearLayout.LayoutParams(0, -2, 1);
        ttp.leftMargin = dp(12);
        tip.addView(tt, ttp);
        add(tip, 12, -1);

        add(sectionHeader("Verlauf", "Wie stabil der Telefoniepfad über die letzten Prüfungen war"), 24, -1);
        LinearLayout history = card();
        history.setOrientation(LinearLayout.VERTICAL);
        historySummary = label("Noch keine Verlaufsdaten", 13, Typeface.BOLD, INK);
        history.addView(historySummary);
        historyChart = new HealthHistoryChart(this);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, dp(142));
        chartParams.topMargin = dp(8);
        history.addView(historyChart, chartParams);
        TextView legend =
                label(
                        "● Stabil   ● Auffällig   ● Fehler/Blockade   ● Leerlauf",
                        11,
                        Typeface.NORMAL,
                        MUTED);
        LinearLayout.LayoutParams legendParams = new LinearLayout.LayoutParams(-1, -2);
        legendParams.topMargin = dp(4);
        history.addView(legend, legendParams);
        add(history, 10, -1);

        recoveryCard = card();
        recoveryCard.setOrientation(LinearLayout.VERTICAL);
        recoveryCard.setBackground(round(AppPalette.WARNING_SOFT, 18));
        TextView recoveryTitle =
                label("Systempfad blockiert", 16, Typeface.BOLD, AppPalette.WARNING);
        recoveryCard.addView(recoveryTitle);
        TextView recoveryCopy =
                label(
                        "HFP ist verbunden, Android stellt aber kein SCO/HFP-Gerät bereit. Wenn Medien-Bluetooth gleichzeitig weiter verfügbar ist, entspricht das sehr genau dem bekannten Fehlerbild. Eine normale App kann den privilegierten Samsung-Bluetoothdienst nicht neu starten. Erste Eskalation: Bluetooth-Agent-Cache prüfen. Community-Berichte nennen außerdem A2DP-Offload/SAP in den Entwickleroptionen als möglichen temporären Workaround.",
                        12,
                        Typeface.NORMAL,
                        INK);
        LinearLayout.LayoutParams rcp = new LinearLayout.LayoutParams(-1, -2);
        rcp.topMargin = dp(6);
        recoveryCard.addView(recoveryCopy, rcp);
        LinearLayout recoveryActions = new LinearLayout(this);
        recoveryActions.setGravity(Gravity.CENTER_VERTICAL);
        TextView agent = compactButton("Bluetooth Agent öffnen", v -> openBluetoothAgentSettings());
        TextView bt = compactButton("Bluetooth öffnen", v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        recoveryActions.addView(agent, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(0, -2, 1);
        btp.leftMargin = dp(8);
        recoveryActions.addView(bt, btp);
        LinearLayout.LayoutParams rap = new LinearLayout.LayoutParams(-1, -2);
        rap.topMargin = dp(12);
        recoveryCard.addView(recoveryActions, rap);
        TextView developer =
                compactButton(
                        "Entwickleroptionen öffnen",
                        v -> {
                            try {
                                startActivity(
                                        new Intent(
                                                Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                            } catch (RuntimeException e) {
                                startActivity(new Intent(Settings.ACTION_SETTINGS));
                            }
                        });
        LinearLayout.LayoutParams devParams = new LinearLayout.LayoutParams(-1, -2);
        devParams.topMargin = dp(8);
        recoveryCard.addView(developer, devParams);
        recoveryCard.setVisibility(View.GONE);
        add(recoveryCard, 12, -1);

        add(sectionHeader("App & Updates", "Neue signierte Releases automatisch erkennen und sicher installieren"), 24, -1);
        updateCard = card();
        TextView updateIcon = label("↥", 22, Typeface.BOLD, GREEN_DARK);
        updateIcon.setGravity(Gravity.CENTER);
        updateIcon.setBackground(round(AppPalette.PRIMARY_SOFT, 99));
        updateCard.addView(updateIcon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout updateCopy = new LinearLayout(this);
        updateCopy.setOrientation(LinearLayout.VERTICAL);
        updateTitle = label("Update-Prüfung wird vorbereitet", 15, Typeface.BOLD, INK);
        updateSub =
                label(
                        "GitHub-Releases werden automatisch geprüft; Downloads werden per SHA-256 verifiziert.",
                        12,
                        Typeface.NORMAL,
                        MUTED);
        updateCopy.addView(updateTitle);
        updateCopy.addView(updateSub);
        LinearLayout.LayoutParams updateCopyParams = new LinearLayout.LayoutParams(0, -2, 1);
        updateCopyParams.leftMargin = dp(13);
        updateCard.addView(updateCopy, updateCopyParams);

        updateAction = compactButton("Prüfen", v -> onUpdateAction());
        LinearLayout.LayoutParams updateActionParams = new LinearLayout.LayoutParams(-2, -2);
        updateActionParams.leftMargin = dp(8);
        updateCard.addView(updateAction, updateActionParams);
        add(updateCard, 10, -1);

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
        details.setBackground(round(AppPalette.SURFACE, 16));
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
        HealthHistoryStore.record(this, probe.health, "manual");
        Diag.log(this, "MANUAL DIAGNOSIS\n" + Diag.snapshot(this));
        refresh(false);
        Toast.makeText(this, probe.health.summary, Toast.LENGTH_LONG).show();
    }

    private void manualCheckAndRepair() {
        BluetoothRepair.Probe probe = BluetoothRepair.probe(this);
        RepairStateStore.saveHealth(this, probe.health);
        HealthHistoryStore.record(this, probe.health, "manual");
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
        new Handler(Looper.getMainLooper()).postDelayed(() -> verifyRepair("MANUAL VERIFY"), RepairVerificationPolicy.FIRST_VERIFY_MS);
    }

    private void forceRepair() {
        BluetoothRepair.RepairResult result =
                BluetoothRepair.repairCommunicationRoute(this, true);
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> verifyRepair("FORCED VERIFY"), RepairVerificationPolicy.FIRST_VERIFY_MS);
    }

    private void verifyRepair(String logPrefix) {
        BluetoothRepair.Probe verified = BluetoothRepair.probe(this);
        RepairStateStore.saveHealth(this, verified.health);
        HealthHistoryStore.record(this, verified.health, "repair");
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
        signatureSub.setText(
                probe.signature.label
                        + (probe.signature.confidence == FailureSignature.Confidence.NONE
                                ? ""
                                : " · " + confidenceLabel(probe.signature.confidence)));
        signatureSub.setTextColor(
                probe.signature.matchesKnownSamsungFailure()
                        ? AppPalette.WARNING
                        : (probe.signature.kind == FailureSignature.Kind.HEALTHY_CALL
                                ? AppPalette.PRIMARY_DARK
                                : MUTED));

        switch (health.state) {
            case HEALTHY:
                setStatus("●  " + health.summary, AppPalette.PRIMARY_SOFT, GREEN_DARK);
                break;
            case SUSPECT_ROUTING:
                setStatus(
                        "●  Routingfehler erkannt",
                        AppPalette.WARNING_SOFT,
                        AppPalette.WARNING);
                break;
            case HFP_CONNECTED_NO_SCO:
            case CALL_WITHOUT_SCO:
            case BLUETOOTH_OFF:
            case PERMISSION_REQUIRED:
                setStatus(
                        "●  " + health.summary,
                        AppPalette.WARNING_SOFT,
                        AppPalette.WARNING);
                break;
            case ERROR:
                setStatus("●  Diagnosefehler", AppPalette.ERROR_SOFT, AppPalette.ERROR);
                break;
            case IDLE:
            default:
                setStatus("●  Bereit", AppPalette.SURFACE_TINT, GREEN_DARK);
                break;
        }

        boolean monitoring = RepairStateStore.monitoringEnabled(this);
        if (metricAutoValue != null) {
            metricAutoValue.setText(monitoring ? "Aktiv" : "Aus");
            metricAutoValue.setTextColor(monitoring ? AppPalette.PRIMARY_DARK : MUTED);
        }
        if (metricHfpValue != null) {
            metricHfpValue.setText(health.hfpProfileConnected ? "Verbunden" : "Nicht aktiv");
            metricHfpValue.setTextColor(
                    health.hfpProfileConnected ? AppPalette.PRIMARY_DARK : MUTED);
        }
        if (metricScoValue != null) {
            metricScoValue.setText(
                    health.scoSelected
                            ? "Aktiv"
                            : (health.scoAvailable ? "Verfügbar" : "Fehlt"));
            metricScoValue.setTextColor(
                    health.scoSelected
                            ? AppPalette.PRIMARY_DARK
                            : (health.inCommunication && !health.scoAvailable
                                    ? AppPalette.WARNING
                                    : MUTED));
        }

        monitorTitle.setText(
                monitoring ? "Automatische Überwachung stoppen" : "Automatische Überwachung starten");
        monitorSub.setText(
                monitoring
                        ? "Auto-Schutz aktiv · ereignisbasiert, Leerlauf-Check ca. stündlich"
                        : "Energiesparend im Hintergrund prüfen und bestätigte HFP/SCO-Routingfehler reparieren");

        long lastCheck = RepairStateStore.lastCheckAt(this);
        String lastSummary = RepairStateStore.lastSummary(this);
        lastCheckTitle.setText(
                lastCheck == 0L ? "Letzte Prüfung" : "Letzte Prüfung · " + formatTime(lastCheck));
        lastCheckSub.setText(
                lastCheck == 0L
                        ? "Noch keine Hintergrundprüfung"
                        : lastSummary
                                + " · Reparaturversuche: "
                                + RepairStateStore.repairCount(this));

        List<HealthHistoryStore.Entry> historyEntries = HealthHistoryStore.read(this);
        if (historyChart != null) historyChart.setEntries(historyEntries);
        if (historySummary != null) {
            long since = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
            int problems = HealthHistoryStore.problemCount(historyEntries, since);
            int healthyCalls = HealthHistoryStore.healthyCallCount(historyEntries, since);
            historySummary.setText(
                    historyEntries.isEmpty()
                            ? "Noch keine Verlaufsdaten"
                            : "Letzte 24 h · "
                                    + healthyCalls
                                    + " stabile Telefonie-Prüfungen · "
                                    + problems
                                    + " Auffälligkeiten");
        }
        if (recoveryCard != null) {
            boolean blocked =
                    probe.signature.kind == FailureSignature.Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE
                            || probe.signature.kind == FailureSignature.Kind.HFP_CONNECTED_NO_SCO;
            recoveryCard.setVisibility(blocked ? View.VISIBLE : View.GONE);
        }
    }

    private void renderUpdateState() {
        if (updateTitle == null || updateSub == null || updateAction == null) return;
        updateSub.setTextColor(MUTED);
        updateAction.setEnabled(true);

        UpdateRelease cached = UpdateStateStore.cachedRelease(this);
        if (cached != null
                && UpdateRelease.compareVersions(cached.version, appVersion()) > 0) {
            availableUpdate = cached;
            updateTitle.setText("Update v" + cached.version + " verfügbar");
            updateTitle.setTextColor(AppPalette.PRIMARY_DARK);
            updateSub.setText(
                    getString(R.string.device_profile_name)
                            + " · signierte Release-APK · SHA-256-Prüfung vor Installation");
            updateAction.setText("Installieren");
            updateAction.setTextColor(AppPalette.PRIMARY_DARK);
            return;
        }

        availableUpdate = null;
        long checked = UpdateStateStore.lastCheckAt(this);
        String error = UpdateStateStore.lastError(this);
        if (checked == 0L) {
            updateTitle.setText("Automatische Update-Prüfung aktiv");
            updateSub.setText("Beim Start und im Auto-Schutz wird höchstens alle 12 Stunden nachgesehen.");
        } else if (error != null && !error.isEmpty()) {
            updateTitle.setText("Update-Prüfung zuletzt nicht möglich");
            updateSub.setText("Installierte Version v" + appVersion() + " · manuelle Prüfung möglich");
        } else {
            updateTitle.setText("App ist aktuell · v" + appVersion());
            updateSub.setText("Letzte Prüfung " + formatTime(checked) + " · passend für " + getString(R.string.device_profile_name));
        }
        updateAction.setText("Prüfen");
        updateAction.setTextColor(GREEN_DARK);
    }

    private void checkForUpdates(boolean force) {
        if (updateTitle != null) {
            updateTitle.setText(force ? "Suche nach Updates …" : "Update-Status wird geprüft …");
        }
        UpdateManager.checkForUpdates(
                this,
                force,
                (release, networkChecked, error) -> {
                    renderUpdateState();
                    if (force) {
                        if (release != null
                                && UpdateRelease.compareVersions(release.version, appVersion()) > 0) {
                            Toast.makeText(
                                            this,
                                            "Update v" + release.version + " ist verfügbar.",
                                            Toast.LENGTH_LONG)
                                    .show();
                        } else if (error != null && !error.isEmpty()) {
                            Toast.makeText(
                                            this,
                                            "Update-Prüfung fehlgeschlagen.",
                                            Toast.LENGTH_LONG)
                                    .show();
                        } else {
                            Toast.makeText(
                                            this,
                                            "Du verwendest bereits die aktuelle Version.",
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
    }

    private void onUpdateAction() {
        if (availableUpdate == null) {
            checkForUpdates(true);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            pendingUpdateInstall = true;
        }

        updateAction.setEnabled(false);
        UpdateManager.installUpdate(
                this,
                availableUpdate,
                (message, error) -> {
                    updateAction.setEnabled(true);
                    updateSub.setText(message);
                    updateSub.setTextColor(error ? AppPalette.ERROR : MUTED);
                    if (error) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
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
        List<HealthHistoryStore.Entry> entries = HealthHistoryStore.read(this);
        long since = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        int problems = HealthHistoryStore.problemCount(entries, since);
        int healthyCalls = HealthHistoryStore.healthyCallCount(entries, since);
        String message =
                RepairStateStore.lastSummary(this)
                        + "\n\nLetzte 24 Stunden"
                        + "\nStabile Telefonie-Prüfungen: "
                        + healthyCalls
                        + "\nAuffälligkeiten: "
                        + problems
                        + "\n\nLetzte Prüfung: "
                        + (check == 0L ? "–" : formatTime(check))
                        + "\nLetzte Reparatur: "
                        + (repair == 0L ? "–" : formatTime(repair))
                        + "\nReparaturversuche: "
                        + RepairStateStore.repairCount(this);

        new AlertDialog.Builder(this)
                .setTitle("Auto-Schutz Verlauf")
                .setMessage(message)
                .setNeutralButton(
                        "Verlauf löschen",
                        (dialog, which) -> {
                            HealthHistoryStore.clear(this);
                            refresh(false);
                        })
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
        row.setBackground(round(primary ? GREEN : AppPalette.SURFACE, 18));
        row.setElevation(dp(primary ? 3 : 1));
        row.setOnClickListener(click);
        row.setClickable(true);

        TextView ico = label(symbol, 22, Typeface.BOLD, primary ? Color.WHITE : GREEN_DARK);
        ico.setGravity(Gravity.CENTER);
        ico.setBackground(
                round(
                        primary
                                ? Color.argb(35, 255, 255, 255)
                                : AppPalette.PRIMARY_SOFT,
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
                        primary ? Color.rgb(224, 246, 238) : MUTED);
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
                        new int[] {AppPalette.PRIMARY_DARK, AppPalette.TEAL});
        g.setCornerRadius(dp(24));
        h.setBackground(g);
        h.setElevation(dp(3));

        h.addView(label("BLUETOOTH TELEFONIE", 11, Typeface.BOLD, Color.rgb(204, 241, 232)));

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
                        Color.rgb(227, 247, 242));
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

    private LinearLayout metricCard(String title, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(11), dp(12), dp(11));
        box.setBackground(round(AppPalette.SURFACE, 16));
        TextView label = label(title, 10, Typeface.BOLD, MUTED);
        TextView current = label(value, 13, Typeface.BOLD, INK);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.topMargin = dp(3);
        box.addView(label);
        box.addView(current, vp);
        return box;
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
        v.setBackground(round(AppPalette.SURFACE, 18));
        v.setElevation(dp(1));
        return v;
    }

    private View bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setTag("bottom-navigation");
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setBackgroundColor(AppPalette.SURFACE);
        nav.setElevation(dp(10));

        nav.addView(
                navItem("⌂", "Start", true, v -> refresh(false)),
                new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(
                navItem("⌕", "Diagnose", false, v -> manualDiagnosis()),
                new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(
                navItem("↻", "Reparatur", false, v -> forceRepair()),
                new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(
                navItem("≡", "Verlauf", false, v -> showHistory()),
                new LinearLayout.LayoutParams(0, dp(56), 1));
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

    private TextView compactButton(String text, View.OnClickListener click) {
        TextView button = label(text, 12, Typeface.BOLD, GREEN_DARK);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(round(AppPalette.SURFACE, 12));
        button.setClickable(true);
        button.setOnClickListener(click);
        return button;
    }

    private void openBluetoothAgentSettings() {
        Intent details =
                new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:com.sec.android.app.bluetoothagent"));
        try {
            startActivity(details);
        } catch (RuntimeException e) {
            Toast.makeText(
                            this,
                            "Bluetooth Agent konnte nicht direkt geöffnet werden.",
                            Toast.LENGTH_LONG)
                    .show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        }
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
        if (color == Color.WHITE || color == AppPalette.SURFACE) g.setStroke(dp(1), BORDER);
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

    private String confidenceLabel(FailureSignature.Confidence confidence) {
        switch (confidence) {
            case HIGH:
                return "hohe Übereinstimmung";
            case MEDIUM:
                return "mittlere Übereinstimmung";
            case LOW:
                return "unspezifisch";
            case NONE:
            default:
                return "";
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
