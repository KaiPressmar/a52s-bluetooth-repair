package de.kaipressmar.a52srepair;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
        TOOLS,
        SETTINGS
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
    private TextView diagBluetoothValue;
    private TextView diagHfpValue;
    private TextView diagScoValue;
    private TextView diagSignatureValue;
    private Switch monitorSwitch;
    private TextView monitorSubtitle;
    private TextView homeLastCheck;
    private LinearLayout recoveryCard;
    private LinearLayout homeUpdateBanner;
    private TextView homeUpdateBannerText;
    private boolean updatingMonitorSwitch;
    private Switch autoRepairSwitch;
    private TextView autoRepairSubtitle;
    private boolean updatingAutoRepairSwitch;

    private HealthHistoryChart historyChart;
    private TextView historySummary;
    private TextView historyHealthyValue;
    private TextView historyProblemValue;
    private TextView historyRepairValue;
    private LinearLayout recentEventsContainer;

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
        content.setPadding(dp(18), dp(12), dp(18), dp(30));
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
                            dp(8),
                            dp(6),
                            dp(8),
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
            case SETTINGS:
                buildSettingsPage();
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
        diagBluetoothValue = null;
        diagHfpValue = null;
        diagScoValue = null;
        diagSignatureValue = null;
        monitorSwitch = null;
        monitorSubtitle = null;
        homeLastCheck = null;
        recoveryCard = null;
        homeUpdateBanner = null;
        homeUpdateBannerText = null;
        autoRepairSwitch = null;
        autoRepairSubtitle = null;

        historyChart = null;
        historySummary = null;
        historyHealthyValue = null;
        historyProblemValue = null;
        historyRepairValue = null;
        recentEventsContainer = null;

        details = null;
        technicalToggle = null;

        updateTitle = null;
        updateSub = null;
        updateAction = null;
    }

    private void buildHomePage() {
        buildTopBar("Übersicht", "Dein Bluetooth-Telefoniepfad auf einen Blick");

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(19));

        GradientDrawable gradient =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[] {AppPalette.HERO_START, AppPalette.HERO_END});
        gradient.setCornerRadius(dp(26));
        hero.setBackground(gradient);
        hero.setElevation(dp(3));

        hero.addView(label("CALL GUARD", 10, Typeface.BOLD, Color.rgb(221, 241, 255)));

        TextView heroTitle =
                label(
                        "Telefonie-Audio.\nAutomatisch geschützt.",
                        27,
                        Typeface.BOLD,
                        Color.WHITE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(7);
        hero.addView(heroTitle, titleParams);

        TextView heroCopy =
                label(
                        "Die App erkennt typische HFP/SCO-Ausfälle und greift nur ein, wenn Android einen sicheren Reparaturpfad anbietet.",
                        13,
                        Typeface.NORMAL,
                        Color.rgb(233, 246, 252));
        heroCopy.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
        copyParams.topMargin = dp(8);
        hero.addView(heroCopy, copyParams);

        statusPill = label("●  Status wird geprüft", 12, Typeface.BOLD, Color.WHITE);
        statusPill.setPadding(dp(11), dp(7), dp(11), dp(7));
        statusPill.setBackground(round(Color.argb(42, 255, 255, 255), 99));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.topMargin = dp(14);
        hero.addView(statusPill, statusParams);
        add(hero, 12, -1);

        homeUpdateBanner = new LinearLayout(this);
        homeUpdateBanner.setOrientation(LinearLayout.HORIZONTAL);
        homeUpdateBanner.setGravity(Gravity.CENTER_VERTICAL);
        homeUpdateBanner.setPadding(dp(13), dp(11), dp(13), dp(11));
        homeUpdateBanner.setBackground(round(AppPalette.PRIMARY_SOFT, 17));
        homeUpdateBanner.setClickable(true);
        homeUpdateBanner.setOnClickListener(v -> showPage(Page.SETTINGS));
        homeUpdateBanner.addView(
                iconBubble(R.drawable.ic_update, PRIMARY_DARK, Color.WHITE),
                new LinearLayout.LayoutParams(dp(38), dp(38)));
        homeUpdateBannerText =
                label("Update verfügbar", 12, Typeface.BOLD, PRIMARY_DARK);
        LinearLayout.LayoutParams updateTextParams = new LinearLayout.LayoutParams(0, -2, 1);
        updateTextParams.leftMargin = dp(10);
        homeUpdateBanner.addView(homeUpdateBannerText, updateTextParams);
        homeUpdateBanner.addView(label("›", 23, Typeface.NORMAL, PRIMARY_DARK));
        homeUpdateBanner.setVisibility(View.GONE);
        add(homeUpdateBanner, 9, -1);

        LinearLayout metrics = new LinearLayout(this);
        LinearLayout hfpMetric = metricCard("HFP-PROFIL", "–", AppPalette.TEAL_SOFT);
        LinearLayout scoMetric = metricCard("TELEFONIE-ROUTE", "–", AppPalette.CYAN_SOFT);
        metricHfpValue = (TextView) hfpMetric.getChildAt(1);
        metricScoValue = (TextView) scoMetric.getChildAt(1);

        metrics.addView(hfpMetric, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams secondMetric = new LinearLayout.LayoutParams(0, -2, 1);
        secondMetric.leftMargin = dp(8);
        metrics.addView(scoMetric, secondMetric);
        add(metrics, 10, -1);

        add(sectionHeader("Verbindung", "Was Android gerade für Telefonie verwendet"), 21, -1);

        LinearLayout routeCard = card();
        routeCard.addView(
                iconBubble(R.drawable.ic_bluetooth, AppPalette.TEAL, AppPalette.TEAL_SOFT),
                new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout routeCopy = new LinearLayout(this);
        routeCopy.setOrientation(LinearLayout.VERTICAL);
        routeTitle = label("Telefonie-Audio", 16, Typeface.BOLD, INK);
        routeSub = label("Verbindung wird geprüft …", 12, Typeface.NORMAL, MUTED);
        signatureSub =
                label("Fehlersignatur wird bewertet …", 11, Typeface.BOLD, PRIMARY_DARK);
        routeCopy.addView(routeTitle);
        routeCopy.addView(routeSub);
        LinearLayout.LayoutParams signatureParams = new LinearLayout.LayoutParams(-1, -2);
        signatureParams.topMargin = dp(4);
        routeCopy.addView(signatureSub, signatureParams);

        LinearLayout.LayoutParams routeCopyParams = new LinearLayout.LayoutParams(0, -2, 1);
        routeCopyParams.leftMargin = dp(13);
        routeCard.addView(routeCopy, routeCopyParams);
        add(routeCard, 9, -1);

        add(
                actionCard(
                        R.drawable.ic_search,
                        "Jetzt prüfen",
                        "Status analysieren und einen bestätigten Routingfehler automatisch reparieren",
                        true,
                        v -> manualCheckAndRepair()),
                10,
                -1);

        add(sectionHeader("Auto-Schutz", "Einmal einschalten, danach läuft die Überwachung sparsam im Hintergrund"), 21, -1);

        LinearLayout protectionCard = card();
        protectionCard.setOrientation(LinearLayout.VERTICAL);

        LinearLayout protectionTop = new LinearLayout(this);
        protectionTop.setGravity(Gravity.CENTER_VERTICAL);
        protectionTop.addView(
                iconBubble(R.drawable.ic_shield, PRIMARY, AppPalette.PRIMARY_SOFT),
                new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout protectionCopy = new LinearLayout(this);
        protectionCopy.setOrientation(LinearLayout.VERTICAL);
        protectionCopy.addView(label("Automatische Überwachung", 15, Typeface.BOLD, INK));
        monitorSubtitle =
                label(
                        "Ereignisbasiert, mit seltenem Sicherheitscheck im Leerlauf",
                        12,
                        Typeface.NORMAL,
                        MUTED);
        protectionCopy.addView(monitorSubtitle);
        LinearLayout.LayoutParams protectionCopyParams =
                new LinearLayout.LayoutParams(0, -2, 1);
        protectionCopyParams.leftMargin = dp(12);
        protectionTop.addView(protectionCopy, protectionCopyParams);

        monitorSwitch = new Switch(this);
        monitorSwitch.setShowText(false);
        tintSwitch(monitorSwitch);
        monitorSwitch.setOnCheckedChangeListener(
                (buttonView, checked) -> {
                    if (!updatingMonitorSwitch) setMonitoringEnabled(checked);
                });
        protectionTop.addView(monitorSwitch);
        protectionCard.addView(protectionTop);

        homeLastCheck = label("Noch keine automatische Prüfung", 11, Typeface.NORMAL, MUTED);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(-1, -2);
        checkParams.topMargin = dp(12);
        protectionCard.addView(homeLastCheck, checkParams);
        add(protectionCard, 9, -1);

        recoveryCard = blockedRouteCard();
        recoveryCard.setVisibility(View.GONE);
        add(recoveryCard, 11, -1);
    }

    private void buildHistoryPage() {
        buildTopBar("Verlauf", "Stabilität und Auffälligkeiten ohne technische Rohdaten");

        LinearLayout stats = new LinearLayout(this);
        LinearLayout healthy = metricCard("STABIL", "0", AppPalette.SUCCESS_SOFT);
        LinearLayout problems = metricCard("AUFFÄLLIG", "0", AppPalette.WARNING_SOFT);
        LinearLayout repairs = metricCard("REPARIERT", "0", AppPalette.PRIMARY_SOFT);
        historyHealthyValue = (TextView) healthy.getChildAt(1);
        historyProblemValue = (TextView) problems.getChildAt(1);
        historyRepairValue = (TextView) repairs.getChildAt(1);

        stats.addView(healthy, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams centerStat = new LinearLayout.LayoutParams(0, -2, 1);
        centerStat.leftMargin = dp(8);
        centerStat.rightMargin = dp(8);
        stats.addView(problems, centerStat);
        stats.addView(repairs, new LinearLayout.LayoutParams(0, -2, 1));
        add(stats, 12, -1);

        add(sectionHeader("Letzte 24 Stunden", "Verdichtete Zustände des Telefoniepfads"), 21, -1);

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
        add(chartCard, 9, -1);

        add(sectionHeader("Letzte Ereignisse", "Die wichtigsten Zustandswechsel in verständlicher Form"), 21, -1);
        recentEventsContainer = card();
        recentEventsContainer.setOrientation(LinearLayout.VERTICAL);
        add(recentEventsContainer, 9, -1);

        add(sectionHeader("Daten", "Der Verlauf bleibt ausschließlich lokal auf dem Gerät"), 21, -1);
        add(
                actionCard(
                        R.drawable.ic_clear,
                        "Verlauf zurücksetzen",
                        "Gespeicherte Verlaufspunkte löschen; Reparaturzähler bleibt erhalten",
                        false,
                        v -> confirmClearHistory()),
                9,
                -1);
    }

    private void buildToolsPage() {
        buildTopBar("Diagnose", "Live-Zustand, Reparatur und Recovery an einem Ort");

        add(sectionHeader("Schnelltest", "Die vier Signale, die für das Fehlerbild entscheidend sind"), 12, -1);

        LinearLayout diagnostic = card();
        diagnostic.setOrientation(LinearLayout.VERTICAL);

        LinearLayout diagHeader = new LinearLayout(this);
        diagHeader.setGravity(Gravity.CENTER_VERTICAL);
        diagHeader.addView(
                iconBubble(R.drawable.ic_search, PRIMARY, AppPalette.PRIMARY_SOFT),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout diagCopy = new LinearLayout(this);
        diagCopy.setOrientation(LinearLayout.VERTICAL);
        diagCopy.addView(label("Live-Diagnose", 15, Typeface.BOLD, INK));
        diagCopy.addView(
                label(
                        "Wird beim Öffnen und beim Zurückkehren in die App aktualisiert",
                        11,
                        Typeface.NORMAL,
                        MUTED));
        LinearLayout.LayoutParams diagCopyParams = new LinearLayout.LayoutParams(0, -2, 1);
        diagCopyParams.leftMargin = dp(11);
        diagHeader.addView(diagCopy, diagCopyParams);
        diagnostic.addView(diagHeader);

        diagnostic.addView(infoDivider());
        diagBluetoothValue = diagnosticRow(diagnostic, "Bluetooth", "wird geprüft");
        diagnostic.addView(infoDivider());
        diagHfpValue = diagnosticRow(diagnostic, "HFP-Profil", "wird geprüft");
        diagnostic.addView(infoDivider());
        diagScoValue = diagnosticRow(diagnostic, "SCO/Telefonie", "wird geprüft");
        diagnostic.addView(infoDivider());
        diagSignatureValue = diagnosticRow(diagnostic, "Fehlersignatur", "wird bewertet");

        TextView refreshDiagnosis =
                smallButton("Diagnose aktualisieren", v -> manualDiagnosis(), false);
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(-1, -2);
        refreshParams.topMargin = dp(12);
        diagnostic.addView(refreshDiagnosis, refreshParams);
        add(diagnostic, 9, -1);

        add(sectionHeader("Reparatur", "Nur gezielt eingreifen, wenn die Diagnose es nahelegt"), 21, -1);
        add(
                actionCard(
                        R.drawable.ic_repair,
                        "SCO/HFP neu auswählen",
                        "Bluetooth-Kommunikationsgerät bewusst neu anfordern und anschließend verifizieren",
                        false,
                        v -> forceRepair()),
                9,
                -1);

        add(sectionHeader("Recovery", "Für den tieferen Zustand „HFP verbunden, SCO fehlt“"), 21, -1);
        add(
                actionCard(
                        R.drawable.ic_settings,
                        "Samsung Bluetooth Agent",
                        "App-Info der Samsung-Bluetooth-Komponente öffnen und dort zuerst den Cache prüfen",
                        false,
                        v -> openBluetoothAgentSettings()),
                9,
                -1);
        add(
                actionCard(
                        R.drawable.ic_bluetooth,
                        "Bluetooth-Einstellungen",
                        "Geräte, Profile und Verbindungen direkt in Android verwalten",
                        false,
                        v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))),
                8,
                -1);
        add(
                actionCard(
                        R.drawable.ic_developer,
                        "Entwickleroptionen",
                        "Nur für bekannte manuelle Workarounds wie A2DP-Offload/SAP öffnen",
                        false,
                        v -> openDeveloperOptions()),
                8,
                -1);

        add(
                noteCard(
                        "Automatische Grenze",
                        "Wenn Android kein SCO/HFP-Kommunikationsgerät mehr anbietet, startet die App keine aggressiven Systemdienste neu. Sie erkennt diesen Zustand und führt dich stattdessen zu den sicheren Recovery-Schritten.",
                        AppPalette.WARNING_SOFT,
                        AppPalette.WARNING),
                11,
                -1);

        add(sectionHeader("Support-Daten", "Technische Informationen nur hier, nicht in den normalen Einstellungen"), 21, -1);

        details = label("", 11, Typeface.NORMAL, MUTED);
        details.setTypeface(Typeface.MONOSPACE);
        details.setPadding(dp(15), dp(14), dp(15), dp(14));
        details.setBackground(round(AppPalette.SURFACE, 18));
        details.setVisibility(View.GONE);
        add(details, 9, -1);

        technicalToggle =
                smallButton(
                        "Technische Details anzeigen",
                        v -> toggleTechnicalDetails(),
                        false);
        technicalToggle.setGravity(Gravity.CENTER);
        add(technicalToggle, 8, -1);

        add(
                actionCard(
                        R.drawable.ic_update,
                        "Diagnoseprotokoll teilen",
                        "Zustände, Fehlersignaturen und Reparaturversuche als Text exportieren",
                        false,
                        v -> shareLog()),
                10,
                -1);
    }

    private void buildSettingsPage() {
        buildTopBar("Einstellungen", "Schutzverhalten, Updates und App-Informationen");

        add(sectionHeader("Schutzverhalten", "Festlegen, wie der Auto-Schutz reagieren darf"), 12, -1);

        LinearLayout repairSetting = card();
        repairSetting.setOrientation(LinearLayout.VERTICAL);

        LinearLayout repairTop = new LinearLayout(this);
        repairTop.setGravity(Gravity.CENTER_VERTICAL);
        repairTop.addView(
                iconBubble(R.drawable.ic_shield, PRIMARY, AppPalette.PRIMARY_SOFT),
                new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout repairCopy = new LinearLayout(this);
        repairCopy.setOrientation(LinearLayout.VERTICAL);
        repairCopy.addView(label("Automatisch reparieren", 15, Typeface.BOLD, INK));
        autoRepairSubtitle =
                label(
                        "Bestätigte, öffentlich reparierbare Routingfehler automatisch beheben",
                        11,
                        Typeface.NORMAL,
                        MUTED);
        repairCopy.addView(autoRepairSubtitle);
        LinearLayout.LayoutParams repairCopyParams = new LinearLayout.LayoutParams(0, -2, 1);
        repairCopyParams.leftMargin = dp(12);
        repairTop.addView(repairCopy, repairCopyParams);

        autoRepairSwitch = new Switch(this);
        autoRepairSwitch.setShowText(false);
        tintSwitch(autoRepairSwitch);
        autoRepairSwitch.setOnCheckedChangeListener(
                (buttonView, checked) -> {
                    if (updatingAutoRepairSwitch) return;
                    RepairStateStore.setAutoRepairEnabled(this, checked);
                    refresh(false);
                    Toast.makeText(
                                    this,
                                    checked
                                            ? "Automatische Reparatur aktiviert."
                                            : "Auto-Schutz erkennt weiterhin Fehler, repariert aber nicht automatisch.",
                                    Toast.LENGTH_SHORT)
                            .show();
                });
        repairTop.addView(autoRepairSwitch);
        repairSetting.addView(repairTop);

        TextView repairHint =
                label(
                        "Diese Einstellung verändert nicht die Überwachung selbst. Auto-Schutz kann aktiv bleiben und bei ausgeschalteter Auto-Reparatur nur erkennen, protokollieren und warnen.",
                        11,
                        Typeface.NORMAL,
                        MUTED);
        repairHint.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(10);
        repairSetting.addView(repairHint, hintParams);
        add(repairSetting, 9, -1);

        add(sectionHeader("App & Updates", "Signierte Builds direkt aus dem Projekt"), 21, -1);
        buildFullUpdateCard();

        add(sectionHeader("System", "Benachrichtigungen und Android-Zugriff"), 21, -1);
        add(
                actionCard(
                        R.drawable.ic_notifications,
                        "Benachrichtigungen",
                        "Status- und Update-Benachrichtigungen in Android konfigurieren",
                        false,
                        v -> openNotificationSettings()),
                9,
                -1);

        add(sectionHeader("App-Info", "Installierter Build und Geräteprofil"), 21, -1);
        add(appInfoCard(), 9, -1);
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
        TextView subtitleView = label(subtitle, 11, Typeface.NORMAL, MUTED);
        subtitleView.setMaxLines(2);
        copy.addView(subtitleView);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.leftMargin = dp(12);
        header.addView(copy, copyParams);

        content.addView(header);
    }

    private LinearLayout appInfoCard() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);

        boolean profileMatch =
                DeviceProfile.matchesTarget(
                        getString(R.string.device_profile_key), Build.MODEL, Build.DEVICE);

        card.addView(infoRow("Version", "v" + appVersion()));
        card.addView(infoDivider());
        card.addView(infoRow("Build", getString(R.string.device_profile_name)));
        card.addView(infoDivider());
        card.addView(
                infoRow(
                        "Gerät",
                        profileMatch
                                ? "Passendes Geräteprofil"
                                : "Gerät weicht vom Build-Profil ab"));
        card.addView(infoDivider());
        card.addView(infoRow("Android-Ziel", "Android 16 · API 36"));
        card.addView(infoDivider());
        card.addView(infoRow("Update-Prüfung", "automatisch, höchstens alle 12 Stunden"));
        return card;
    }

    private TextView diagnosticRow(
            LinearLayout parent,
            String labelText,
            String initialValue) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = label(labelText, 12, Typeface.NORMAL, MUTED);
        TextView right = label(initialValue, 12, Typeface.BOLD, INK);
        right.setGravity(Gravity.RIGHT);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row);
        return right;
    }

    private View infoDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.topMargin = dp(10);
        params.bottomMargin = dp(10);
        divider.setLayoutParams(params);
        return divider;
    }

    private LinearLayout infoRow(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = label(key, 12, Typeface.NORMAL, MUTED);
        TextView right = label(value, 12, Typeface.BOLD, INK);
        right.setGravity(Gravity.RIGHT);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private LinearLayout blockedRouteCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(AppPalette.WARNING_SOFT, 20));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(
                iconBubble(R.drawable.ic_repair, AppPalette.WARNING, Color.WHITE),
                new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = label("Systempfad blockiert", 15, Typeface.BOLD, AppPalette.WARNING);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(11);
        top.addView(title, titleParams);
        card.addView(top);

        TextView copy =
                label(
                        "HFP ist verbunden, Android bietet aber kein SCO/HFP-Gerät mehr an. Dieser tiefere Vendor-Zustand braucht einen manuellen Recovery-Schritt.",
                        12,
                        Typeface.NORMAL,
                        INK);
        copy.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
        copyParams.topMargin = dp(9);
        card.addView(copy, copyParams);

        TextView button = smallButton("Recovery öffnen", v -> showPage(Page.TOOLS), true);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(11);
        card.addView(button, buttonParams);
        return card;
    }

    private LinearLayout noteCard(
            String title,
            String copy,
            int background,
            int titleColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(round(background, 18));
        card.addView(label(title, 13, Typeface.BOLD, titleColor));
        TextView body = label(copy, 12, Typeface.NORMAL, INK);
        body.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(5);
        card.addView(body, bodyParams);
        return card;
    }

    private void buildFullUpdateCard() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(
                iconBubble(R.drawable.ic_update, PRIMARY, AppPalette.PRIMARY_SOFT),
                new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        updateTitle = label("Update-Status wird geprüft", 15, Typeface.BOLD, INK);
        updateSub =
                label(
                        "Downloads werden vor der Installation per SHA-256 und Paketmetadaten verifiziert.",
                        12,
                        Typeface.NORMAL,
                        MUTED);
        copy.addView(updateTitle);
        copy.addView(updateSub);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.leftMargin = dp(12);
        top.addView(copy, copyParams);
        card.addView(top);

        updateAction = smallButton("Nach Updates suchen", v -> onUpdateAction(), true);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(12);
        card.addView(updateAction, actionParams);
        add(card, 9, -1);
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
                        primary
                                ? Color.argb(35, 255, 255, 255)
                                : AppPalette.PRIMARY_SOFT);
        row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = label(title, 15, Typeface.BOLD, primary ? Color.WHITE : INK);
        TextView subtitleView =
                label(
                        subtitle,
                        12,
                        Typeface.NORMAL,
                        primary ? Color.rgb(234, 233, 255) : MUTED);
        subtitleView.setLineSpacing(dp(1), 1f);
        copy.addView(titleView);
        copy.addView(subtitleView);

        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.leftMargin = dp(12);
        row.addView(copy, copyParams);
        row.addView(label("›", 25, Typeface.NORMAL, primary ? Color.WHITE : MUTED));
        return row;
    }

    private LinearLayout metricCard(String title, String value, int tint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(11), dp(12), dp(11));
        box.setBackground(round(AppPalette.SURFACE, 17));

        TextView label = label(title, 9, Typeface.BOLD, MUTED);
        TextView current = label(value, 13, Typeface.BOLD, INK);
        box.addView(label);
        LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(-1, -2);
        currentParams.topMargin = dp(3);
        box.addView(current, currentParams);

        View stripe = new View(this);
        stripe.setBackground(round(tint, 99));
        LinearLayout.LayoutParams stripeParams = new LinearLayout.LayoutParams(dp(28), dp(3));
        stripeParams.topMargin = dp(7);
        box.addView(stripe, stripeParams);
        return box;
    }

    private LinearLayout sectionHeader(String title, String subtitle) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(label(title, 17, Typeface.BOLD, INK));
        TextView subtitleView = label(subtitle, 11, Typeface.NORMAL, MUTED);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(2);
        section.addView(subtitleView, subtitleParams);
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
                label(text, 12, Typeface.BOLD, filled ? Color.WHITE : PRIMARY_DARK);
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
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setBackgroundColor(AppPalette.SURFACE);
        nav.setElevation(dp(10));

        nav.addView(
                navDestination(Page.HOME, R.drawable.ic_home, "Übersicht", "nav-home"),
                new LinearLayout.LayoutParams(0, dp(62), 1));
        nav.addView(
                navDestination(Page.HISTORY, R.drawable.ic_history, "Verlauf", "nav-history"),
                new LinearLayout.LayoutParams(0, dp(62), 1));
        nav.addView(
                navDestination(Page.TOOLS, R.drawable.ic_tools, "Diagnose", "nav-tools"),
                new LinearLayout.LayoutParams(0, dp(62), 1));
        nav.addView(
                navDestination(Page.SETTINGS, R.drawable.ic_settings, "Einstellungen", "nav-settings"),
                new LinearLayout.LayoutParams(0, dp(62), 1));
        return nav;
    }

    private View navDestination(Page page, int iconRes, String title, String tag) {
        LinearLayout item = new LinearLayout(this);
        item.setTag(tag);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(5), dp(6), dp(5), dp(6));
        item.setClickable(true);
        item.setContentDescription(title);
        item.setOnClickListener(v -> showPage(page));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(MUTED);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(icon, new LinearLayout.LayoutParams(dp(21), dp(21)));

        TextView label = label(title, 9, Typeface.NORMAL, MUTED);
        label.setMaxLines(1);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.topMargin = dp(3);
        item.addView(label, labelParams);
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
                            || (currentPage == Page.TOOLS && "nav-tools".equals(item.getTag()))
                            || (currentPage == Page.SETTINGS && "nav-settings".equals(item.getTag()));

            item.setBackground(active ? round(AppPalette.NAV_ACTIVE, 18) : null);
            ImageView icon = (ImageView) item.getChildAt(0);
            TextView label = (TextView) item.getChildAt(1);
            icon.setColorFilter(active ? PRIMARY_DARK : MUTED);
            label.setTextColor(active ? PRIMARY_DARK : MUTED);
            label.setTypeface(
                    Typeface.create("sans", active ? Typeface.BOLD : Typeface.NORMAL));
        }
    }

    private void tintSwitch(Switch toggle) {
        int[][] states =
                new int[][] {
                    new int[] {android.R.attr.state_checked},
                    new int[] {-android.R.attr.state_checked}
                };
        toggle.setThumbTintList(
                new ColorStateList(
                        states,
                        new int[] {Color.WHITE, Color.rgb(245, 246, 250)}));
        toggle.setTrackTintList(
                new ColorStateList(
                        states,
                        new int[] {PRIMARY, AppPalette.NEUTRAL}));
    }

    private void manualDiagnosis() {
        BluetoothRepair.Probe probe = BluetoothRepair.probe(this);
        RepairStateStore.saveHealth(this, probe.health);
        HealthHistoryStore.record(this, probe.health, "manual");
        Diag.log(this, "MANUAL DIAGNOSIS\n" + Diag.snapshot(this));
        refresh(false);

        String signature =
                probe.signature.label
                        + (probe.signature.confidence == FailureSignature.Confidence.NONE
                                ? ""
                                : "\nÜbereinstimmung: "
                                        + confidenceLabel(probe.signature.confidence));

        new AlertDialog.Builder(this)
                .setTitle(probe.health.summary)
                .setMessage(probe.health.detail + "\n\n" + signature)
                .setPositiveButton("OK", null)
                .show();
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
                        .setPositiveButton("Recovery öffnen", (d, w) -> showPage(Page.TOOLS))
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

    private void setMonitoringEnabled(boolean enabled) {
        boolean currentlyEnabled = RepairStateStore.monitoringEnabled(this);
        if (enabled == currentlyEnabled) {
            refresh(false);
            return;
        }

        if (enabled) {
            if (!btPermission()) {
                requestNeededPermissions();
                Toast.makeText(
                                this,
                                "Bluetooth-Berechtigung erteilen und Auto-Schutz danach erneut aktivieren.",
                                Toast.LENGTH_LONG)
                        .show();
                refresh(false);
                return;
            }

            RepairStateStore.setMonitoringEnabled(this, true);
            try {
                startForegroundService(new Intent(this, MonitorService.class));
                Toast.makeText(this, "Auto-Schutz ist aktiv.", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException e) {
                RepairStateStore.setMonitoringEnabled(this, false);
                Toast.makeText(
                                this,
                                "Auto-Schutz konnte nicht gestartet werden.",
                                Toast.LENGTH_LONG)
                        .show();
            }
        } else {
            RepairStateStore.setMonitoringEnabled(this, false);
            stopService(new Intent(this, MonitorService.class));
            BluetoothRepair.releaseCommunicationRoute(this);
            Toast.makeText(this, "Auto-Schutz beendet.", Toast.LENGTH_SHORT).show();
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
                                    : " · "
                                            + confidenceLabel(
                                                    probe.signature.confidence)));
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
                    setHeroStatus("●  " + health.summary, AppPalette.SUCCESS, Color.WHITE);
                    break;
                case SUSPECT_ROUTING:
                    setHeroStatus(
                            "●  Routingfehler erkannt",
                            AppPalette.WARNING,
                            Color.WHITE);
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
                    setHeroStatus(
                            "●  Bereit",
                            Color.argb(42, 255, 255, 255),
                            Color.WHITE);
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

        if (autoRepairSwitch != null) {
            boolean autoRepair = RepairStateStore.autoRepairEnabled(this);
            updatingAutoRepairSwitch = true;
            autoRepairSwitch.setChecked(autoRepair);
            updatingAutoRepairSwitch = false;
            autoRepairSubtitle.setText(
                    autoRepair
                            ? "Bestätigte, öffentlich reparierbare Routingfehler automatisch beheben"
                            : "Nur erkennen, protokollieren und warnen");
        }

        if (monitorSwitch != null) {
            updatingMonitorSwitch = true;
            monitorSwitch.setChecked(monitoring);
            updatingMonitorSwitch = false;

            boolean autoRepair = RepairStateStore.autoRepairEnabled(this);
            monitorSubtitle.setText(
                    monitoring
                            ? (autoRepair
                                    ? "Aktiv · erkennt und repariert bestätigte Routingfehler"
                                    : "Aktiv · erkennt und protokolliert; Auto-Reparatur ist aus")
                            : "Aus · einschalten für automatische Erkennung im Hintergrund");
        }

        long lastCheck = RepairStateStore.lastCheckAt(this);
        String lastSummary = RepairStateStore.lastSummary(this);
        if (homeLastCheck != null) {
            homeLastCheck.setText(
                    lastCheck == 0L
                            ? "Noch keine automatische Prüfung"
                            : "Zuletzt "
                                    + formatTime(lastCheck)
                                    + " · "
                                    + lastSummary);
        }

        if (diagBluetoothValue != null) {
            diagBluetoothValue.setText(
                    health.state == BluetoothHealth.State.BLUETOOTH_OFF
                            ? "Ausgeschaltet"
                            : (health.state == BluetoothHealth.State.PERMISSION_REQUIRED
                                    ? "Berechtigung fehlt"
                                    : "Aktiv"));
            diagBluetoothValue.setTextColor(
                    health.state == BluetoothHealth.State.BLUETOOTH_OFF
                                    || health.state == BluetoothHealth.State.PERMISSION_REQUIRED
                            ? AppPalette.WARNING
                            : AppPalette.SUCCESS);

            diagHfpValue.setText(health.hfpProfileConnected ? "Verbunden" : "Nicht verbunden");
            diagHfpValue.setTextColor(
                    health.hfpProfileConnected ? AppPalette.SUCCESS : MUTED);

            diagScoValue.setText(
                    health.scoSelected
                            ? "Aktiv"
                            : (health.scoAvailable ? "Verfügbar" : "Nicht verfügbar"));
            diagScoValue.setTextColor(
                    health.scoSelected
                            ? AppPalette.SUCCESS
                            : (health.inCommunication && !health.scoAvailable
                                    ? AppPalette.WARNING
                                    : MUTED));

            diagSignatureValue.setText(
                    probe.signature.label
                            + (probe.signature.confidence == FailureSignature.Confidence.NONE
                                    ? ""
                                    : " · " + confidenceLabel(probe.signature.confidence)));
            diagSignatureValue.setTextColor(
                    probe.signature.matchesKnownSamsungFailure()
                            ? AppPalette.WARNING
                            : (probe.signature.kind == FailureSignature.Kind.HEALTHY_CALL
                                    ? AppPalette.SUCCESS
                                    : MUTED));
        }

        List<HealthHistoryStore.Entry> entries = HealthHistoryStore.read(this);
        if (historyChart != null) historyChart.setEntries(entries);

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
        if (historyHealthyValue != null) {
            historyHealthyValue.setText(String.valueOf(healthyCalls));
        }
        if (historyProblemValue != null) {
            historyProblemValue.setText(String.valueOf(problems));
        }
        if (historyRepairValue != null) {
            historyRepairValue.setText(
                    String.valueOf(HealthHistoryStore.repairCount(entries, since)));
        }
        if (recentEventsContainer != null) {
            renderRecentEvents(entries);
        }

        if (recoveryCard != null) {
            boolean blocked =
                    probe.signature.kind
                                    == FailureSignature.Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE
                            || probe.signature.kind
                                    == FailureSignature.Kind.HFP_CONNECTED_NO_SCO;
            recoveryCard.setVisibility(blocked ? View.VISIBLE : View.GONE);
        }
    }

    private void renderRecentEvents(List<HealthHistoryStore.Entry> entries) {
        recentEventsContainer.removeAllViews();
        List<HealthHistoryStore.Entry> recent = HealthHistoryStore.recent(entries, 6);

        if (recent.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.addView(
                    iconBubble(R.drawable.ic_history, AppPalette.NEUTRAL, AppPalette.SURFACE_TINT),
                    new LinearLayout.LayoutParams(dp(42), dp(42)));

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(label("Noch keine Ereignisse", 13, Typeface.BOLD, INK));
            copy.addView(
                    label(
                            "Nach den ersten Prüfungen erscheint hier eine verständliche Kurzchronik.",
                            11,
                            Typeface.NORMAL,
                            MUTED));
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
            copyParams.leftMargin = dp(11);
            empty.addView(copy, copyParams);
            recentEventsContainer.addView(empty);
            return;
        }

        Collections.reverse(recent);
        for (int i = 0; i < recent.size(); i++) {
            HealthHistoryStore.Entry entry = recent.get(i);
            recentEventsContainer.addView(eventRow(entry));
            if (i < recent.size() - 1) recentEventsContainer.addView(infoDivider());
        }
    }

    private View eventRow(HealthHistoryStore.Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackground(round(historyColor(entry), 99));
        row.addView(dot, new LinearLayout.LayoutParams(dp(9), dp(9)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(historyTitle(entry), 12, Typeface.BOLD, INK));
        copy.addView(
                label(
                        formatTime(entry.timestamp) + " · " + historySource(entry.source),
                        10,
                        Typeface.NORMAL,
                        MUTED));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.leftMargin = dp(11);
        row.addView(copy, copyParams);
        return row;
    }

    private int historyColor(HealthHistoryStore.Entry entry) {
        switch (entry.state) {
            case HEALTHY:
                return AppPalette.SUCCESS;
            case SUSPECT_ROUTING:
            case HFP_CONNECTED_NO_SCO:
            case CALL_WITHOUT_SCO:
                return AppPalette.WARNING;
            case ERROR:
            case BLUETOOTH_OFF:
            case PERMISSION_REQUIRED:
                return AppPalette.ERROR;
            case IDLE:
            default:
                return AppPalette.NEUTRAL;
        }
    }

    private String historyTitle(HealthHistoryStore.Entry entry) {
        switch (entry.state) {
            case HEALTHY:
                return entry.inCommunication
                        ? "Telefoniepfad stabil"
                        : "Bluetooth-Telefonie bereit";
            case SUSPECT_ROUTING:
                return "Routingabweichung erkannt";
            case HFP_CONNECTED_NO_SCO:
                return "HFP verbunden, SCO fehlt";
            case CALL_WITHOUT_SCO:
                return "Anruf ohne Bluetooth-Telefoniepfad";
            case BLUETOOTH_OFF:
                return "Bluetooth ausgeschaltet";
            case PERMISSION_REQUIRED:
                return "Bluetooth-Berechtigung fehlt";
            case ERROR:
                return "Diagnosefehler";
            case IDLE:
            default:
                return "Bereit";
        }
    }

    private String historySource(String source) {
        if ("manual".equals(source)) return "manuelle Prüfung";
        if ("repair".equals(source)) return "Reparaturprüfung";
        if ("recovery".equals(source)) return "Recovery";
        if (source != null && source.startsWith("audio-mode")) return "Anrufereignis";
        if ("communication-route".equals(source)) return "Routingänderung";
        if ("device-added".equals(source) || "device-removed".equals(source)) {
            return "Geräteänderung";
        }
        return "Auto-Schutz";
    }

    private void renderUpdateState() {
        UpdateRelease cached = UpdateStateStore.cachedRelease(this);
        if (cached != null
                && UpdateRelease.compareVersions(cached.version, appVersion()) > 0) {
            availableUpdate = cached;
        } else {
            availableUpdate = null;
        }

        if (homeUpdateBanner != null) {
            if (availableUpdate != null) {
                homeUpdateBannerText.setText(
                        "Update v"
                                + availableUpdate.version
                                + " verfügbar · in Einstellungen installieren");
                homeUpdateBanner.setVisibility(View.VISIBLE);
            } else {
                homeUpdateBanner.setVisibility(View.GONE);
            }
        }

        if (updateTitle == null || updateSub == null || updateAction == null) return;

        updateSub.setTextColor(MUTED);
        updateAction.setEnabled(true);

        if (availableUpdate != null) {
            updateTitle.setText("Update v" + availableUpdate.version + " verfügbar");
            updateTitle.setTextColor(PRIMARY_DARK);
            updateSub.setText(
                    getString(R.string.device_profile_name)
                            + " · signierte APK · Integrität wird vor Installation geprüft");
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

        updateAction.setText("Nach Updates suchen");
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
        Intent intent =
                new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:com.sec.android.app.bluetoothagent"));
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        }
    }

    private void openDeveloperOptions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName())));
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
        if (color == AppPalette.SURFACE) drawable.setStroke(dp(1), BORDER);
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
