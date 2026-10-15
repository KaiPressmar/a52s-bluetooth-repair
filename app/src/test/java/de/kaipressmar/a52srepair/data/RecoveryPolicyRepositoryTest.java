package de.kaipressmar.a52srepair.data;
import static org.junit.Assert.*;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class RecoveryPolicyRepositoryTest {
    @Test public void preventionRiskCannotTransferBetweenAccessoriesOrFirmware() {
        RecoveryPolicyRepository p=new RecoveryPolicyRepository(ApplicationProvider.getApplicationContext());
        String car=p.scope("private-car"),headset=p.scope("private-headset");
        assertNotEquals(car,headset);assertFalse(car.contains("private"));
        assertNull(p.scope(null));assertFalse(p.elevated(null,100));
        p.recordProblem(car,100);assertTrue(p.elevated(car,100));
        assertFalse(p.elevated(headset,100));assertFalse(p.elevated(car,99));
        assertFalse(p.elevated(car,8L*24*60*60*1000));
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"DISPLAY","other-firmware");
        assertNotEquals(car,p.scope("private-car"));
        p.recordProblem(null,200);p.clear();assertFalse(p.elevated(car,200));
    }
}
