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
    private enum Page {
        HOME,
        HISTORY,
        TOOLS
    }

    private LinearLayout content;
    private LinearLayout navigation;
    private Page currentPage = Page.HOME;

    private TextView statusPill;
    private TextView routeTitle;
    private TextView routeSub;
    private TextView signatureSub;
    private TextView metricAutoValue;
    private TextView metricHfpValue;
    private TextView metricScoValue;
    private TextView monitorTitle;
    private TextView monitorSub;
    private TextView lastCheckTitle;
    private TextView lastCheckSub;
    private LinearLayout recoveryCard;

    private HealthHistoryChart historyChart;
    private TextView historySummary;
    private TextView historyHealthyValue;
    private TextView historyProblemValue;
    private TextView historyRepairValue;

    private TextView details;
    private TextView technicalToggle;

    private TextView updateTitle;
    private TextView updateSub;
    private TextView updateAction;
    private UpdateRelease availableUpdate;
    private boolean pendingUpdateInstall;

    private final int PRIMARY = AppPalette.PRIMARY;
    private final int PRIMARY_DARK = AppPalette.PRIMARY_DARK;
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
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(28));
        scroll.addView(content);

        navigation = bottomNav();
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
                            dp(10),
                            dp(7),
                            dp(10),
                            dp(8) + Math.max(0, systemBars.bottom));
                    return insets;
                });

        setContentView(root);
        root.requestApplyInsets();

        showPage(Page.HOME);
        requestNeededPermissions();

        new Handler(Looper.getMainLooper())
                .postDelayed(() -> checkForUpdates(false), 1_200L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) {
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

    private void showPage(Page page) {
        currentPage = page;
        resetPageReferences();
        content.removeAllViews();

        switch (page) {
            case HISTORY:
                buildHistoryPage();
                break;
            case TOOLS:
                buildToolsPage();
                break;
            case HOME:
            default:
                buildHomePage();
                break;
        }

        updateNavigationSelection();
        refresh(false);
        renderUpdateState();
    }

    private void resetPageReferences() {
        statusPill = null;
        routeTitle = null;
        routeSub = null;
        signatureSub = null;
        metricAutoValue = null;
        metricHfpValue = null;
        metricScoValue = null;
        monitorTitle = null;
        monitorSub = null;
        lastCheckTitle = null;
        lastCheckSub = null;
        recoveryCard = null;
        historyChart = null;
        historySummary = null;
        historyHealthyValue = null;
        historyProblemValue = null;
        historyRepairValue = null;
        details = null;
        technicalToggle = null;
        updateTitle = null;
        updateSub = null;
        updateAction = null;
    }

    private void buildHomePage() {
        buildTopBar("Übersicht", "Telefonie-Audio im Blick, ohne Technik-Chaos");

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable gradient =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[] {AppPalette.HERO_START, AppPalette.HERO_END});
        gradient.setCornerRadius(dp(26));
        hero.setBackground(gradient);
        hero.setElevation(dp(3));

        TextView kicker = label("BLUETOOTH CALL GUARD", 10, Typeface.BOLD, Color.rgb(220, 244, 255));
        hero.addView(kicker);

        TextView heroTitle =
                label("Deine Verbindung.\nAutomatisch geschützt.", 27, Typeface.BOLD, Color.WHITE);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(8);
        hero.addView(heroTitle, hp);

        TextView heroCopy =
                label(
                        "Erkennt festhängende HFP/SCO-Routen und repariert nur dann, wenn Android einen sicheren öffentlichen Reparaturpfad anbietet.",
                        13,
                        Typeface.NORMAL,
                        Color.rgb(232, 246, 252));
        heroCopy.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hcp = new LinearLayout.LayoutParams(-1, -2);
        hcp.topMargin = dp(8);
        hero.addView(heroCopy, hcp);

        statusPill = label("●  Status wird geprüft", 12, Typeface.BOLD, Color.WHITE);
        statusPill.setPadding(dp(11), dp(7), dp(11), dp(7));
        statusPill.setBackground(round(Color.argb(40, 255, 255, 255), 99));
        LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(-2, -2);
        spp.topMargin = dp(14);
        hero.addView(statusPill, spp);

        add(hero, 12, -1);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout autoMetric = metricCard("AUTO", "Aus", AppPalette.PRIMARY_SOFT);
        LinearLayout hfpMetric = metricCard("HFP", "–", AppPalette.TEAL_SOFT);
        LinearLayout scoMetric = metricCard("SCO", "–", AppPalette.CYAN_SOFT);
        metricAutoValue = (TextView) autoMetric.getChildAt(1);
        metricHfpValue = (TextView) hfpMetric.getChildAt(1);
        metricScoValue = (TextView) scoMetric.getChildAt(1);

        metrics.addView(autoMetric, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1);
        mp.leftMargin = dp(8);
        mp.rightMargin = dp(8);
        metrics.addView(hfpMetric, mp);
        metrics.addView(scoMetric, new LinearLayout.LayoutParams(0, -2, 1));
        add(metrics, 10, -1);

        LinearLayout routeCard = card();
        ImageView routeIcon = iconBubble(R.drawable.ic_bluetooth, AppPalette.TEAL, AppPalette.TEAL_SOFT);
        routeCard.addView(routeIcon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout routeCopy = new LinearLayout(this);
        routeCopy.setOrientation(LinearLayout.VERTICAL);
        routeTitle = label("Telefonie-Audio", 16, Typeface.BOLD, INK);
        routeSub = label("Verbindung wird geprüft …", 12, Typeface.NORMAL, MUTED);
        signatureSub = label("Fehlersignatur wird bewertet …", 11, Typeface.BOLD, PRIMARY_DARK);
        routeCopy.addView(routeTitle);
        routeCopy.addView(routeSub);
        LinearLayout.LayoutParams sigParams = new LinearLayout.LayoutParams(-1, -2);
        sigParams.topMargin = dp(4);
        routeCopy.addView(signatureSub, sigParams);

        LinearLayout.LayoutParams rcp = new LinearLayout.LayoutParams(0, -2, 1);
        rcp.leftMargin = dp(13);
        routeCard.addView(routeCopy, rcp);
        add(routeCard, 10, -1);

        add(sectionHeader("Schnellzugriff", "Die zwei Aktionen, die im Alltag wirklich zählen"), 22, -1);

        LinearLayout primaryAction =
                actionCard(
                        R.drawable.ic_search,
                        "Jetzt prüfen",
                        "Zustand analysieren und nur bei bestätigtem Routingfehler automatisch reparieren",
                        true,
                        v -> manualCheckAndRepair());
        add(primaryAction, 10, -1);

        LinearLayout monitorCard =
                actionCard(
                        R.drawable.ic_shield,
                        "Auto-Schutz aktivieren",
                        "Ereignisbasiert und energiesparend im Hintergrund überwachen",
                        false,
                        v -> toggleMonitor());
        monitorTitle = (TextView) ((LinearLayout) monitorCard.getChildAt(1)).getChildAt(0);
        monitorSub = (TextView) ((LinearLayout) monitorCard.getChildAt(1)).getChildAt(1);
        add(monitorCard, 9, -1);

        LinearLayout lastCard = card();
        ImageView historyIcon = iconBubble(R.drawable.ic_history, PRIMARY, AppPalette.PRIMARY_SOFT);
        lastCard.addView(historyIcon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout lastCopy = new LinearLayout(this);
        lastCopy.setOrientation(LinearLayout.VERTICAL);
        lastCheckTitle = label("Letzte Prüfung", 14, Typeface.BOLD, INK);
        lastCheckSub = label("Noch keine Hintergrundprüfung", 12, Typeface.NORMAL, MUTED);
        lastCopy.addView(lastCheckTitle);
        lastCopy.addView(lastCheckSub);
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(0, -2, 1);
        lcp.leftMargin = dp(12);
        lastCard.addView(lastCopy, lcp);
        TextView historyLink = smallButton("Verlauf", v -> showPage(Page.HISTORY), false);
        lastCard.addView(historyLink);
        add(lastCard, 9, -1);

        recoveryCard = blockedRouteCard();
        recoveryCard.setVisibility(View.GONE);
        add(recoveryCard, 12, -1);

        buildCompactUpdateCard();
    }

    private void buildHistoryPage() {
        buildTopBar("Verlauf", "Stabilität, Auffälligkeiten und Reparaturen auf einen Blick");

        add(pageIntroCard(
                R.drawable.ic_history,
                "Stabilität statt Log-Datei",
                "Die Historie fasst wichtige Zustandswechsel zusammen. Wiederholte identische Hintergrundchecks werden bewusst verdichtet."),
                12,
                -1);

        LinearLayout stats = new LinearLayout(this);
        LinearLayout healthy = metricCard("STABIL", "0", AppPalette.SUCCESS_SOFT);
        LinearLayout problems = metricCard("AUFFÄLLIG", "0", AppPalette.WARNING_SOFT);
        LinearLayout repairs = metricCard("REPARATUREN", "0", AppPalette.PRIMARY_SOFT);
        historyHealthyValue = (TextView) healthy.getChildAt(1);
        historyProblemValue = (TextView) problems.getChildAt(1);
        historyRepairValue = (TextView) repairs.getChildAt(1);

        stats.addView(healthy, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1);
        sp.leftMargin = dp(8);
        sp.rightMargin = dp(8);
        stats.addView(problems, sp);
        stats.addView(repairs, new LinearLayout.LayoutParams(0, -2, 1));
        add(stats, 12, -1);

        add(sectionHeader("Letzte 24 Stunden", "Telefoniepfad und erkannte Fehlerzustände"), 22, -1);

        LinearLayout chartCard = card();
        chartCard.setOrientation(LinearLayout.VERTICAL);
        historySummary = label("Noch keine Verlaufsdaten", 14, Typeface.BOLD, INK);
        chartCard.addView(historySummary);
        historyChart = new HealthHistoryChart(this);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, dp(180));
        chartParams.topMargin = dp(8);
        chartCard.addView(historyChart, chartParams);

        TextView legend =
                label(
                        "● stabil    ● auffällig    ● Fehler    ● Leerlauf",
                        11,
                        Typeface.NORMAL,
                        MUTED);
        LinearLayout.LayoutParams legendParams = new LinearLayout.LayoutParams(-1, -2);
        legendParams.topMargin = dp(3);
        chartCard.addView(legend, legendParams);
        add(chartCard, 10, -1);

        LinearLayout last = card();
        ImageView infoIcon = iconBubble(R.drawable.ic_history, AppPalette.CYAN, AppPalette.CYAN_SOFT);
        last.addView(infoIcon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout lastCopy = new LinearLayout(this);
        lastCopy.setOrientation(LinearLayout.VERTICAL);
        lastCheckTitle = label("Letzte Prüfung", 14, Typeface.BOLD, INK);
        lastCheckSub = label("Noch keine Hintergrundprüfung", 12, Typeface.NORMAL, MUTED);
        lastCopy.addView(lastCheckTitle);
        lastCopy.addView(lastCheckSub);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = dp(12);
        last.addView(lastCopy, lp);
        add(last, 10, -1);

        add(sectionHeader("Historie verwalten", "Daten lokal auf diesem Gerät"), 22, -1);
        add(
                actionCard(
                        R.drawable.ic_settings,
                        "Verlauf zurücksetzen",
                        "Gespeicherte Verlaufspunkte löschen; Reparaturzähler bleibt erhalten",
                        false,
                        v -> confirmClearHistory()),
                10,
                -1);
        add(
                actionCard(
                        R.drawable.ic_update,
                        "Diagnoseprotokoll teilen",
                        "Technische Details als Text exportieren",
                        false,
                        v -> shareLog()),
                9,
                -1);
    }

    private void buildToolsPage() {
        buildTopBar("Werkzeuge", "Diagnose, Reparatur, Updates und Systemeinstellungen");

        add(pageIntroCard(
                R.drawable.ic_tools,
                "Alles Technische an einem Ort",
                "Die Navigation wechselt jetzt zwischen echten Bereichen. Aktionen liegen dort, wo man sie erwartet."),
                12,
                -1);

        add(sectionHeader("Diagnose & Reparatur", "Manuelle Werkzeuge für Sonderfälle"), 22, -1);
        add(
                actionCard(
                        R.drawable.ic_search,
                        "Nur Diagnose",
                        "Zustand prüfen und protokollieren, ohne das Routing zu verändern",
                        false,
                        v -> manualDiagnosis()),
                10,
                -1);
        add(
                actionCard(
                        R.drawable.ic_repair,
                        "SCO/HFP neu auswählen",
                        "Bluetooth-Kommunikationsgerät bewusst neu anfordern",
                        false,
                        v -> forceRepair()),
                9,
                -1);

        add(sectionHeader("System", "Android- und Samsung-Einstellungen"), 22, -1);
        add(
                actionCard(
                        R.drawable.ic_bluetooth,
                        "Bluetooth-Einstellungen",
                        "Verbindungen, Geräte und Telefonieprofile verwalten",
                        false,
                        v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))),
                10,
                -1);
        add(
                actionCard(
                        R.drawable.ic_settings,
                        "Bluetooth Agent",
                        "Samsung-Komponente für den tieferen HFP/SCO-Fehler öffnen",
                        false,
                        v -> openBluetoothAgentSettings()),
                9,
                -1);

        add(sectionHeader("App & Updates", "Signierte Builds direkt aus dem Projekt"), 22, -1);
        buildFullUpdateCard();

        add(sectionHeader("Technische Details", "Für Fehlersuche und Bugreports"), 22, -1);
        details = label("", 11, Typeface.NORMAL, MUTED);
        details.setTypeface(Typeface.MONOSPACE);
        details.setPadding(dp(15), dp(14), dp(15), dp(14));
        details.setBackground(round(AppPalette.SURFACE, 18));
        details.setVisibility(View.GONE);
        add(details, 8, -1);

        technicalToggle = smallButton("Technische Details anzeigen", v -> toggleTechnicalDetails(), false);
        technicalToggle.setGravity(Gravity.CENTER);
        add(technicalToggle, 8, -1);

        add(
                actionCard(
                        R.drawable.ic_update,
                        "Diagnoseprotokoll teilen",
                        "Zustände, Fehlersignaturen und Reparaturversuche exportieren",
                        false,
                        v -> shareLog()),
                14,
                -1);
    }

    private void buildTopBar(String title, String subtitle) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(3), dp(2), dp(7));

        TextView brand = label("B", 18, Typeface.BOLD, Color.WHITE);
        brand.setGravity(Gravity.CENTER);
        GradientDrawable logo =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[] {AppPalette.PRIMARY, AppPalette.TEAL});
        logo.setCornerRadius(dp(15));
        brand.setBackground(logo);
        header.addView(brand, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title, 20, Typeface.BOLD, INK));
        copy.addView(label(subtitle, 11, Typeface.NORMAL, MUTED));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
        cp.leftMargin = dp(12);
        header.addView(copy, cp);

        LinearLayout profile = new LinearLayout(this);
        profile.setOrientation(LinearLayout.VERTICAL);
        profile.setGravity(Gravity.RIGHT);
        TextView version = label("v" + appVersion(), 11, Typeface.BOLD, PRIMARY_DARK);
        version.setGravity(Gravity.RIGHT);
        TextView device = label(getString(R.string.device_profile_name), 9, Typeface.NORMAL, MUTED);
        device.setGravity(Gravity.RIGHT);
        profile.addView(version);
        profile.addView(device);
        header.addView(profile, new LinearLayout.LayoutParams(-2, -2));

        content.addView(header);
    }

    private LinearLayout pageIntroCard(int iconRes, String title, String copyText) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconBubble(iconRes, PRIMARY, AppPalette.PRIMARY_SOFT), new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView titleView = label(title, 17, Typeface.BOLD, INK);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.leftMargin = dp(12);
        top.addView(titleView, tp);
        card.addView(top);

        TextView copy = label(copyText, 12, Typeface.NORMAL, MUTED);
        copy.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(10);
        card.addView(copy, cp);
        return card;
    }

    private LinearLayout blockedRouteCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(AppPalette.WARNING_SOFT, 20));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconBubble(R.drawable.ic_repair, AppPalette.WARNING, Color.WHITE), new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = label("Systempfad blockiert", 15, Typeface.BOLD, AppPalette.WARNING);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.leftMargin = dp(11);
        top.addView(title, tp);
        card.addView(top);

        TextView copy =
                label(
                        "HFP ist verbunden, aber Android bietet kein SCO/HFP-Kommunikationsgerät mehr an. Das entspricht dem tieferen Samsung/Vendor-Fehlerbild. Die App kann dann nicht sicher per öffentlicher API reparieren.",
                        12,
                        Typeface.NORMAL,
                        INK);
        copy.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(9);
        card.addView(copy, cp);

        TextView button = smallButton("Recovery-Tools öffnen", v -> showPage(Page.TOOLS), true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(11);
        card.addView(button, bp);
        return card;
    }

    private void buildCompactUpdateCard() {
        updateTitle = label("App ist aktuell · v" + appVersion(), 13, Typeface.BOLD, INK);
        updateSub = label("Automatische Update-Prüfung aktiv", 11, Typeface.NORMAL, MUTED);
        updateAction = smallButton("Prüfen", v -> onUpdateAction(), false);

        LinearLayout card = card();
        card.addView(iconBubble(R.drawable.ic_update, PRIMARY, AppPalette.PRIMARY_SOFT), new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(updateTitle);
        copy.addView(updateSub);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
        cp.leftMargin = dp(11);
        card.addView(copy, cp);
        card.addView(updateAction);
        add(card, 12, -1);
    }

    private void buildFullUpdateCard() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconBubble(R.drawable.ic_update, PRIMARY, AppPalette.PRIMARY_SOFT), new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        updateTitle = label("Update-Status wird geprüft", 15, Typeface.BOLD, INK);
        updateSub =
                label(
                        "GitHub-Releases werden automatisch geprüft und Downloads vor Installation per SHA-256 verifiziert.",
                        12,
                        Typeface.NORMAL,
                        MUTED);
        copy.addView(updateTitle);
        copy.addView(updateSub);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
        cp.leftMargin = dp(12);
        top.addView(copy, cp);
        card.addView(top);

        updateAction = smallButton("Nach Updates suchen", v -> onUpdateAction(), true);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = dp(12);
        card.addView(updateAction, ap);
        add(card, 10, -1);
    }

    private LinearLayout actionCard(
            int iconRes,
            String title,
            String subtitle,
            boolean primary,
            View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(14), dp(15), dp(14));
        row.setClickable(true);
        row.setOnClickListener(click);
        row.setElevation(dp(primary ? 2 : 1));

        if (primary) {
            GradientDrawable gradient =
                    new GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            new int[] {AppPalette.PRIMARY, AppPalette.PRIMARY_DARK});
            gradient.setCornerRadius(dp(20));
            row.setBackground(gradient);
        } else {
            row.setBackground(round(AppPalette.SURFACE, 20));
        }

        ImageView icon =
                iconBubble(
                        iconRes,
                        primary ? Color.WHITE : PRIMARY,
                        primary ? Color.argb(35, 255, 255, 255) : AppPalette.PRIMARY_SOFT);
        row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = label(title, 15, Typeface.BOLD, primary ? Color.WHITE : INK);
        TextView subView =
                label(
                        subtitle,
                        12,
                        Typeface.NORMAL,
                        primary ? Color.rgb(232, 232, 255) : MUTED);
        subView.setLineSpacing(dp(1), 1f);
        copy.addView(titleView);
        copy.addView(subView);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
        cp.leftMargin = dp(12);
        row.addView(copy, cp);

        TextView chevron = label("›", 25, Typeface.NORMAL, primary ? Color.WHITE : MUTED);
        row.addView(chevron);
        return row;
    }

    private LinearLayout metricCard(String title, String value, int tint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(11), dp(12), dp(11));
        box.setBackground(round(AppPalette.SURFACE, 17));
        TextView label = label(title, 9, Typeface.BOLD, MUTED);
        TextView current = label(value, 13, Typeface.BOLD, INK);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.topMargin = dp(3);
        box.addView(label);
        box.addView(current, vp);
        View stripe = new View(this);
        stripe.setBackground(round(tint, 99));
        LinearLayout.LayoutParams stripeParams = new LinearLayout.LayoutParams(dp(28), dp(3));
        stripeParams.topMargin = dp(7);
        box.addView(stripe, stripeParams);
        return box;
    }

    private LinearLayout sectionHeader(String title, String sub) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(label(title, 17, Typeface.BOLD, INK));
        TextView subtitle = label(sub, 11, Typeface.NORMAL, MUTED);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(2);
        section.addView(subtitle, sp);
        return section;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(round(AppPalette.SURFACE, 20));
        card.setElevation(dp(1));
        return card;
    }

    private ImageView iconBubble(int iconRes, int tint, int background) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(tint);
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setBackground(round(background, 15));
        return icon;
    }

    private TextView smallButton(String text, View.OnClickListener click, boolean filled) {
        TextView button =
                label(
                        text,
                        12,
                        Typeface.BOLD,
                        filled ? Color.WHITE : PRIMARY_DARK);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(round(filled ? PRIMARY : AppPalette.PRIMARY_SOFT, 14));
        button.setClickable(true);
        button.setOnClickListener(click);
        return button;
    }

    private LinearLayout bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setTag("bottom-navigation");
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(7), dp(10), dp(8));
        nav.setBackgroundColor(AppPalette.SURFACE);
        nav.setElevation(dp(10));

        nav.addView(
                navDestination(Page.HOME, R.drawable.ic_home, "Übersicht", "nav-home"),
                new LinearLayout.LayoutParams(0, dp(62), 1));
        nav.addView(
                navDestination(Page.HISTORY, R.drawable.ic_history, "Verlauf", "nav-history"),
                new LinearLayout.LayoutParams(0, dp(62), 1));
        nav.addView(
                navDestination(Page.TOOLS, R.drawable.ic_tools, "Werkzeuge", "nav-tools"),
                new LinearLayout.LayoutParams(0, dp(62), 1));
        return nav;
    }

    private View navDestination(Page page, int iconRes, String title, String tag) {
        LinearLayout item = new LinearLayout(this);
        item.setTag(tag);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(8), dp(6), dp(8), dp(6));
        item.setClickable(true);
        item.setOnClickListener(v -> showPage(page));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(MUTED);
        item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView label = label(title, 10, Typeface.NORMAL, MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(3);
        item.addView(label, lp);
        return item;
    }

    private void updateNavigationSelection() {
        if (navigation == null) return;
        for (int i = 0; i < navigation.getChildCount(); i++) {
            View child = navigation.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout item = (LinearLayout) child;
            boolean active =
                    (currentPage == Page.HOME && "nav-home".equals(item.getTag()))
                            || (currentPage == Page.HISTORY && "nav-history".equals(item.getTag()))
                            || (currentPage == Page.TOOLS && "nav-tools".equals(item.getTag()));

            item.setBackground(active ? round(AppPalette.NAV_ACTIVE, 18) : null);
            ImageView icon = (ImageView) item.getChildAt(0);
            TextView label = (TextView) item.getChildAt(1);
            icon.setColorFilter(active ? PRIMARY_DARK : MUTED);
            label.setTextColor(active ? PRIMARY_DARK : MUTED);
            label.setTypeface(
                    Typeface.create("sans", active ? Typeface.BOLD : Typeface.NORMAL));
        }
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
                new AlertDialog.Builder(this)
                        .setTitle("Systempfad blockiert")
                        .setMessage(probe.health.detail)
                        .setPositiveButton("Werkzeuge öffnen", (d, w) -> showPage(Page.TOOLS))
                        .setNegativeButton("Schließen", null)
                        .show();
            } else {
                Toast.makeText(this, probe.health.summary, Toast.LENGTH_LONG).show();
            }
            return;
        }

        BluetoothRepair.RepairResult result =
                BluetoothRepair.repairCommunicationRoute(this, false);
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        new Handler(Looper.getMainLooper())
                .postDelayed(
                        () -> verifyRepair("MANUAL VERIFY"),
                        RepairVerificationPolicy.FIRST_VERIFY_MS);
    }

    private void forceRepair() {
        BluetoothRepair.RepairResult result =
                BluetoothRepair.repairCommunicationRoute(this, true);
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        new Handler(Looper.getMainLooper())
                .postDelayed(
                        () -> verifyRepair("FORCED VERIFY"),
                        RepairVerificationPolicy.FIRST_VERIFY_MS);
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
                                "Auto-Schutz aktiv. Die Benachrichtigung zeigt den aktuellen Zustand.",
                                Toast.LENGTH_LONG)
                        .show();
            } catch (RuntimeException e) {
                RepairStateStore.setMonitoringEnabled(this, false);
                Toast.makeText(this, "Auto-Schutz konnte nicht gestartet werden.", Toast.LENGTH_LONG)
                        .show();
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

        if (details != null && details.getVisibility() == View.VISIBLE) {
            details.setText(Diag.snapshot(this));
        }

        if (routeTitle != null) {
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
                                    ? AppPalette.SUCCESS
                                    : MUTED));
        }

        if (statusPill != null) {
            switch (health.state) {
                case HEALTHY:
                    setHeroStatus("●  " + health.summary, AppPalette.SUCCESS_SOFT, Color.WHITE);
                    break;
                case SUSPECT_ROUTING:
                    setHeroStatus("●  Routingfehler erkannt", AppPalette.WARNING, Color.WHITE);
                    break;
                case HFP_CONNECTED_NO_SCO:
                case CALL_WITHOUT_SCO:
                case BLUETOOTH_OFF:
                case PERMISSION_REQUIRED:
                    setHeroStatus("●  " + health.summary, AppPalette.WARNING, Color.WHITE);
                    break;
                case ERROR:
                    setHeroStatus("●  Diagnosefehler", AppPalette.ERROR, Color.WHITE);
                    break;
                case IDLE:
                default:
                    setHeroStatus("●  Bereit", Color.argb(40, 255, 255, 255), Color.WHITE);
                    break;
            }
        }

        boolean monitoring = RepairStateStore.monitoringEnabled(this);
        if (metricAutoValue != null) {
            metricAutoValue.setText(monitoring ? "Aktiv" : "Aus");
            metricAutoValue.setTextColor(monitoring ? AppPalette.SUCCESS : MUTED);
        }
        if (metricHfpValue != null) {
            metricHfpValue.setText(health.hfpProfileConnected ? "Verbunden" : "Nicht aktiv");
            metricHfpValue.setTextColor(health.hfpProfileConnected ? AppPalette.TEAL : MUTED);
        }
        if (metricScoValue != null) {
            metricScoValue.setText(
                    health.scoSelected
                            ? "Aktiv"
                            : (health.scoAvailable ? "Verfügbar" : "Fehlt"));
            metricScoValue.setTextColor(
                    health.scoSelected
                            ? AppPalette.CYAN
                            : (health.inCommunication && !health.scoAvailable
                                    ? AppPalette.WARNING
                                    : MUTED));
        }

        if (monitorTitle != null) {
            monitorTitle.setText(monitoring ? "Auto-Schutz stoppen" : "Auto-Schutz aktivieren");
            monitorSub.setText(
                    monitoring
                            ? "Aktiv · ereignisbasiert · Leerlauf-Check ca. stündlich"
                            : "Energiesparend im Hintergrund auf HFP/SCO-Fehler reagieren");
        }

        long lastCheck = RepairStateStore.lastCheckAt(this);
        String lastSummary = RepairStateStore.lastSummary(this);
        if (lastCheckTitle != null) {
            lastCheckTitle.setText(
                    lastCheck == 0L
                            ? "Letzte Prüfung"
                            : "Letzte Prüfung · " + formatTime(lastCheck));
            lastCheckSub.setText(
                    lastCheck == 0L
                            ? "Noch keine Hintergrundprüfung"
                            : lastSummary);
        }

        List<HealthHistoryStore.Entry> entries = HealthHistoryStore.read(this);
        if (historyChart != null) historyChart.setEntries(entries);
        if (historySummary != null
                || historyHealthyValue != null
                || historyProblemValue != null
                || historyRepairValue != null) {
            long since = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
            int healthyCalls = HealthHistoryStore.healthyCallCount(entries, since);
            int problems = HealthHistoryStore.problemCount(entries, since);
            if (historySummary != null) {
                historySummary.setText(
                        entries.isEmpty()
                                ? "Noch keine Verlaufsdaten"
                                : healthyCalls
                                        + " stabile Telefonie-Prüfungen · "
                                        + problems
                                        + " Auffälligkeiten");
            }
            if (historyHealthyValue != null) historyHealthyValue.setText(String.valueOf(healthyCalls));
            if (historyProblemValue != null) historyProblemValue.setText(String.valueOf(problems));
            if (historyRepairValue != null) {
                historyRepairValue.setText(String.valueOf(RepairStateStore.repairCount(this)));
            }
        }

        if (recoveryCard != null) {
            boolean blocked =
                    probe.signature.kind == FailureSignature.Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE
                            || probe.signature.kind == FailureSignature.Kind.HFP_CONNECTED_NO_SCO;
            recoveryCard.setVisibility(blocked ? View.VISIBLE : View.GONE);
        }
    }

    private void renderUpdateState() {
        UpdateRelease cached = UpdateStateStore.cachedRelease(this);
        if (cached != null
                && UpdateRelease.compareVersions(cached.version, appVersion()) > 0) {
            availableUpdate = cached;
        } else {
            availableUpdate = null;
        }

        if (updateTitle == null || updateSub == null || updateAction == null) return;

        updateSub.setTextColor(MUTED);
        updateAction.setEnabled(true);

        if (availableUpdate != null) {
            updateTitle.setText("Update v" + availableUpdate.version + " verfügbar");
            updateTitle.setTextColor(PRIMARY_DARK);
            updateSub.setText(
                    getString(R.string.device_profile_name)
                            + " · signierte Release-APK · SHA-256 wird vor Installation geprüft");
            updateAction.setText("Installieren");
            return;
        }

        long checked = UpdateStateStore.lastCheckAt(this);
        String error = UpdateStateStore.lastError(this);
        updateTitle.setTextColor(INK);

        if (checked == 0L) {
            updateTitle.setText("Automatische Update-Prüfung aktiv");
            updateSub.setText("Beim Start und über Auto-Schutz wird regelmäßig nachgesehen.");
        } else if (error != null && !error.isEmpty()) {
            updateTitle.setText("Letzte Update-Prüfung nicht möglich");
            updateSub.setText("Installiert: v" + appVersion() + " · manuelle Prüfung möglich");
        } else {
            updateTitle.setText("App ist aktuell · v" + appVersion());
            updateSub.setText("Letzte Prüfung " + formatTime(checked));
        }

        updateAction.setText(currentPage == Page.TOOLS ? "Nach Updates suchen" : "Prüfen");
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
                    if (updateAction != null) updateAction.setEnabled(true);
                    if (updateSub != null) {
                        updateSub.setText(message);
                        updateSub.setTextColor(error ? AppPalette.ERROR : MUTED);
                    }
                    if (error) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void toggleTechnicalDetails() {
        if (details == null || technicalToggle == null) return;
        boolean show = details.getVisibility() != View.VISIBLE;
        details.setVisibility(show ? View.VISIBLE : View.GONE);
        technicalToggle.setText(
                show ? "Technische Details ausblenden" : "Technische Details anzeigen");
        if (show) details.setText(Diag.snapshot(this));
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Verlauf zurücksetzen?")
                .setMessage("Die gespeicherten Verlaufspunkte werden von diesem Gerät gelöscht.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton(
                        "Löschen",
                        (dialog, which) -> {
                            HealthHistoryStore.clear(this);
                            refresh(false);
                        })
                .show();
    }

    private void openBluetoothAgentSettings() {
        Intent detailsIntent =
                new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:com.sec.android.app.bluetoothagent"));
        try {
            startActivity(detailsIntent);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        }
    }

    private void shareLog() {
        File file = new File(getFilesDir(), "a52s-bt-repair.log");
        if (!file.exists()) {
            Toast.makeText(this, "Noch kein Diagnoseprotokoll vorhanden.", Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        try {
            String text = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " Diagnose");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(intent, "Diagnoseprotokoll teilen"));
        } catch (Exception e) {
            Toast.makeText(this, "Protokoll konnte nicht geteilt werden.", Toast.LENGTH_LONG)
                    .show();
        }
    }

    private TextView label(String text, float size, int style, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (color == AppPalette.SURFACE) {
            drawable.setStroke(dp(1), BORDER);
        }
        return drawable;
    }

    private void add(View view, int top, int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, -2);
        params.topMargin = dp(top);
        content.addView(view, params);
    }

    private void setHeroStatus(String text, int background, int foreground) {
        statusPill.setText(text);
        statusPill.setTextColor(foreground);
        statusPill.setBackground(round(background, 99));
    }

    @SuppressWarnings("deprecation")
    private String appVersion() {
        try {
            String version =
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null ? "–" : version;
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
        return new SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
                .format(new Date(millis));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean btPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
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
}
