package tech.bogomolov.incomingsmsgateway;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Global app settings. Currently hosts the heartbeat / app-alive monitor
 * (issue #31); the heartbeat itself runs in {@link SmsReceiverService}.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        HeartbeatSettings settings = HeartbeatSettings.load(this);

        final CheckBox enabledCheckbox = findViewById(R.id.input_heartbeat_enabled);
        enabledCheckbox.setChecked(settings.isEnabled());

        final EditText urlInput = findViewById(R.id.input_heartbeat_url);
        urlInput.setText(settings.getUrl());

        final EditText intervalInput = findViewById(R.id.input_heartbeat_interval);
        intervalInput.setText(String.valueOf(settings.getIntervalMinutes()));

        Button saveButton = findViewById(R.id.btn_heartbeat_save);
        saveButton.setOnClickListener(v -> {
            HeartbeatSettings updated = readSettings();
            if (updated == null) {
                return;
            }
            updated.save(this);
            applyHeartbeat(updated);
            Toast.makeText(this, R.string.heartbeat_saved_toast, Toast.LENGTH_SHORT).show();
            finish();
        });

        Button testButton = findViewById(R.id.btn_heartbeat_test);
        testButton.setOnClickListener(v -> testHeartbeat());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Validates the form and returns the entered settings, or null (with an inline
    // error set) when invalid. URL/interval are only enforced when the heartbeat is
    // enabled, so a user can clear the toggle without filling a valid URL first.
    private HeartbeatSettings readSettings() {
        final CheckBox enabledCheckbox = findViewById(R.id.input_heartbeat_enabled);
        boolean enabled = enabledCheckbox.isChecked();

        final EditText urlInput = findViewById(R.id.input_heartbeat_url);
        String url = urlInput.getText().toString().trim();

        final EditText intervalInput = findViewById(R.id.input_heartbeat_interval);
        String intervalText = intervalInput.getText().toString().trim();
        int interval = intervalText.isEmpty() ? 0 : Integer.parseInt(intervalText);

        if (enabled) {
            if (TextUtils.isEmpty(url)) {
                urlInput.setError(getString(R.string.error_empty_url));
                return null;
            }
            try {
                new URL(url);
            } catch (MalformedURLException e) {
                urlInput.setError(getString(R.string.error_wrong_url));
                return null;
            }
            if (interval < HeartbeatSettings.MIN_INTERVAL_MINUTES) {
                intervalInput.setError(getString(R.string.error_wrong_interval));
                return null;
            }
        } else if (interval < HeartbeatSettings.MIN_INTERVAL_MINUTES) {
            interval = HeartbeatSettings.DEFAULT_INTERVAL_MINUTES;
        }

        return new HeartbeatSettings(enabled, url, interval);
    }

    // Pushes the new settings to the service. Starting it with the reschedule action
    // both (re)schedules a running service and starts one if needed (e.g. heartbeat
    // turned on while no forwarding rules exist). When disabling with no service
    // running, there is nothing to do — don't spin one up just to stop the ping.
    private void applyHeartbeat(HeartbeatSettings settings) {
        if (!settings.isEnabled() && !isServiceRunning()) {
            return;
        }

        Intent intent = new Intent(this, SmsReceiverService.class);
        intent.setAction(SmsReceiverService.ACTION_RESCHEDULE_HEARTBEAT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(intent);
        } else {
            getApplicationContext().startService(intent);
        }
    }

    private void testHeartbeat() {
        final EditText urlInput = findViewById(R.id.input_heartbeat_url);
        String url = urlInput.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            urlInput.setError(getString(R.string.error_empty_url));
            return;
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            urlInput.setError(getString(R.string.error_wrong_url));
            return;
        }

        new Thread(() -> {
            Request request = new Request(url, "");
            request.setUseChunkedMode(false);
            String result = request.execute();
            runOnUiThread(() ->
                    Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show());
        }).start();
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SmsReceiverService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
