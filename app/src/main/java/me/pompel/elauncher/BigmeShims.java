package me.pompel.elauncher;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

/* BIGME shim — neutered on KitKat fork. The Bigme HiBreak ContentProvider hooks
 * only matter on Android Q+ and the HiBreak device. This is a no-op build for
 * old e-readers.
 */
public class BigmeShims {
    private static final String ELAUNCHER_TAG = "eLauncher";

    public static void queryLauncherProvider(@NonNull Context context) {
        // No-op on KitKat: feature requires API 29+ and Bigme HiBreak hardware.
        Log.d(ELAUNCHER_TAG, "queryLauncherProvider: skipped (KitKat build)");
    }

    public static void registerUnlockReceiver(@NonNull Context context) {
        if (!"HiBreak".equals(Build.MODEL))
            return;

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.HIDE_BAKCLOGO");
        filter.addAction("android.intent.action.SHOW_BACKLOGO");
        UnlockReceiver unlockReceiver = new UnlockReceiver();
        context.registerReceiver(unlockReceiver, filter);
    }
}
