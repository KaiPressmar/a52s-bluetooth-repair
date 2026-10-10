package de.kaipressmar.a52srepair.diagnostics;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class NativeServiceInventoryTest {
    @Test public void readsOnlyRelevantDeclarationsAndNeverExportsPropertiesOrArguments() throws Exception {
        File dir=Files.createTempDirectory("native-inventory").toFile();
        Files.writeString(new File(dir,"audioserver.rc").toPath(),"service audioserver /system/bin/audioserver\n    setenv PRIVATE secret\n    onrestart restart vendor.audio-hal\nservice unrelated /vendor/bin/private\n");
        Files.writeString(new File(dir,"bluetooth.rc").toPath(),"service vendor.bluetooth-1-0-qti /vendor/bin/hw/android.hardware.bluetooth@1.0-service-qti --private secret\n");
        Files.writeString(new File(dir,"radio.rc").toPath(),"service audio-guess /vendor/bin/private\n");
        String result=NativeServiceInventory.capture(new File[]{dir});
        assertTrue(result.contains("service=audioserver"));assertTrue(result.contains("service=vendor.bluetooth-1-0-qti"));
        assertTrue(result.contains("declaredOnly=true restartPrivilege=false"));assertTrue(result.contains("filesExamined=2"));
        assertFalse(result.contains("secret"));assertFalse(result.contains("audio-guess"));assertFalse(result.contains("unrelated"));
        assertTrue(new File(dir,"audio-unreadable.rc").mkdir());
        assertTrue(NativeServiceInventory.capture(new File[]{dir}).contains("initFile=unavailable"));
    }
    @Test public void unreadableInventoryIsExplicitAndMalformedCommandsAreNotInterpreted() {
        assertTrue(NativeServiceInventory.capture(new File[]{new File("/missing/service-dir")}).contains("unavailable"));
        assertEquals("",NativeServiceInventory.declarations(" # service audioserver /system/bin/audioserver\nservice audio;injected /system/bin/audio\nservice audio relative\nservice camera /vendor/bin/camera\n"));
    }
    @Test public void fileCountAndByteLimitsBoundExport() throws Exception {
        File dir=Files.createTempDirectory("native-limit").toFile();
        for(int i=0;i<30;i++)Files.writeString(new File(dir,"audio"+i+".rc").toPath(),"service audioserver /system/bin/audioserver\n");
        assertTrue(NativeServiceInventory.capture(new File[]{dir}).contains("inventoryLimitReached=true"));
        File big=Files.createTempDirectory("native-bytes").toFile();
        for(int i=0;i<5;i++)Files.writeString(new File(big,"audio"+i+".rc").toPath(),"#".repeat(40000));
        assertTrue(NativeServiceInventory.capture(new File[]{big}).contains("inventoryLimitReached=true"));
    }
    @Test public void declarationOutputIsBoundedEvenForHostileRepeatedInput() {
        assertTrue(NativeServiceInventory.declarations("service audioserver /system/bin/audioserver\n".repeat(1000)).length()<4200);
    }
}
