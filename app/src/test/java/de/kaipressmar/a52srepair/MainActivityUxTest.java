package de.kaipressmar.a52srepair;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityUxTest {
 @Test public void screenContainsCriticalUseCaseActions() {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
  assertTrue(hasButton(a,"Zustand aktualisieren"));
  assertTrue(hasButton(a,"Audio-Routing reparieren"));
  assertTrue(hasButton(a,"SCO-Verbindung neu aufbauen"));
  assertTrue(hasButton(a,"Monitoring starten"));
  assertTrue(hasButton(a,"Diagnoseprotokoll teilen"));
 }

 private boolean hasButton(MainActivity a,String label) {
  View root=a.findViewById(android.R.id.content);
  java.util.ArrayDeque<View> q=new java.util.ArrayDeque<>();
  q.add(root);
  while(!q.isEmpty()){
   View v=q.remove();
   if(v instanceof Button && label.contentEquals(((Button)v).getText())) return true;
   if(v instanceof ViewGroup){
    ViewGroup g=(ViewGroup)v;
    for(int i=0;i<g.getChildCount();i++) q.add(g.getChildAt(i));
   }
  }
  return false;
 }
}
