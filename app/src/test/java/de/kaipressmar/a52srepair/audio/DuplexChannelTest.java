package de.kaipressmar.a52srepair.audio;

import static org.junit.Assert.*;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAudioTrack;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,34,36})
public class DuplexChannelTest {
    private final List<String> events=new ArrayList<>();
    private AudioDeviceInfo input,output;
    private AudioTrack track;
    private Fake capture;
    private DuplexChannel channel;
    private static class Fake implements DuplexChannel.Capture {
        boolean initialized=true,preferred=true,active,failStart,failStop,failRelease,failObservation,changeOnRead,stopOnStart;
        Boolean silenced=false;int reads,readResult=320,releases,stops;AudioDeviceInfo route;
        public boolean initialized(){return initialized;}public boolean prefer(AudioDeviceInfo d){return preferred;}
        public void start(){active=!stopOnStart;if(failStart)throw new IllegalStateException();}
        public boolean recording(){return active;}public int session(){return 8;}
        public AudioDeviceInfo routed(){if(failObservation)throw new IllegalStateException();return route;}
        public Boolean silenced(){return silenced;}
        public int read(byte[] buffer){reads++;Arrays.fill(buffer,(byte)91);if(changeOnRead)route=null;return readResult;}
        public void stop(){stops++;if(failStop)throw new IllegalStateException();active=false;}
        public void release(){releases++;if(failRelease)throw new IllegalStateException();}
    }
    @Before public void setup() {
        output=IdleAudioPortTest.info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,"00:11:22:33:44:01",1);
        input=IdleAudioPortTest.info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,"00:11:22:33:44:01",2);
        ReflectionHelpers.setField(ReflectionHelpers.getField(input,"mPort"),"mRole",1);
        track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(3200).build();
        capture=new Fake();capture.route=input;ShadowAudioTrack.setRoutedDevice(output);
        channel=new DuplexChannel(capture,track,input,output,events::add);
    }
    @Test public void exactBothRoutesAndUnsilencedFramesAreRequiredAndSamplesAreErased() {
        assertTrue(channel.start());assertTrue(channel.ready());assertEquals(1,capture.reads);
        byte[] discarded=ReflectionHelpers.getField(channel,"discard");assertArrayEquals(new byte[320],discarded);
        assertTrue(channel.close());assertTrue(channel.close());assertEquals(1,capture.releases);
        assertFalse(channel.ready());assertNull(channel.failure());
        assertFalse(events.toString().contains("00:11"));assertFalse(events.toString().contains("91"));
    }
    @Test public void preferredDeviceDoesNotProveActualRouteAndFallbackMicIsNeverRead() {
        assertTrue(channel.start());capture.route=null;assertFalse(channel.ready());
        capture.route=output;assertFalse(channel.ready());
        capture.route=input;ShadowAudioTrack.setRoutedDevice(null);assertFalse(channel.ready());
        ShadowAudioTrack.setRoutedDevice(output);capture.silenced=null;assertFalse(channel.ready());
        assertEquals(0,capture.reads);assertTrue(channel.close());
    }
    @Test public void silencedCaptureAndDeadNativeStreamNeverPassAndAreClosed() {
        assertTrue(channel.start());capture.silenced=true;assertFalse(channel.ready());assertNotNull(channel.failure());
        assertEquals(0,capture.reads);assertTrue(channel.close());
        capture=new Fake();capture.route=input;capture.readResult=-6;
        channel=new DuplexChannel(capture,track,input,output,events::add);assertTrue(channel.start());
        assertFalse(channel.ready());assertTrue(channel.failure().contains("-6"));
        assertArrayEquals(new byte[320],ReflectionHelpers.<byte[]>getField(channel,"discard"));assertTrue(channel.close());
    }
    @Test public void noFramesWaitsAndNativeObservationFailureIsNotSuccess() {
        assertTrue(channel.start());capture.readResult=0;assertFalse(channel.ready());assertNull(channel.failure());
        capture.failObservation=true;assertFalse(channel.ready());assertNotNull(channel.failure());assertTrue(channel.close());
    }
    @Test public void rejectedAndPartiallyAcceptedStartAreReleased() {
        capture.initialized=false;assertFalse(channel.start());assertNotNull(channel.failure());assertTrue(channel.close());
        capture=new Fake();capture.preferred=false;channel=new DuplexChannel(capture,track,input,output,events::add);
        assertFalse(channel.start());assertTrue(channel.close());
        capture=new Fake();capture.failStart=true;channel=new DuplexChannel(capture,track,input,output,events::add);
        assertFalse(channel.start());assertTrue(channel.close());assertEquals(1,capture.stops);
        capture=new Fake();capture.stopOnStart=true;channel=new DuplexChannel(capture,track,input,output,events::add);
        assertFalse(channel.start());assertTrue(channel.failure().contains("did not start"));assertTrue(channel.close());
    }
    @Test public void aRouteChangeDuringReadCannotPassAndSamplesAreStillErased() {
        assertTrue(channel.start());capture.changeOnRead=true;assertFalse(channel.ready());
        assertArrayEquals(new byte[320],ReflectionHelpers.<byte[]>getField(channel,"discard"));assertTrue(channel.close());
    }
    @Test public void failedStopStillAttemptsReleaseAndFailedReleaseRetainsOwnershipForRetry() {
        assertTrue(channel.start());capture.failStop=true;capture.failRelease=true;
        assertFalse(channel.close());assertEquals(1,capture.releases);
        capture.failRelease=false;assertTrue(channel.close());assertEquals(2,capture.releases);
    }
    @Test public void identityNeedsRoleTypeIdAndAddressAndCaseInsensitiveAddressIsAllowed() {
        assertFalse(DuplexChannel.matches(null,input));assertFalse(DuplexChannel.matches(input,null));
        assertFalse(DuplexChannel.matches(IdleAudioPortTest.info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,"",2),input));
        assertFalse(DuplexChannel.matches(IdleAudioPortTest.info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,"00:11:22:33:44:01",0),output));
        assertFalse(DuplexChannel.matches(IdleAudioPortTest.info(AudioDeviceInfo.TYPE_BUILTIN_MIC,"00:11:22:33:44:01",1),output));
        assertFalse(DuplexChannel.matches(IdleAudioPortTest.info(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,"00:11:22:33:44:02",1),output));
        assertTrue(DuplexChannel.matches(output,output));
    }
    @Test @Config(shadows={UnknownCaptureConfiguration.class}) public void realBuilderAndAdapterUseNonblockingCaptureAndReleaseTheNativeRecord() {
        DuplexChannel actual=DuplexChannel.create(track,input,output,events::add);
        DuplexChannel.Capture nativeCapture=ReflectionHelpers.getField(actual,"record");
        assertTrue(actual.start());assertTrue(nativeCapture.initialized());assertTrue(nativeCapture.recording());
        assertNull(nativeCapture.routed());assertNull(nativeCapture.silenced());nativeCapture.session();
        org.robolectric.shadows.ShadowAudioRecord.setSourceProvider(record->new org.robolectric.shadows.ShadowAudioRecord.AudioRecordSource(){
            public int readInByteArray(byte[] bytes,int offset,int count,boolean blocking){assertFalse(blocking);return count;}
        });
        assertEquals(320,nativeCapture.read(new byte[320]));assertTrue(actual.close());assertFalse(nativeCapture.recording());
    }
    // Android's real service returns an empty list => no configuration yet. Robolectric's
    // default IAudioService mock returns null instead; model the documented public null result.
    @Implements(className="android.media.AudioRecordingMonitorImpl",isInAndroidSdk=false)
    public static class UnknownCaptureConfiguration {
        @Implementation protected android.media.AudioRecordingConfiguration getActiveRecordingConfiguration(){return null;}
    }
}
