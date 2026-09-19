package de.kaipressmar.a52srepair;
import android.view.*;import android.widget.*;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MainActivityUxTest {
 @Test public void dashboardHasModernUseCaseHierarchy(){
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
  String t=allText(a.findViewById(android.R.id.content));
  assertContains(t,"Deine Verbindung.");
  assertContains(t,"BLUETOOTH TELEFONIE");
  assertContains(t,"HFP");
  assertContains(t,"SCO");
  assertContains(t,"Schnellaktionen");
  assertContains(t,"Diagnose starten");
  assertContains(t,"Audio reparieren");
  assertContains(t,"Monitoring starten");
  assertContains(t,"Werkzeuge & Diagnose");
  assertContains(t,"Version ");
 }
 private void assertContains(String actual,String expected){assertTrue("Expected dashboard text: "+expected+"\nActual text:\n"+actual,actual.contains(expected));}
 private String allText(View root){StringBuilder s=new StringBuilder();java.util.ArrayDeque<View>q=new java.util.ArrayDeque<>();q.add(root);while(!q.isEmpty()){View v=q.remove();if(v instanceof TextView)s.append(((TextView)v).getText()).append("\n");if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)q.add(g.getChildAt(i));}}return s.toString();}
}