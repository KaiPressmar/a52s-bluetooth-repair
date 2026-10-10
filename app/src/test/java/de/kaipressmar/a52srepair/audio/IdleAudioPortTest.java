package de.kaipressmar.a52srepair.audio;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.telecom.TelecomManager;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.core.repair.IdleRepairEngine.Method;
import de.kaipressmar.a52srepair.diagnostics.AudioDiagnostics;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticLog;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,34,36})
public class IdleAudioPortTest {
    private Context context;
    private AudioManager audio;
    private TelecomManager telecom;
    private FakeHeadset hfp;
    private IdleAudioPort port;
    private static final String ADDRESS="00:11:22:33:44:01";
    private static BluetoothDevice device(String address) {return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);}
    private static final class FakeHeadset implements HeadsetAccess {
        List<BluetoothDevice> devices=List.of(device(ADDRESS));
        Boolean sco=false;
        boolean voiceSupported=true,voiceAccepted=true,stopAccepted=true,failStart,failStop;
        int starts,stops,opened,closed;
        public void open(){opened++;}public void close(){closed++;}
        public List<BluetoothDevice> connectedDevices(){return devices;}
        public Boolean scoAudioConnected(BluetoothDevice target){return devices.contains(target)?sco:null;}
        public boolean supportsVoiceRecognition(BluetoothDevice target){return voiceSupported;}
        public boolean startVoiceRecognition(BluetoothDevice target){starts++;if(failStart)throw new IllegalStateException();return voiceAccepted;}
        public boolean stopVoiceRecognition(BluetoothDevice target){stops++;if(failStop)throw new IllegalStateException();return stopAccepted;}
    }
    static AudioDeviceInfo info(int type,String address,int id) {
        AudioDeviceInfo d=org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder().setType(type).build();
        Object p=ReflectionHelpers.getField(d,"mPort");ReflectionHelpers.setField(p,"mRole",2);
        ReflectionHelpers.setField(p,"mAddress",address);ReflectionHelpers.setField(ReflectionHelpers.getField(p,"mHandle"),"mId",id);return d;
    }
    @Before public void prepare() {
        context=ApplicationProvider.getApplicationContext();audio=context.getSystemService(AudioManager.class);
        telecom=context.getSystemService(TelecomManager.class);
        shadowOf((android.app.Application)context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.READ_PHONE_STATE);
        shadowOf(telecom).setIsInCall(false);audio.setMode(AudioManager.MODE_NORMAL);
        shadowOf(audio).setIsMusicActive(false);hfp=new FakeHeadset();
        port=new IdleAudioPort(context,audio,telecom,hfp,"test");port.targetReady();
    }
    @Test public void productionUsesAnIsolatedAudioManagerForIdleBinderOwnership() {
        IdleAudioPort production=new IdleAudioPort(context,()->{},"isolated");
        AudioManager isolated=ReflectionHelpers.getField(production,"audio");
        assertNotSame(audio,isolated);
        assertNotSame(ReflectionHelpers.getField(audio,"mICallBack"),ReflectionHelpers.getField(isolated,"mICallBack"));
        assertTrue(production.release());production.closeObserver();
    }
    @Test public void duplexNeedsExplicitMicrophoneGrantExactInputOutputAndUnmutedMic() {
        AudioDeviceInfo output=info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,1);
        AudioDeviceInfo input=info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,2);
        ReflectionHelpers.setField(ReflectionHelpers.getField(input,"mPort"),"mRole",1);
        shadowOf(audio).setAvailableCommunicationDevices(List.of(output));shadowOf(audio).setInputDevices(List.of(input));
        shadowOf((android.app.Application)context).denyPermissions(Manifest.permission.RECORD_AUDIO);
        assertFalse(port.supports(Method.DUPLEX));assertFalse(port.start(Method.DUPLEX));assertEquals(AudioManager.MODE_NORMAL,audio.getMode());
        shadowOf((android.app.Application)context).grantPermissions(Manifest.permission.RECORD_AUDIO);
        assertTrue(port.supports(Method.DUPLEX));audio.setMicrophoneMute(true);assertFalse(port.supports(Method.DUPLEX));
        audio.setMicrophoneMute(false);shadowOf(audio).setInputDevices(List.of());assertFalse(port.supports(Method.DUPLEX));
        shadowOf(audio).setInputDevices(List.of(input,input));assertFalse(port.supports(Method.DUPLEX));
        ReflectionHelpers.setField(ReflectionHelpers.getField(input,"mPort"),"mAddress","");
        shadowOf(audio).setInputDevices(List.of(input));assertFalse(port.supports(Method.DUPLEX));
        assertTrue(port.release());
    }
    @Test @Config(shadows={PreferredTrack.class}) public void actualDuplexBuilderOwnsCaptureAndPermissionRevocationStopsIt() {
        AudioDeviceInfo output=info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,1);
        AudioDeviceInfo input=info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,2);
        ReflectionHelpers.setField(ReflectionHelpers.getField(input,"mPort"),"mRole",1);
        shadowOf(audio).setAvailableCommunicationDevices(List.of(output));shadowOf(audio).setInputDevices(List.of(input));
        shadowOf((android.app.Application)context).grantPermissions(Manifest.permission.RECORD_AUDIO);
        assertTrue(port.start(Method.DUPLEX));assertNotNull(ReflectionHelpers.getField(port,"duplex"));
        assertFalse(port.transportReady()); // Native route/config missing in simulator is not success.
        shadowOf((android.app.Application)context).denyPermissions(Manifest.permission.RECORD_AUDIO);
        assertTrue(port.blockedReason().contains("microphone"));
        assertTrue(port.release());assertNull(ReflectionHelpers.getField(port,"duplex"));
        assertEquals(AudioManager.MODE_NORMAL,audio.getMode());
    }
    @Implements(android.media.AudioTrack.class)
    public static class PreferredTrack extends org.robolectric.shadows.ShadowAudioTrack {
        @Implementation protected boolean native_setOutputDevice(int deviceId) { return true; }
    }
    @Test public void permissionsAreRequiredAndUnavailableSafetyObservationsFailClosed() {
        assertNull(port.blockedReason());
        shadowOf((android.app.Application)context).denyPermissions(Manifest.permission.READ_PHONE_STATE);
        assertNotNull(port.blockedReason());assertFalse(port.start(Method.VOICE_RECOGNITION));assertEquals(0,hfp.starts);
        shadowOf((android.app.Application)context).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        assertNotNull(port.blockedReason());
        shadowOf((android.app.Application)context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.READ_PHONE_STATE);
        assertNotNull(new IdleAudioPort(context,null,telecom,hfp,"missing").blockedReason());
        assertNotNull(new IdleAudioPort(context,audio,null,hfp,"missing").blockedReason());
    }
    @Test public void activeCallsRingingCommunicationAndMusicBlockWithoutMutations() {
        shadowOf(telecom).setIsInCall(true);assertNotNull(port.blockedReason());assertFalse(port.start(Method.MODERN));
        shadowOf(telecom).setIsInCall(false);
        for(int mode:List.of(AudioManager.MODE_RINGTONE,AudioManager.MODE_IN_CALL,AudioManager.MODE_IN_COMMUNICATION)) {
            audio.setMode(mode);assertFalse(port.start(Method.VOICE_RECOGNITION));assertEquals(mode,audio.getMode());
        }
        audio.setMode(AudioManager.MODE_NORMAL);shadowOf(audio).setIsMusicActive(true);
        assertNotNull(port.blockedReason());assertEquals(0,hfp.starts);
    }
    @Test public void multipleDevicesAndTargetReplacementNeverSelectAnotherHeadset() {
        BluetoothDevice other=device("00:11:22:33:44:02");
        hfp.devices=List.of(device(ADDRESS),other);assertFalse(port.targetReady());
        hfp.devices=List.of(other);assertFalse(port.targetReady());assertFalse(port.start(Method.LEGACY_SCO));
        hfp.devices=List.of();assertFalse(port.targetReady());
    }
    @Test public void onlyIdleObservationCanForgetItsPinnedTarget() {
        assertTrue(port.start(Method.VOICE_RECOGNITION));
        BluetoothDevice other=device("00:11:22:33:44:02");hfp.devices=List.of(other);
        port.resetIdleObservationTarget();assertFalse(port.targetReady());
        assertTrue(port.release());port.resetIdleObservationTarget();assertTrue(port.targetReady());
    }
    @Test public void modernRequiresUniqueAddressMatchedNonzeroScoOutput() {
        assertFalse(port.supports(Method.MODERN));
        for(AudioDeviceInfo d:List.of(info(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,ADDRESS,1),
                info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,"",1),info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,0),
                info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,"00:11:22:33:44:02",1))) {
            shadowOf(audio).setAvailableCommunicationDevices(List.of(d));assertFalse(port.supports(Method.MODERN));
        }
        AudioDeviceInfo car=info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS.toLowerCase(),1);
        shadowOf(audio).setAvailableCommunicationDevices(List.of(car));assertTrue(port.supports(Method.MODERN));
        shadowOf(audio).setAvailableCommunicationDevices(List.of(car,info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,2)));
        assertFalse(port.supports(Method.MODERN));
    }
    @Test public void successfulModernRequestOwnsOnlyItsModeAndSelectionAndPreservesMuteVolume() {
        AudioDeviceInfo car=info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,1);
        shadowOf(audio).setAvailableCommunicationDevices(List.of(car));audio.setMicrophoneMute(true);
        int volume=audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        assertTrue(port.start(Method.MODERN));assertSame(car,audio.getCommunicationDevice());
        assertEquals(AudioManager.MODE_IN_COMMUNICATION,audio.getMode());assertNull(port.blockedReason());
        assertTrue(port.release());assertTrue(port.release());assertNull(audio.getCommunicationDevice());
        assertEquals(AudioManager.MODE_NORMAL,audio.getMode());assertTrue(audio.isMicrophoneMute());
        assertEquals(volume,audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL));
    }
    @Test public void wiredUsbAndHearingAidOutputsBlockWhileOrdinarySpeakerDoesNot() {
        for(int type:List.of(AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_HEARING_AID)) {
            shadowOf(audio).setOutputDevices(List.of(info(type,"",2)));
            assertNotNull(port.blockedReason());assertFalse(port.start(Method.MODERN));
        }
        shadowOf(audio).setOutputDevices(List.of(info(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,"",2)));
        assertNull(port.blockedReason());
    }
    @Test public void communicationPortRemovalBeforeRequestStillReleasesSilentTrackAndMode() {
        shadowOf(audio).setAvailableCommunicationDevices(List.of(info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,1)));
        assertTrue(port.supports(Method.MODERN));
        shadowOf(audio).setAvailableCommunicationDevices(List.of());
        assertFalse(port.start(Method.MODERN));assertTrue(port.release());
        assertEquals(AudioManager.MODE_NORMAL,audio.getMode());assertNull(audio.getCommunicationDevice());
    }
    @Test public void rejectedModernRequestStillReleasesModeAndTrack() {
        shadowOf(audio).setAvailableCommunicationDevices(List.of(info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,1)));
        shadowOf(audio).lockCommunicationDevice(true);assertFalse(port.start(Method.MODERN));
        shadowOf(audio).lockCommunicationDevice(false);assertTrue(port.release());assertEquals(AudioManager.MODE_NORMAL,audio.getMode());
    }
    @Test public void legacyRequestIsNotProofOfScoAndStopsOnlyItsRequest() {
        assertTrue(port.start(Method.LEGACY_SCO));assertEquals(Boolean.FALSE,port.scoConnected());
        assertTrue(port.release());assertEquals(AudioManager.MODE_NORMAL,audio.getMode());
    }
    @Test public void voiceSessionDoesNotUseModeOrRecordingAndStopsOnlyAfterOurAcceptedStart() {
        assertTrue(port.supports(Method.VOICE_RECOGNITION));assertTrue(port.start(Method.VOICE_RECOGNITION));
        assertEquals(AudioManager.MODE_NORMAL,audio.getMode());assertTrue(port.release());assertEquals(1,hfp.stops);
        assertTrue(port.release());assertEquals(1,hfp.stops);
        hfp.voiceAccepted=false;assertFalse(port.start(Method.VOICE_RECOGNITION));assertTrue(port.release());assertEquals(1,hfp.stops);
    }
    @Test public void exceptionAfterVoiceAcceptanceRetainsCleanupAndRetriesIt() {
        hfp.failStart=true;hfp.failStop=true;assertFalse(port.start(Method.VOICE_RECOGNITION));
        assertFalse(port.release());hfp.failStop=false;assertTrue(port.release());assertEquals(2,hfp.stops);
    }
    @Test public void incomingCallHandsVoiceChannelToSystemWithoutStoppingItsAudio() {
        assertTrue(port.start(Method.VOICE_RECOGNITION));shadowOf(telecom).setIsInCall(true);
        assertTrue(port.release());assertEquals(0,hfp.stops);
        assertTrue(DiagnosticLog.readAll(context).contains("VOICE_HANDOVER"));
    }
    @Test public void uncertainVoiceStopAndTargetSwapBlockRatherThanStoppingAnotherDevice() {
        assertTrue(port.start(Method.VOICE_RECOGNITION));hfp.stopAccepted=false;hfp.sco=true;
        assertFalse(port.release());
        hfp.devices=List.of(device(ADDRESS),device("00:11:22:33:44:02"));assertFalse(port.release());assertEquals(1,hfp.stops);
        hfp.devices=List.of(device("00:11:22:33:44:02"));assertTrue(port.release());assertEquals(1,hfp.stops);
    }
    @Test public void occupiedScoIsNeverStartedOrStoppedAsOurRequest() {
        hfp.sco=true;assertFalse(port.start(Method.VOICE_RECOGNITION));assertTrue(port.release());assertEquals(0,hfp.stops);
        hfp.sco=null;assertFalse(port.start(Method.MODERN));
    }
    @Test public void detailedLogsAndInventoryContainNoDeviceAddressOrName() {
        shadowOf(audio).setAvailableCommunicationDevices(List.of(info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,1)));
        port.open();port.observe();port.observe();port.closeObserver();
        String log=DiagnosticLog.readAll(context);assertTrue(log.contains("targetMatch=true"));assertFalse(log.contains(ADDRESS));
        assertEquals(1,hfp.closed);assertEquals("audioManager=absent",AudioDiagnostics.capture(null,null));
        assertEquals("none",AudioDiagnostics.device(null,null));
    }
    @Implements(AudioManager.class)
    public static class FailingAudio extends org.robolectric.shadows.ShadowAudioManager {
        boolean failSet,failClear,failMode,failRead,failList,failLegacyStart,failLegacyStop;
        @Implementation protected boolean setCommunicationDevice(AudioDeviceInfo d) {
            boolean result=super.setCommunicationDevice(d);if(failSet)throw new IllegalStateException();return result;
        }
        @Implementation protected void clearCommunicationDevice(){if(failClear)throw new IllegalStateException();super.clearCommunicationDevice();}
        @Implementation protected void setMode(int mode){if(failMode&&mode==AudioManager.MODE_NORMAL)throw new IllegalStateException();super.setMode(mode);}
        @Implementation protected java.util.List<AudioDeviceInfo> getAvailableCommunicationDevices() {
            if(failList)throw new IllegalStateException();return super.getAvailableCommunicationDevices();
        }
        @Implementation protected void startBluetoothSco(){if(failLegacyStart)throw new IllegalStateException();}
        @Implementation protected void stopBluetoothSco(){if(failLegacyStop)throw new IllegalStateException();}
        @Implementation protected int getMode(){if(failRead)throw new IllegalStateException();return super.getMode();}
    }
    @Test @Config(sdk=34,shadows=FailingAudio.class)
    public void failedPortInventoryAndLegacyBinderReleaseDoNotLeakOwnershipOrAssumeSupport() {
        FailingAudio platform=org.robolectric.shadow.api.Shadow.extract(audio);
        platform.failList=true;assertFalse(port.supports(Method.MODERN));platform.failList=false;
        platform.failLegacyStart=true;platform.failLegacyStop=true;
        assertFalse(port.start(Method.LEGACY_SCO));assertFalse(port.release());
        platform.failLegacyStop=false;assertTrue(port.release());
        assertEquals(AudioManager.MODE_NORMAL,audio.getMode());
        assertTrue(DiagnosticLog.readAll(context).contains("STOP_SCO_ERROR"));
    }
    @Test @Config(sdk=34,shadows=FailingAudio.class)
    public void binderFailureAfterAcceptanceAndDuringReleaseRemainsOwnedUntilSuccessfulCleanup() {
        FailingAudio platform=org.robolectric.shadow.api.Shadow.extract(audio);
        platform.setAvailableCommunicationDevices(List.of(info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,ADDRESS,1)));
        platform.failSet=true;platform.failClear=true;platform.failMode=true;
        assertFalse(port.start(Method.MODERN));assertFalse(port.release());assertNotNull(audio.getCommunicationDevice());
        platform.failClear=false;platform.failMode=false;assertTrue(port.release());assertNull(audio.getCommunicationDevice());
        platform.failRead=true;assertNotNull(port.blockedReason());assertTrue(AudioDiagnostics.capture(audio,null).contains("IllegalStateException"));
    }
}
