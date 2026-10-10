package de.kaipressmar.a52srepair.audio;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import android.telecom.TelecomManager;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.data.AppSettings;
import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,34,36})
public class IdleRepairControllerTest {
    private Context context;
    private AppSettings settings;
    private IdleRepairController controller;
    private TelecomManager telecom;
    private AudioManager audio;
    private Hfp hfp;
    private int updates;
    private static class Hfp implements HeadsetAccess {
        BluetoothDevice target=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:01");
        List<BluetoothDevice> devices=List.of(target);
        Boolean sco=false;int closes;
        public void open(){}public void close(){closes++;}
        public List<BluetoothDevice> connectedDevices(){return devices;}
        public Boolean scoAudioConnected(BluetoothDevice d){return d != null && devices.contains(d)?sco:null;}
        public boolean supportsVoiceRecognition(BluetoothDevice d){return false;}
        public boolean startVoiceRecognition(BluetoothDevice d){throw new AssertionError("unsupported voice must not start");}
        public boolean stopVoiceRecognition(BluetoothDevice d){throw new AssertionError("unowned voice must not stop");}
    }
    @Before public void setup() {
        context=ApplicationProvider.getApplicationContext();settings=new AppSettings(context);
        context.getSharedPreferences("app-state",Context.MODE_PRIVATE).edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        shadowOf((android.app.Application)context).grantPermissions(Manifest.permission.READ_PHONE_STATE,Manifest.permission.BLUETOOTH_CONNECT);
        telecom=context.getSystemService(TelecomManager.class);audio=context.getSystemService(AudioManager.class);
        shadowOf(telecom).setIsInCall(false);audio.setMode(AudioManager.MODE_NORMAL);hfp=new Hfp();
        shadowOf(audio).setAvailableCommunicationDevices(List.of(IdleAudioPortTest.info(
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,"00:11:22:33:44:01",1)));
        controller=new IdleRepairController(context,()->updates++,
                (event,id)->new IdleAudioPort(context,audio,telecom,hfp,id));
    }
    private static void advance(long ms){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    @Test public void manualTestRequiresVisibleActivityAndPausingCancelsAndRestoresMode() {
        assertEquals("NOT_VISIBLE",controller.startManually());controller.resume();
        assertNull(controller.startManually());assertEquals("BUSY",controller.startManually());advance(250);
        controller.pause();assertFalse(controller.running());assertEquals("CANCELLED",settings.idleResult());
        assertEquals(AudioManager.MODE_NORMAL,audio.getMode());assertTrue(hfp.closes>0);advance(80000);
        assertEquals("CANCELLED",settings.idleResult());
    }
    @Test public void callAddedCancelsImmediatelyAndNoBackgroundRestartOccurs() {
        controller.resume();assertNull(controller.startManually());advance(250);
        shadowOf(telecom).setIsInCall(true);IdleRepairController.callStarted();
        assertEquals("CANCELLED",settings.idleResult());assertFalse(controller.running());
        controller.pause();advance(80000);assertFalse(controller.running());IdleRepairController.callStarted();
    }
    @Test public void manualCooldownSurvivesControllerReplacementAndIsBounded() {
        controller.resume();assertNull(controller.startManually());controller.cancel("user");
        assertEquals("COOLDOWN",controller.startManually());advance(30001);
        assertNull(controller.startManually());controller.pause();
        assertTrue(updates>=3);
    }
    @Test public void permissionOrCallFailureDoesNotSpendAttemptBudget() {
        controller.resume();shadowOf(telecom).setIsInCall(true);
        assertEquals("BLOCKED",controller.startManually());assertTrue(settings.idleResult().startsWith("BLOCKED"));
        shadowOf(telecom).setIsInCall(false);assertNull(controller.startManually());controller.pause();
    }
    @Test public void automaticTestIsOptInAndPostCallCandidateWaitsUntilVisibleAndSafe() {
        settings.setIdlePostCallPending(true);advance(10000);assertFalse(controller.running());
        controller.resume();advance(1000);assertFalse(controller.running());
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("idle_auto",true).commit();
        shadowOf(telecom).setIsInCall(true);advance(1000);assertFalse(controller.running());assertTrue(settings.idlePostCallPending());
        shadowOf(telecom).setIsInCall(false);advance(1000);assertTrue(controller.running());assertFalse(settings.idlePostCallPending());
        controller.pause();advance(80000);assertFalse(controller.running());
    }
    @Test public void pausedProtectionPreventsAutomaticTestsButManualRemainsAvailable() {
        settings.setProtectionEnabled(false);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("idle_auto",true).commit();
        controller.resume();advance(3000);assertFalse(controller.running());
        assertNull(controller.startManually());controller.pause();
    }
    @Test public void turningOffAutoOrVoiceFallbackCancelsAnExistingOperation() {
        var prefs=PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean("idle_auto",true).commit();controller.resume();advance(250);
        assertTrue(controller.running());settings.setProtectionEnabled(false);advance(250);
        assertFalse(controller.running());assertEquals("CANCELLED",settings.idleResult());
        advance(30001);prefs.edit().putBoolean("idle_voice",true).commit();
        assertNull(controller.startManually());advance(250);
        prefs.edit().putBoolean("idle_voice",false).commit();advance(250);
        assertFalse(controller.running());controller.pause();
    }
    @Test public void unknownTargetAndRepeatedAutoEventsNeverLoopRepairs() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("idle_auto",true).commit();
        hfp.devices=List.of();controller.resume();advance(3000);assertFalse(controller.running());
        hfp.devices=List.of(hfp.target);advance(1000);assertTrue(controller.running());advance(80000);
        assertFalse(controller.running());assertEquals("UNRESOLVED",settings.idleResult());
        settings.setIdlePostCallPending(true);advance(10000);assertFalse(controller.running());controller.pause();
    }
    @Test public void interruptedDiscoveryAndPersistentCooldownStateDoNotLockOutAfterReboot() {
        settings.markIdleAttempt(100000);assertTrue(settings.idleCooldown(100001,30000));
        assertFalse(settings.idleCooldown(1,30000));assertFalse(settings.idleCooldown(130000,30000));
        controller.resume();hfp.devices=List.of();assertNull(controller.startManually());advance(6000);
        assertFalse(controller.running());assertEquals("BLOCKED",settings.idleResult());controller.pause();
    }
    @Test public void unknownTeardownLatchesControllerAgainstNewRequestsEvenAfterCleanupRetry() {
        controller.resume();assertNull(controller.startManually());advance(250);
        hfp.sco=true;advance(1000); // Link connects, but never turns off after release.
        advance(10000);assertEquals("CLEANUP_FAILED",settings.idleResult());
        advance(30001);assertEquals("BUSY",controller.startManually());controller.pause();
        controller.resume();assertEquals("BUSY",controller.startManually());controller.pause();
    }
    @Test public void duplexIsManualOnlyEvenWhenSettingAndMicrophonePermissionAreGranted() {
        shadowOf((android.app.Application)context).grantPermissions(Manifest.permission.RECORD_AUDIO);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("idle_auto",true).putBoolean("idle_duplex",true).commit();
        controller.resume();advance(1000);assertTrue(controller.running());
        de.kaipressmar.a52srepair.core.repair.IdleRepairEngine engine=org.robolectric.util.ReflectionHelpers.getField(controller,"engine");
        assertFalse(org.robolectric.util.ReflectionHelpers.<Boolean>getField(engine,"duplexEnabled"));
        controller.cancel("manual next");advance(30001);assertNull(controller.startManually());
        engine=org.robolectric.util.ReflectionHelpers.getField(controller,"engine");
        assertTrue(org.robolectric.util.ReflectionHelpers.<Boolean>getField(engine,"duplexEnabled"));
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("idle_duplex",false).commit();
        advance(250);assertEquals("CANCELLED",settings.idleResult());controller.pause();
    }
}
