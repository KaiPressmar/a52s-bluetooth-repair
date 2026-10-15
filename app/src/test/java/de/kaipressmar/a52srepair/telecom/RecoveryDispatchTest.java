package de.kaipressmar.a52srepair.telecom;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.data.CallReportRepository;
import de.kaipressmar.a52srepair.data.RecoveryJournalRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk={31,34,36})
@SuppressWarnings("deprecation")
public class RecoveryDispatchTest {
    private Context context;
    private static final int MASK=CallAudioState.ROUTE_BLUETOOTH|CallAudioState.ROUTE_EARPIECE|CallAudioState.ROUTE_SPEAKER;
    private static final class Host implements CallSession.Host {
        int route=CallAudioState.ROUTE_BLUETOOTH;
        int state=Call.STATE_ACTIVE;
        boolean safe=true, follow=true;
        String target="car";
        final List<Integer> requests=new ArrayList<>();
        @Override public CallAudioState audioState(){return new CallAudioState(false,route,MASK);}
        @Override public List<Integer> callStates(){return List.of(state);}
        @Override public boolean safeToRepair(){return safe;}
        @Override public String targetToken(){return target;}
        @Override public String selectedTargetToken(){return route==CallAudioState.ROUTE_BLUETOOTH?target:null;}
        @Override public void requestRoute(int requested){requests.add(requested);if(follow)route=requested;}
    }
    @Before public void setup(){
        context=ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("protection_enabled",true).putString("preventive_mode","ALWAYS").commit();
        context.getSystemService(AudioManager.class).setStreamVolume(AudioManager.STREAM_VOICE_CALL,5,0);
    }
    private static void advance(long ms){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}

    @Test public void heldCallAfterDurableReservationCannotDispatch(){
        Host host=new Host(); List<Runnable> writes=new ArrayList<>();
        CallSession session=new CallSession(context,host,writes::add);
        session.start();advance(1200);assertEquals(1,writes.size());
        host.state=Call.STATE_HOLDING;session.onEvent();advance(200);
        writes.remove(0).run();advance(1);
        assertTrue(host.requests.isEmpty());assertEquals(1,session.report().reservedRequests);
        assertEquals(0,session.report().dispatchedRequests);
        session.finish();writes.forEach(Runnable::run);
    }
    @Test public void disabledProtectionBeforeAcknowledgmentCancelsCommand(){
        Host host=new Host();List<Runnable>writes=new ArrayList<>();
        CallSession session=new CallSession(context,host,writes::add);
        session.start();advance(1200);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("protection_enabled",false).commit();
        writes.remove(0).run();advance(1);
        assertTrue(host.requests.isEmpty());session.finish();writes.forEach(Runnable::run);
    }
    @Test public void selectingSpeakerWhileStorageIsPendingAlwaysWins(){
        Host host=new Host();List<Runnable>writes=new ArrayList<>();
        CallSession session=new CallSession(context,host,writes::add);
        session.start();advance(1200);host.route=CallAudioState.ROUTE_SPEAKER;
        writes.remove(0).run();advance(1);
        assertTrue(host.requests.isEmpty());assertEquals("USER_CHOICE",session.report().stopReason);
        session.finish();writes.forEach(Runnable::run);
    }
    @Test public void changedTargetDuringReservationCannotBeSeized(){
        Host host=new Host();List<Runnable>writes=new ArrayList<>();
        CallSession session=new CallSession(context,host,writes::add);
        session.start();advance(1200);host.target="headset";
        writes.remove(0).run();advance(1);
        assertTrue(host.requests.isEmpty());session.finish();writes.forEach(Runnable::run);
    }
    @Test public void processInterruptionSuppressesAnotherPreventiveBounceUntilActualCallEnd(){
        Host host=new Host();CallSession first=new CallSession(context,host,Runnable::run);
        first.start();advance(1200);assertEquals(1,host.requests.size());first.interrupt();
        assertTrue(new RecoveryJournalRepository(context).interrupted());
        host.route=CallAudioState.ROUTE_BLUETOOTH;
        CallSession rebound=new CallSession(context,host,Runnable::run);
        rebound.start();advance(30_000);assertEquals(1,host.requests.size());
        assertEquals("INTERRUPTED",rebound.report().stopReason);
        assertFalse(rebound.report().audibleConfirmed);
        rebound.finish();assertFalse(new RecoveryJournalRepository(context).interrupted());
    }
    @Test public void oldAcknowledgmentAfterFinishCannotAffectNewSession(){
        Host host=new Host();List<Runnable>writes=new ArrayList<>();
        CallSession old=new CallSession(context,host,writes::add);old.start();advance(1200);old.finish();
        for(Runnable write:new ArrayList<>(writes))write.run();advance(1);
        assertTrue(host.requests.isEmpty());assertEquals(1,new CallReportRepository(context).history().all().size());
    }
    @Test public void disappearingAuthorityAfterLeaveStopsReturn(){
        Host host=new Host();CallSession session=new CallSession(context,host,Runnable::run);
        session.start();advance(1200);assertEquals(List.of(CallAudioState.ROUTE_EARPIECE),host.requests);
        host.safe=false;session.onEvent();advance(20_000);
        assertEquals(1,host.requests.size());assertEquals("INELIGIBLE",session.report().stopReason);session.finish();
    }
    @Test public void deadlineAndSingleRebuildRemainBoundedWhenBluetoothNeverReturns(){
        Host host=new Host();
        CallSession session=new CallSession(context,host,Runnable::run);
        session.start();advance(1200);host.follow=false;
        advance(60_000);
        assertTrue(host.requests.size()<=4);
        assertEquals(1,host.requests.stream().filter(i->i==CallAudioState.ROUTE_EARPIECE).count());
        assertEquals("DEADLINE",session.report().stopReason);
        assertEquals(host.requests.size(),session.report().dispatchedRequests);session.finish();
    }
}
