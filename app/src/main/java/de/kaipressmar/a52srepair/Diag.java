package de.kaipressmar.a52srepair;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Diag {
    static synchronized void log(Context c, String event) {
        try (FileWriter w = new FileWriter(new File(c.getFilesDir(), "a52s-bt-repair.log"), true)) {
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + " | " + event + "\n");
        } catch (Exception ignored) {}
    }

    static String snapshot(Context c) {
        StringBuilder s = new StringBuilder();
        AudioManager am = (AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        BluetoothManager bm = (BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ba = bm == null ? null : bm.getAdapter();
        s.append("device=").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
         .append(" sdk=").append(Build.VERSION.SDK_INT).append(" build=").append(Build.DISPLAY).append('\n');
        try { s.append("bluetoothEnabled=").append(ba != null && ba.isEnabled()).append('\n'); }
        catch (SecurityException e) { s.append("bluetoothEnabled=permission-denied\n"); }
        s.append("audioMode=").append(am.getMode()).append(" speaker=").append(am.isSpeakerphoneOn())
         .append(" musicActive=").append(am.isMusicActive()).append('\n');
        AudioDeviceInfo comm = am.getCommunicationDevice();
        s.append("communicationDevice=").append(comm == null ? "none" : device(comm)).append('\n');
        s.append("outputs:\n");
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) s.append(" - ").append(device(d)).append('\n');
        return s.toString();
    }

    static String device(AudioDeviceInfo d) {
        return "type=" + d.getType() + " id=" + d.getId() + " name=" + d.getProductName();
    }
}
