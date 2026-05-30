package me.pompel.elauncher;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        gestureDetector = new GestureDetector(this, new GestureListener());
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            assert e1 != null;
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffY) > Math.abs(diffX)) {
                if (diffY < 0 && Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    restartApplication();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent e) {
            restartApplication();
        }
    }

    @Override
    public void onBackPressed() {
        restartApplication();
    }

    private void restartApplication() {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity(); // Close all activities
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // Initialize dark mode switch to show current system state if not explicitly set
            androidx.preference.SwitchPreferenceCompat darkModePreference =
                findPreference("dark_mode_preference");
            if (darkModePreference != null) {
                android.content.SharedPreferences prefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());

                // Only set the visual state if the preference hasn't been explicitly set
                if (!prefs.contains("dark_mode_preference")) {
                    boolean systemDarkMode = (getResources().getConfiguration().uiMode &
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES;
                    darkModePreference.setChecked(systemDarkMode);
                }
            }

            wireDeveloperSection();
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshDeveloperSummaries();
        }

        private void wireDeveloperSection() {
            final android.content.Context ctx = requireContext().getApplicationContext();
            final androidx.preference.SwitchPreferenceCompat uploadSwitch = findPreference("upload_server_enabled");
            if (uploadSwitch != null) {
                uploadSwitch.setChecked(UploadService.isRunning());
                uploadSwitch.setSummary(uploadSummary(ctx));
                uploadSwitch.setOnPreferenceChangeListener(new androidx.preference.Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(androidx.preference.Preference preference, Object newValue) {
                        boolean enable = Boolean.TRUE.equals(newValue);
                        android.content.Intent intent = new android.content.Intent(ctx, UploadService.class);
                        if (enable) {
                            ctx.startService(intent);
                        } else {
                            ctx.stopService(intent);
                        }
                        uploadSwitch.setSummary(uploadSummary(ctx));
                        return true;
                    }
                });
            }

            // restart the server when the password or port changes
            androidx.preference.Preference.OnPreferenceChangeListener restartIfRunning =
                new androidx.preference.Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(androidx.preference.Preference preference, Object newValue) {
                        if (UploadService.isRunning()) {
                            android.content.Intent intent = new android.content.Intent(ctx, UploadService.class);
                            intent.setAction(UploadService.ACTION_RESTART);
                            ctx.startService(intent);
                        }
                        return true;
                    }
                };
            androidx.preference.EditTextPreference passwordPref = findPreference("upload_server_password");
            if (passwordPref != null) passwordPref.setOnPreferenceChangeListener(restartIfRunning);
            androidx.preference.EditTextPreference portPref = findPreference("upload_server_port");
            if (portPref != null) portPref.setOnPreferenceChangeListener(restartIfRunning);
            androidx.preference.EditTextPreference rootPref = findPreference("upload_server_root");
            if (rootPref != null) rootPref.setOnPreferenceChangeListener(restartIfRunning);
        }

        private String uploadSummary(android.content.Context ctx) {
            if (!UploadService.isRunning()) return "Off";
            String ip = DevInfo.wifiIp(ctx);
            int port = UploadService.runningPort();
            return "On — http://" + ip + ":" + port;
        }

        private void refreshDeveloperSummaries() {
            android.content.Context ctx = requireContext().getApplicationContext();
            setSummary("dev_wifi_ip", DevInfo.wifiIp(ctx));
            setSummary("dev_all_ips", DevInfo.allIps());
            setSummary("dev_android", DevInfo.androidVersion());
            setSummary("dev_model", DevInfo.deviceModel());
            setSummary("dev_fingerprint", DevInfo.buildFingerprint());
            setSummary("dev_storage", DevInfo.storageFree());

            androidx.preference.SwitchPreferenceCompat uploadSwitch = findPreference("upload_server_enabled");
            if (uploadSwitch != null) {
                uploadSwitch.setChecked(UploadService.isRunning());
                uploadSwitch.setSummary(uploadSummary(ctx));
            }
        }

        private void setSummary(String key, String value) {
            androidx.preference.Preference p = findPreference(key);
            if (p != null) p.setSummary(value);
        }
    }
}