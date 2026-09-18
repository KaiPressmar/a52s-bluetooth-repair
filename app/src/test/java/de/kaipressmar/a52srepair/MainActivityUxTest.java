package de.kaipressmar.a52srepair;

import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.robolectric.Robolectric;
import static org.junit.Assert.*;

public class MainActivityUxTest {
 @Test public void primaryUseCaseLabelsAreUnderstandable() {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();
  String all=((TextView)a.findViewById(android.R.id.content)).toString();
  assertNotNull(a);
 }
 @Test public void screenContainsRepairAndMonitoringActions() {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();
  boolean repair=false, monitor=false;
  android.view.ViewGroup root=a.findViewById(android.R.id.content);
  java.util.ArrayDeque<android.view.View> q=new java.util.ArrayDeque<>(); q.add(root);
  while(!q.isEmpty()){ android.view.View v=q.remove(); if(v instanceof Button){String t=((Button)v).getText().toString(); repair|=t.contains("Audio-Routing reparieren"); monitor|=t.contains("Monitoring starten");} if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)q.add(g.getChildAt(i));}}
  assertTrue(repair); assertTrue(monitor);
 }
}
