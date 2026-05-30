package me.pompel.elauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.preference.PreferenceManager;

/**
 * Keeps the upload server in sync with the user's preference across reboots
 * and Wi-Fi changes.
 *
 * - On BOOT_COMPLETED: if the user left the server enabled, start it (only
 *   when we already have Wi-Fi, since binding to 0.0.0.0 still works without
 *   Wi-Fi but the published URL would be useless).
 * - On CONNECTIVITY_CHANGE: start when Wi-Fi connects, stop when it drops.
 *   The preference itself stays on, so the server resumes automatically the
 *   next time Wi-Fi comes back.
 */
public class UploadAutostartReceiver extends BroadcastReceiver {
    private static final String TAG = "UploadAutostart";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean("upload_server_enabled", false)) {
            // user explicitly disabled it. make sure nothing's running.
            if (UploadService.isRunning()) {
                context.stopService(new Intent(context, UploadService.class));
            }
            return;
        }

        boolean wifi = isWifiConnected(context);
        if (wifi && !UploadService.isRunning()) {
            Log.i(TAG, "starting upload server (action=" + intent.getAction() + ")");
            context.startService(new Intent(context, UploadService.class));
        } else if (!wifi && UploadService.isRunning()) {
            Log.i(TAG, "stopping upload server: wifi gone");
            context.stopService(new Intent(context, UploadService.class));
        }
    }

    private boolean isWifiConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Exception e) {
            return false;
        }
    }
}
