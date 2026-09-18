package de.kaipressmar.a52srepair;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothProfile;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    LinearLayout box; TextView status;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(getColor(R.color.surface)); box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(48,48,48,64); scroll.addView(box); setContentView(scroll);
        TextView title = new TextView(this); title.setText("A52s Bluetooth Repair\nTelefonie-Audio im Blick"); title.setTextSize(26); title.setTextColor(getColor(R.color.text_primary)); title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); box.addView(title); TextView intro=new TextView(this); intro.setText("Diagnose und gezielte Reparatur für Bluetooth-HFP/SCO – ohne unnötige Eingriffe."); intro.setTextSize(15); intro.setTextColor(getColor(R.color.text_secondary)); intro.setPadding(0,12,0,24); box.addView(intro);
        status = new TextView(this); status.setTextSize(14); status.setTextColor(getColor(R.color.text_primary)); status.setBackgroundResource(R.drawable.bg_card); status.setPadding(28,28,28,28); box.addView(status);
        button("Zustand aktualisieren", v -> snapshot("MANUAL SNAPSHOT"));
        button("Monitoring starten", v -> startMonitor());
        button("Monitoring stoppen", v -> stopService(new Intent(this, MonitorService.class)));
        button("Audio-Routing reparieren", v -> resetRouting());
        button("SCO-Verbindung neu aufbauen", v -> restartSco());
        button("Bluetooth-Einstellungen öffnen", v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        button("Diagnoseprotokoll teilen", v -> shareLog());
        requestNeededPermissions(); snapshot("APP START");
    }
    void button(String text, View.OnClickListener l) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(15); b.setMinHeight(56); b.setTextColor(getColor(R.color.text_primary)); b.setBackgroundResource(R.drawable.bg_secondary); b.setOnClickListener(l); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=14; box.addView(b,p); }
    void requestNeededPermissions() {
        List<String> p=new ArrayList<>();
        if (Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!p.isEmpty()) requestPermissions(p.toArray(new String[0]),10);
    }
    void snapshot(String tag) { String s=Diag.snapshot(this); Diag.log(this, tag+"\n"+s); status.setText(s); }
    void startMonitor() { try { startForegroundService(new Intent(this, MonitorService.class)); status.setText("Monitoring läuft. Alle 15 s wird ein Snapshot gespeichert."); } catch(Exception e){ status.setText("Monitoring konnte nicht starten: "+e); Diag.log(this,"MONITOR ERROR "+e); } }
    void resetRouting() {
        AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE); Diag.log(this,"TEST1 BEFORE\n"+Diag.snapshot(this));
        try { am.clearCommunicationDevice(); am.setSpeakerphoneOn(false); am.setMode(AudioManager.MODE_NORMAL); status.setText("Test 1 ausgeführt. Jetzt einen Anruf testen.\n\n"+Diag.snapshot(this)); }
        catch(Exception e){ status.setText("Test 1 Fehler: "+e); }
        Diag.log(this,"TEST1 AFTER\n"+Diag.snapshot(this));
    }
    @SuppressWarnings("deprecation") void restartSco() {
        AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE); Diag.log(this,"TEST2 BEFORE\n"+Diag.snapshot(this));
        try {
            am.stopBluetoothSco(); am.clearCommunicationDevice(); am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                boolean selected=false;
                for(AudioDeviceInfo d:am.getAvailableCommunicationDevices()) if(d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO){ selected=am.setCommunicationDevice(d); break; }
                if(!selected) am.startBluetoothSco();
                String s="SCO-Neuaufbau ausgeführt (communicationDevice="+selected+"). Jetzt einen Anruf testen.\n\n"+Diag.snapshot(this); status.setText(s); Diag.log(this,"TEST2 AFTER selected="+selected+"\n"+Diag.snapshot(this));
            },700);
        } catch(Exception e){ status.setText("Test 2 Fehler: "+e); Diag.log(this,"TEST2 ERROR "+e); }
    }
    void shareLog() {
        File f=new File(getFilesDir(),"a52s-bt-repair.log");
        if(!f.exists()){status.setText("Noch kein Log vorhanden.");return;}
        try { String text=new String(java.nio.file.Files.readAllBytes(f.toPath())); Intent i=new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_SUBJECT,"A52s Bluetooth Repair Log"); i.putExtra(Intent.EXTRA_TEXT,text); startActivity(Intent.createChooser(i,"Log teilen")); }
        catch(Exception e){status.setText("Log konnte nicht geteilt werden: "+e);}
    }
}
