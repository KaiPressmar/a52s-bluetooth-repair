package de.kaipressmar.a52srepair;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

public class MainActivity extends Activity {
 private LinearLayout content; private TextView statusPill, routeTitle, routeSub, details, monitorTitle, monitorSub;
 private boolean monitoring=false;
 private final int GREEN=Color.rgb(15,122,82), GREEN_DARK=Color.rgb(5,94,61), MINT=Color.rgb(226,247,237), INK=Color.rgb(18,32,26), MUTED=Color.rgb(102,116,109), BG=Color.rgb(246,249,247), BORDER=Color.rgb(226,234,229);

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(Color.WHITE); getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
  ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
  content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20),dp(18),dp(20),dp(28)); scroll.addView(content);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); root.addView(bottomNav()); setContentView(root);
  buildDashboard(); requestNeededPermissions(); refresh(false);
 }
 private void buildDashboard(){
  LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
  TextView mark=label("B",22,Typeface.BOLD,GREEN_DARK); mark.setTextColor(Color.WHITE); mark.setGravity(Gravity.CENTER); mark.setBackground(round(GREEN_DARK,18)); head.addView(mark,new LinearLayout.LayoutParams(dp(54),dp(54)));
  LinearLayout brandBox=new LinearLayout(this);brandBox.setOrientation(LinearLayout.VERTICAL);brandBox.addView(label("A52s Bluetooth Repair",19,Typeface.BOLD,INK));brandBox.addView(label("Version "+appVersion(),11,Typeface.NORMAL,MUTED));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.leftMargin=dp(14);head.addView(brandBox,bp);
  TextView gear=label("⚙",25,Typeface.NORMAL,INK);gear.setGravity(Gravity.CENTER);gear.setBackground(round(Color.WHITE,18));gear.setElevation(dp(1));gear.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));head.addView(gear,new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(head);

  statusPill=label("●  Status wird geprüft",13,Typeface.BOLD,GREEN_DARK);statusPill.setPadding(dp(12),dp(7),dp(12),dp(7));statusPill.setBackground(round(Color.rgb(229,246,237),99));add(statusPill,18,-2);

  add(label("Deine Verbindung.\nOhne Unterbrechungen.",30,Typeface.BOLD,INK),14,-1);
  

  LinearLayout route=card(); TextView icon=label("◉",24,Typeface.BOLD,GREEN);icon.setGravity(Gravity.CENTER);icon.setBackground(round(Color.rgb(232,247,240),99));route.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(50)));
  LinearLayout rt=new LinearLayout(this);rt.setOrientation(LinearLayout.VERTICAL);routeTitle=label("Telefonie-Audio",16,Typeface.BOLD,INK);routeSub=label("Verbindung wird geprüft …",13,Typeface.NORMAL,MUTED);rt.addView(routeTitle);rt.addView(routeSub);LinearLayout.LayoutParams rtp=new LinearLayout.LayoutParams(0,-2,1);rtp.leftMargin=dp(14);route.addView(rt,rtp);TextView arrow=label("›",28,Typeface.NORMAL,MUTED);route.addView(arrow);add(route,20,-1);

  add(sectionHeader("Schnellaktionen","Direkte Hilfe für Telefonie-Audio"),24,-1);
  action("⌕","Diagnose starten","Aktuellen Bluetooth- und Audiozustand prüfen",true,v->refresh(true));
  action("⌁","Audio reparieren","Audio-Routing zurücksetzen und Testanruf durchführen",false,v->resetRouting());
  action("↻","SCO neu aufbauen","Telefonie-Audiopfad gezielt neu verbinden",false,v->restartSco());
  LinearLayout mon=action("●","Monitoring starten","Diagnosezustand alle 15 Sekunden protokollieren",false,v->toggleMonitor());monitorTitle=(TextView)((LinearLayout)mon.getChildAt(1)).getChildAt(0);monitorSub=(TextView)((LinearLayout)mon.getChildAt(1)).getChildAt(1);

  LinearLayout tip=card();tip.setBackground(round(Color.rgb(233,248,240),18));TextView bulb=label("i",16,Typeface.BOLD,GREEN_DARK);bulb.setGravity(Gravity.CENTER);bulb.setBackground(round(Color.WHITE,99));tip.addView(bulb,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView tt=label("Tipp\nBei ausgefallenem Anruf-Audio zuerst Diagnose starten. Danach nur eine Reparaturaktion ausführen und erneut testen.",13,Typeface.NORMAL,INK);tt.setLineSpacing(dp(2),1f);LinearLayout.LayoutParams ttp=new LinearLayout.LayoutParams(0,-2,1);ttp.leftMargin=dp(12);tip.addView(tt,ttp);add(tip,12,-1);

  add(sectionHeader("Werkzeuge & Diagnose","Details, Export und Android-Einstellungen"),24,-1);
  action("⚙","Bluetooth-Einstellungen","Verbindungen direkt in Android verwalten",false,v->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
  action("↗","Diagnoseprotokoll teilen","Gesammelte Diagnoseinformationen exportieren",false,v->shareLog());

  details=label("",11,Typeface.NORMAL,MUTED);details.setTypeface(Typeface.MONOSPACE);details.setPadding(dp(16),dp(14),dp(16),dp(14));details.setBackground(round(Color.WHITE,16));details.setVisibility(View.GONE);add(details,10,-1);
  TextView technical=label("Technische Details anzeigen",13,Typeface.BOLD,MUTED);technical.setGravity(Gravity.CENTER);technical.setPadding(0,dp(12),0,dp(12));technical.setOnClickListener(v->{boolean show=details.getVisibility()!=View.VISIBLE;details.setVisibility(show?View.VISIBLE:View.GONE);technical.setText(show?"Technische Details ausblenden":"Technische Details anzeigen");});add(technical,2,-1);
 }
 private LinearLayout action(String symbol,String title,String subtitle,boolean primary,View.OnClickListener click){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(13),dp(14),dp(13));row.setBackground(round(primary?GREEN:Color.WHITE,18));row.setElevation(dp(primary?3:1));row.setOnClickListener(click);row.setClickable(true);
  TextView ico=label(symbol,22,Typeface.BOLD,primary?Color.WHITE:GREEN_DARK);ico.setGravity(Gravity.CENTER);ico.setBackground(round(primary?Color.argb(35,255,255,255):Color.rgb(235,247,241),99));row.addView(ico,new LinearLayout.LayoutParams(dp(46),dp(46)));
  LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);TextView t=label(title,15,Typeface.BOLD,primary?Color.WHITE:INK);TextView sub=label(subtitle,12,Typeface.NORMAL,primary?Color.rgb(222,244,235):MUTED);copy.addView(t);copy.addView(sub);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(13);row.addView(copy,cp);
  TextView chevron=label("›",26,Typeface.NORMAL,primary?Color.WHITE:MUTED);row.addView(chevron);add(row,10,-1);return row;
 }
 private LinearLayout heroCard(){LinearLayout h=new LinearLayout(this);h.setOrientation(LinearLayout.VERTICAL);h.setPadding(dp(20),dp(20),dp(20),dp(20));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{GREEN_DARK,GREEN});g.setCornerRadius(dp(24));h.setBackground(g);h.setElevation(dp(3));TextView kicker=label("BLUETOOTH TELEFONIE",11,Typeface.BOLD,Color.rgb(202,239,221));h.addView(kicker);TextView title=label("Deine Verbindung.\\nOhne Unterbrechungen.",28,Typeface.BOLD,Color.WHITE);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(8);h.addView(title,tp);TextView sub=label("Prüfen, reparieren und den Audio-Pfad im Blick behalten.",14,Typeface.NORMAL,Color.rgb(224,245,235));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(8);h.addView(sub,sp);LinearLayout chips=new LinearLayout(this);chips.setGravity(Gravity.CENTER_VERTICAL);chips.addView(chip("HFP"));chips.addView(chip("SCO"));chips.addView(chip("AUDIO"));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(16);h.addView(chips,cp);return h;} private TextView chip(String s){TextView v=label(s,10,Typeface.BOLD,Color.WHITE);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),dp(6),dp(10),dp(6));v.setBackground(round(Color.argb(32,255,255,255),99));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.rightMargin=dp(8);v.setLayoutParams(p);return v;} private LinearLayout sectionHeader(String title,String sub){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.addView(label(title,18,Typeface.BOLD,INK));x.addView(label(sub,12,Typeface.NORMAL,MUTED));return x;} private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(16),dp(15),dp(16),dp(15));v.setBackground(round(Color.WHITE,18));v.setElevation(dp(1));return v;}
 private View bottomNav(){
  LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(8),dp(8),dp(8),dp(10));nav.setBackgroundColor(Color.WHITE);nav.setElevation(dp(8));
  nav.addView(navItem("⌂","Start",true),new LinearLayout.LayoutParams(0,dp(58),1));nav.addView(navItem("⌕","Diagnose",false),new LinearLayout.LayoutParams(0,dp(58),1));nav.addView(navItem("⌁","Reparatur",false),new LinearLayout.LayoutParams(0,dp(58),1));nav.addView(navItem("≡","Verlauf",false),new LinearLayout.LayoutParams(0,dp(58),1));return nav;
 }
 private View navItem(String icon,String name,boolean active){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);TextView i=label(icon,21,Typeface.BOLD,active?GREEN_DARK:MUTED);TextView n=label(name,11,active?Typeface.BOLD:Typeface.NORMAL,active?GREEN_DARK:MUTED);x.addView(i);x.addView(n);return x;}
 private TextView label(String s,float size,int style,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans",style));return v;}
 private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(color==Color.WHITE)g.setStroke(dp(1),BORDER);return g;}
 private void add(View v,int top,int width){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,-2);p.topMargin=dp(top);content.addView(v,p);}
 private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "–";}}\n private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private boolean btPermission(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
 private boolean btEnabled(){try{return btPermission()&&BluetoothAdapter.getDefaultAdapter()!=null&&BluetoothAdapter.getDefaultAdapter().isEnabled();}catch(Exception e){return false;}}
 private void refresh(boolean log){String snap=Diag.snapshot(this);if(log)Diag.log(this,"MANUAL SNAPSHOT\n"+snap);details.setText(snap);AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);AudioDeviceInfo d=am.getCommunicationDevice();boolean permission=btPermission(),enabled=btEnabled();String name=d==null?"Kein Telefonie-Audiogerät aktiv":String.valueOf(d.getProductName());routeTitle.setText(name);routeSub.setText(enabled?(d==null?"Bluetooth aktiv · bereit für Anruf-Audio":"Verbunden · Telefonie-Audio aktiv"):"Bluetooth ausgeschaltet");if(!permission)setStatus("●  Berechtigung erforderlich",Color.rgb(255,244,221),Color.rgb(143,91,0));else if(!enabled)setStatus("●  Bluetooth ist ausgeschaltet",Color.rgb(255,244,221),Color.rgb(143,91,0));else if(d==null)setStatus("●  Bereit",Color.rgb(232,244,238),GREEN_DARK);else setStatus("●  Telefonie-Audio verbunden",Color.rgb(229,246,237),GREEN_DARK);}
 private void setStatus(String s,int bg,int fg){statusPill.setText(s);statusPill.setTextColor(fg);statusPill.setBackground(round(bg,99));}
 private void requestNeededPermissions(){List<String>p=new ArrayList<>();if(Build.VERSION.SDK_INT>=31&&!btPermission())p.add(Manifest.permission.BLUETOOTH_CONNECT);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),10);}
 @Override public void onRequestPermissionsResult(int r,String[]p,int[]g){super.onRequestPermissionsResult(r,p,g);refresh(false);}
 private void toggleMonitor(){if(monitoring){stopService(new Intent(this,MonitorService.class));monitoring=false;monitorTitle.setText("Monitoring starten");monitorSub.setText("Diagnosezustand alle 15 Sekunden protokollieren");}else try{startForegroundService(new Intent(this,MonitorService.class));monitoring=true;monitorTitle.setText("Monitoring stoppen");monitorSub.setText("Diagnoseaufzeichnung ist aktiv");}catch(Exception e){details.setVisibility(View.VISIBLE);details.setText("Monitoring konnte nicht starten: "+e);}}
 private void resetRouting(){AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);Diag.log(this,"TEST1 BEFORE\n"+Diag.snapshot(this));try{am.clearCommunicationDevice();am.setSpeakerphoneOn(false);am.setMode(AudioManager.MODE_NORMAL);Toast.makeText(this,"Audio-Routing repariert – jetzt Testanruf durchführen.",Toast.LENGTH_LONG).show();}catch(Exception e){details.setVisibility(View.VISIBLE);details.setText("Reparaturfehler: "+e);}Diag.log(this,"TEST1 AFTER\n"+Diag.snapshot(this));refresh(false);}
 @SuppressWarnings("deprecation") private void restartSco(){AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);Diag.log(this,"TEST2 BEFORE\n"+Diag.snapshot(this));try{am.stopBluetoothSco();am.clearCommunicationDevice();am.setMode(AudioManager.MODE_IN_COMMUNICATION);new Handler(Looper.getMainLooper()).postDelayed(()->{boolean selected=false;for(AudioDeviceInfo d:am.getAvailableCommunicationDevices())if(d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO){selected=am.setCommunicationDevice(d);break;}if(!selected)am.startBluetoothSco();Diag.log(this,"TEST2 AFTER selected="+selected+"\n"+Diag.snapshot(this));Toast.makeText(this,"SCO neu aufgebaut – jetzt Testanruf durchführen.",Toast.LENGTH_LONG).show();refresh(false);},700);}catch(Exception e){details.setVisibility(View.VISIBLE);details.setText("SCO-Fehler: "+e);}}
 private void shareLog(){File f=new File(getFilesDir(),"a52s-bt-repair.log");if(!f.exists()){Toast.makeText(this,"Noch kein Diagnoseprotokoll vorhanden.",Toast.LENGTH_SHORT).show();return;}try{String t=new String(java.nio.file.Files.readAllBytes(f.toPath()));Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"A52s Bluetooth Repair Log");i.putExtra(Intent.EXTRA_TEXT,t);startActivity(Intent.createChooser(i,"Diagnoseprotokoll teilen"));}catch(Exception e){details.setVisibility(View.VISIBLE);details.setText("Protokoll konnte nicht geteilt werden: "+e);}}
}
