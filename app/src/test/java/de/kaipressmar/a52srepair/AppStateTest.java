package de.kaipressmar.a52srepair;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppStateTest {
 @Test public void missingPermissionIsActionable() {
  AppState s=new AppState(false,false,false,"none","");
  assertEquals("Berechtigung erforderlich",s.healthLabel());
  assertEquals(2,s.healthTone());
 }
 @Test public void bluetoothOffIsWarning() {
  AppState s=new AppState(false,true,false,"none","");
  assertEquals("Bluetooth ist ausgeschaltet",s.healthLabel());
  assertEquals(2,s.healthTone());
 }
 @Test public void enabledWithoutCallRouteIsReadyNotFailure() {
  AppState s=new AppState(true,true,false,"none","");
  assertEquals("Bereit – kein Telefonie-Audiogerät aktiv",s.healthLabel());
  assertEquals(1,s.healthTone());
 }
 @Test public void activeCommunicationDeviceIsHealthy() {
  AppState s=new AppState(true,true,true,"Car Audio","");
  assertEquals("Telefonie-Audio verbunden",s.healthLabel());
  assertEquals(0,s.healthTone());
 }
}
