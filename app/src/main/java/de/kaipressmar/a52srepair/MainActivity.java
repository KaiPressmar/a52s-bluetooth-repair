package de.kaipressmar.a52srepair;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

public class MainActivity extends Activity {
 private LinearLayout box; private TextView health,device,details; private Button monitor;
 private final int green=0xff087a52, ink=0xff12231c, muted=0xff68766f;

 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  getWindow().setStatusBarColor(0xfff7faf8); getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  ScrollView s=new ScrollView(this); s.setFillViewport(true); s.setBackgroundColor(0xfff7faf8);
  box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24),dp(20),dp(24),dp(36)); s.addView(box); setContentView(s);
  header(); dashboard(); requestNeededPermissions(); refresh(false);
 }
 private void header(){
  LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
  TextView logo=text("⌁",28,Typeface.BOLD); logo.setTextColor(0xffffffff); logo.setGravity(Gravity.CENTER); logo.setBackgroundResource(R.drawable.bg_primary);
  row.addView(logo,new LinearLayout.LayoutParams(dp(52),dp(52)));
  TextView name=text("A52s\nBluetooth Repair",20,Typeface.BOLD); LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1); np.leftMargin=dp(14); row.addView(name,np);
  TextView gear=text("⚙",25,Typeface.NORMAL); gear.setGravity(Gravity.CENTER); gear.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))); row.addView(gear,new LinearLayout.LayoutParams(dp(48),dp(48)));
  box.addView(row);
  health=text("●  Status wird geprüft",14,Typeface.BOLD); health.setTextColor(green); health.setPadding(dp(12),dp(7),dp(12),dp(7)); health.setBackgroundResource(R.drawable.bg_status_good); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-2,-2); hp.topMargin=dp(18); box.addView(health,hp);
  TextView hero=text("Deine Verbindung.\nOhne Unterbrechungen.",29,Typeface.BOLD); LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,-2); h.topMargin=dp(14); box.addView(hero,h);
  TextView sub=text("Diagnostiziere und behebe Bluetooth-Telefonie-Audio gezielt – mit nachvollziehbaren, sicheren Schritten.",16,Typeface.NORMAL); sub.setTextColor(muted); sub.setLineSpacing(0,1.15f); add(sub,8);
 }
 private void dashboard(){
  device=text("",16,Typeface.BOLD); device.setPadding(dp(18),dp(17),dp(18),dp(17)); device.setBackgroundResource(R.drawable.bg_card); add(device,20);
  TextView section=text("Schnellaktionen",18,Typeface.BOLD); add(section,22);
  action("⌕   Diagnose starten","Aktuellen Bluetooth- und Audiozustand prüfen",true,v->refresh(true));
  action("🔧   Audio reparieren","Routing zurücksetzen und anschließend Anruf testen",false,v->resetRouting());
  action("↻   SCO neu aufbauen","Telefonie-Audiopfad gezielt neu verbinden",false,v->restartSco());
  monitor=action("●   Monitoring starten","Alle 15 Sekunden Diagnosezustand protokollieren",false,v->toggleMonitor());
  TextView tip=text("Tipp\nFalls Anruf-Audio ausfällt: zuerst Diagnose starten. Danach genau eine Reparaturaktion ausführen und erneut testen.",14,Typeface.NORMAL); tip.setLineSpacing(0,1.15f); tip.setPadding(dp(18),dp(16),dp(18),dp(16)); tip.setBackgroundResource(R.drawable.bg_status_good); add(tip,12);
  TextView tech=text("Technische Details",18,Typeface.BOLD); add(tech,24);
  details=text("",12,Typeface.NORMAL); details.setTypeface(Typeface.MONOSPACE); details.setTextColor(muted); details.setPadding(dp(18),dp(16),dp(18),dp(16)); details.setBackgroundResource(R.drawable.bg_card); add(details,10);
  action("⚙   Bluetooth-Einstellungen","Android Bluetooth-Einstellungen öffnen",false,v->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
  action("↗   Diagnoseprotokoll teilen","Gesammelte Diagnoseinformationen exportieren",false,v->shareLog());
 }
 private Button action(String title,String subtitle,boolean primary,View.OnClickListener l){
  Button b=new Button(this); b.setAllCaps(false); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); b.setText(title+"\n"+subtitle); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setTextColor(primary?0xffffffff:ink); b.setPadding(dp(18),dp(11),dp(18),dp(11)); b.setMinHeight(dp(70)); b.setBackgroundResource(primary?R.drawable.bg_primary:R.drawable.bg_secondary); b.setOnClickListener(l); add(b,10); return b;
 }
 private TextView text(String s,float size,int style){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(ink);v.setTypeface(Typeface.DEFAULT,style);return v;}
 private void add(View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);box.addView(v,p);}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private boolean btPermission(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
 private boolean btEnabled(){try{return btPermission()&&BluetoothAdapter.getDefaultAdapter()!=null&&BluetoothAdapter.getDefaultAdapter().isEnabled();}catch(Exception e){return false;}}
 private void refresh(boolean log){
  String snap=Diag.snapshot(this); if(log)Diag.log(this,"MANUAL SNAPSHOT\n"+snap); details.setText(snap);
  AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE); AudioDeviceInfo d=am.getCommunicationDevice(); boolean permission=btPermission(), enabled=btEnabled();
  String dn=d==null?"Kein Telefonie-Audiogerät aktiv":String.valueOf(d.getProductName());
  device.setText((d==null?"◯":"◉")+"   "+dn+"\n     "+(enabled?"Bluetooth aktiv":"Bluetooth ausgeschaltet"));
  if(!permission){health.setText("●  Bluetooth-Berechtigung erforderlich");health.setBackgroundResource(R.drawable.bg_status_warn);}
  else if(!enabled){health.setText("●  Bluetooth ist ausgeschaltet");health.setBackgroundResource(R.drawable.bg_status_warn);}
  else if(d==null){health.setText("●  Bereit – kein Telefonie-Audiogerät aktiv");health.setBackgroundResource(R.drawable.bg_status_neutral);}
  else {health.setText("●  Telefonie-Audio verbunden");health.setBackgroundResource(R.drawable.bg_status_good);}
 }
 private void requestNeededPermissions(){List<String>p=new ArrayList<>();if(Build.VERSION.SDK_INT>=31&&!btPermission())p.add(Manifest.permission.BLUETOOTH_CONNECT);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),10);}
 @Override public void onRequestPermissionsResult(int r,String[]p,int[]g){super.onRequestPermissionsResult(r,p,g);refresh(false);}
 private void toggleMonitor(){boolean running="Monitoring stoppen".equals(monitor.getTag());if(running){stopService(new Intent(this,MonitorService.class));monitor.setTag(null);monitor.setText("●   Monitoring starten\nAlle 15 Sekunden Diagnosezustand protokollieren");}else{try{startForegroundService(new Intent(this,MonitorService.class));monitor.setTag("Monitoring stoppen");monitor.setText("■   Monitoring stoppen\nDiagnoseaufzeichnung ist aktiv");}catch(Exception e){details.setText("Monitoring konnte nicht starten: "+e);}}}
 private void resetRouting(){AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);Diag.log(this,"TEST1 BEFORE\n"+Diag.snapshot(this));try{am.clearCommunicationDevice();am.setSpeakerphoneOn(false);am.setMode(AudioManager.MODE_NORMAL);Toast.makeText(this,"Audio-Routing zurückgesetzt – jetzt Testanruf durchführen.",Toast.LENGTH_LONG).show();}catch(Exception e){details.setText("Reparaturfehler: "+e);}Diag.log(this,"TEST1 AFTER\n"+Diag.snapshot(this));refresh(false);}
 @SuppressWarnings("deprecation") private void restartSco(){AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);Diag.log(this,"TEST2 BEFORE\n"+Diag.snapshot(this));try{am.stopBluetoothSco();am.clearCommunicationDevice();am.setMode(AudioManager.MODE_IN_COMMUNICATION);new Handler(Looper.getMainLooper()).postDelayed(()->{boolean selected=false;for(AudioDeviceInfo d:am.getAvailableCommunicationDevices())if(d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO){selected=am.setCommunicationDevice(d);break;}if(!selected)am.startBluetoothSco();Diag.log(this,"TEST2 AFTER selected="+selected+"\n"+Diag.snapshot(this));Toast.makeText(this,"SCO neu aufgebaut – jetzt Testanruf durchführen.",Toast.LENGTH_LONG).show();refresh(false);},700);}catch(Exception e){details.setText("SCO-Fehler: "+e);}}
 private void shareLog(){File f=new File(getFilesDir(),"a52s-bt-repair.log");if(!f.exists()){Toast.makeText(this,"Noch kein Diagnoseprotokoll vorhanden.",Toast.LENGTH_SHORT).show();return;}try{String t=new String(java.nio.file.Files.readAllBytes(f.toPath()));Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"A52s Bluetooth Repair Log");i.putExtra(Intent.EXTRA_TEXT,t);startActivity(Intent.createChooser(i,"Diagnoseprotokoll teilen"));}catch(Exception e){details.setText("Protokoll konnte nicht geteilt werden: "+e);}}
}
