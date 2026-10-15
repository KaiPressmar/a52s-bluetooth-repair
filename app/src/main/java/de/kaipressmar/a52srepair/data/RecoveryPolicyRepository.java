package de.kaipressmar.a52srepair.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Local risk observations scoped to stable accessory identity, firmware and policy revision. */
public final class RecoveryPolicyRepository {
    public static final int VERSION = 1;
    private static final long WINDOW_MS = 7L * 24 * 60 * 60 * 1000;
    private final SharedPreferences prefs;
    public RecoveryPolicyRepository(Context context) {
        prefs=context.getSharedPreferences("recovery-policy-v1",Context.MODE_PRIVATE);
    }
    public String scope(String privateAccessoryId) {
        if (privateAccessoryId == null) return null;
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(
                    (VERSION + ";" + Build.DISPLAY + ";" + privateAccessoryId).getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for(byte b:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException unavailable) { return null; }
    }
    public boolean elevated(String scope,long now) {
        if(scope==null)return false;
        long at=prefs.getLong(scope,-1L);
        return at>=0 && now>=at && now-at<=WINDOW_MS;
    }
    public void recordProblem(String scope,long now) {
        if(scope!=null) prefs.edit().putLong(scope,now).apply();
    }
    public void clear() { prefs.edit().clear().apply(); }
}
