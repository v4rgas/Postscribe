package me.pompel.elauncher;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

public class UploadService extends Service {
    private static final String TAG = "UploadService";
    public static final int DEFAULT_PORT = 8080;
    public static final String EXTRA_PORT = "port";
    public static final String ACTION_RESTART = "me.pompel.elauncher.RESTART";

    private static volatile UploadServer server;
    private static volatile int runningPort = -1;

    public static boolean isRunning() { return server != null; }
    public static int runningPort() { return runningPort; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        int port = DEFAULT_PORT;
        String portStr = prefs.getString("upload_server_port", String.valueOf(DEFAULT_PORT));
        try { port = Integer.parseInt(portStr.trim()); } catch (NumberFormatException ignored) {}
        if (intent != null && intent.hasExtra(EXTRA_PORT)) port = intent.getIntExtra(EXTRA_PORT, port);
        boolean restart = intent != null && ACTION_RESTART.equals(intent.getAction());
        if (restart && server != null) {
            server.stop();
            server = null;
            runningPort = -1;
        }
        if (server == null) {
            String password = prefs.getString("upload_server_password", "");
            String rootPath = prefs.getString("upload_server_root", "/sdcard");
            UploadServer s = new UploadServer(this, port, rootPath, password);
            try {
                s.start();
                server = s;
                runningPort = port;
                Log.i(TAG, "upload server listening on " + port);
            } catch (IOException e) {
                Log.e(TAG, "failed to start on port " + port, e);
                server = null;
                runningPort = -1;
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
            runningPort = -1;
            Log.i(TAG, "upload server stopped");
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
