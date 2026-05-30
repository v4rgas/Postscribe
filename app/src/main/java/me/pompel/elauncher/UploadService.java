package me.pompel.elauncher;

import android.app.Service;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

public class UploadService extends Service {
    private static final String TAG = "UploadService";
    public static final int DEFAULT_PORT = 8080;
    public static final String EXTRA_PORT = "port";
    public static final String ACTION_RESTART = "me.pompel.elauncher.RESTART";
    public static final String ACTION_STATE_CHANGED = "me.pompel.elauncher.SERVER_STATE_CHANGED";
    public static final String MDNS_NAME = "postscribe";
    public static final String MDNS_TYPE = "_http._tcp.";

    private static volatile UploadServer server;
    private static volatile int runningPort = -1;
    private static volatile String mdnsHostname;

    private NsdManager nsd;
    private NsdManager.RegistrationListener mdnsListener;

    public static boolean isRunning() { return server != null; }
    public static int runningPort() { return runningPort; }
    /** The advertised mDNS hostname (e.g. "postscribe.local"), or null if not registered. */
    public static String mdnsHostname() { return mdnsHostname; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        int port = DEFAULT_PORT;
        String portStr = prefs.getString("upload_server_port", String.valueOf(DEFAULT_PORT));
        try { port = Integer.parseInt(portStr.trim()); } catch (NumberFormatException ignored) {}
        if (intent != null && intent.hasExtra(EXTRA_PORT)) port = intent.getIntExtra(EXTRA_PORT, port);
        boolean restart = intent != null && ACTION_RESTART.equals(intent.getAction());
        if (restart && server != null) {
            unregisterMdns();
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
                registerMdns(port);
                sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName()));
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
        unregisterMdns();
        if (server != null) {
            server.stop();
            server = null;
            runningPort = -1;
            Log.i(TAG, "upload server stopped");
            sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName()));
        }
        super.onDestroy();
    }

    private void registerMdns(int port) {
        try {
            nsd = (NsdManager) getSystemService(NSD_SERVICE);
            if (nsd == null) return;
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName(MDNS_NAME);
            info.setServiceType(MDNS_TYPE);
            info.setPort(port);
            mdnsListener = new NsdManager.RegistrationListener() {
                @Override public void onServiceRegistered(NsdServiceInfo registered) {
                    String name = registered.getServiceName();
                    mdnsHostname = name + ".local";
                    Log.i(TAG, "mDNS registered as " + name);
                }
                @Override public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                    Log.w(TAG, "mDNS registration failed: " + errorCode);
                    mdnsHostname = null;
                }
                @Override public void onServiceUnregistered(NsdServiceInfo info) {
                    Log.i(TAG, "mDNS unregistered");
                    mdnsHostname = null;
                }
                @Override public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {
                    Log.w(TAG, "mDNS unregistration failed: " + errorCode);
                }
            };
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, mdnsListener);
        } catch (Exception e) {
            Log.w(TAG, "mDNS unavailable", e);
            mdnsHostname = null;
        }
    }

    private void unregisterMdns() {
        if (nsd != null && mdnsListener != null) {
            try { nsd.unregisterService(mdnsListener); } catch (Exception ignored) {}
        }
        mdnsListener = null;
        mdnsHostname = null;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
