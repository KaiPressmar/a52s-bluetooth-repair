package de.kaipressmar.a52srepair.audio;

import static org.junit.Assert.*;
import android.media.AudioDeviceInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 36})
public class VoiceStreamTest {
    @Test public void leAudioDoesNotRequireClassicSco() {
        VoiceStream.Evidence e = VoiceStream.evidenceForTypes(AudioDeviceInfo.TYPE_BLE_HEADSET);
        assertEquals(Boolean.TRUE, e.onBluetooth);
        assertFalse(e.requiresSco);
    }
    @Test public void classicOrMixedBluetoothStillChecksSco() {
        VoiceStream.Evidence classic = VoiceStream.evidenceForTypes(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        assertEquals(Boolean.TRUE, classic.onBluetooth);
        assertTrue(classic.requiresSco);
        assertTrue(VoiceStream.evidenceForTypes(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET).requiresSco);
    }
    @Test public void MediaBluetoothIsNotVoiceTransportAndEmptyEvidenceIsUnknown() {
        assertEquals(Boolean.FALSE, VoiceStream.evidenceForTypes(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP).onBluetooth);
        assertEquals(null, VoiceStream.evidenceForTypes().onBluetooth);
    }
}
