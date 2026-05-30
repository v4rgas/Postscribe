package me.pompel.elauncher;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

public class DevInfo {

    public static String wifiIp(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "(no wifi service)";
            WifiInfo info = wm.getConnectionInfo();
            int ip = info != null ? info.getIpAddress() : 0;
            if (ip == 0) return "(not connected)";
            return String.format("%d.%d.%d.%d", ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
        } catch (Exception e) {
            return "(error: " + e.getMessage() + ")";
        }
    }

    public static String allIps() {
        StringBuilder sb = new StringBuilder();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(ifaces)) {
                if (iface.isLoopback() || !iface.isUp()) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr.isLoopbackAddress()) continue;
                    String host = addr.getHostAddress();
                    if (host == null) continue;
                    int pct = host.indexOf('%');
                    if (pct >= 0) host = host.substring(0, pct);
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(iface.getName()).append(": ").append(host);
                }
            }
        } catch (Exception e) {
            return "(error: " + e.getMessage() + ")";
        }
        return sb.length() == 0 ? "(no interfaces)" : sb.toString();
    }

    public static String androidVersion() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    public static String deviceModel() {
        return Build.MANUFACTURER + " " + Build.MODEL + " / " + Build.DEVICE;
    }

    public static String buildFingerprint() {
        return Build.FINGERPRINT;
    }

    public static String storageFree() {
        try {
            StatFs s = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long free = (long) s.getAvailableBlocks() * s.getBlockSize();
            long total = (long) s.getBlockCount() * s.getBlockSize();
            return human(free) + " free / " + human(total) + " total";
        } catch (Exception e) {
            return "(error: " + e.getMessage() + ")";
        }
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = -1;
        do { v /= 1024.0; i++; } while (v >= 1024 && i < u.length - 1);
        return String.format("%.1f %s", v, u[i]);
    }
}
