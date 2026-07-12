package tech.bogomolov.incomingsmsgateway;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private Context context;
    private ListAdapter listAdapter;
    // El chequeo de update corre una vez por proceso (ver maybeCheckUpdate).
    private boolean updateChecked = false;

    private static final int PERMISSION_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Siembra las 3 reglas bancarias predefinidas la primera vez (REQ-001/005).
        // Corre antes de la comprobación de permisos, así las reglas existen aunque
        // el usuario aún no haya concedido el permiso SMS.
        TemplateSeeder.seedIfNeeded(this);

        ArrayList<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECEIVE_SMS);
        }
        // Android 13+ gates the foreground-service "F" indicator behind a runtime
        // permission. Without it the service still runs and forwards SMS, but the
        // persistent notification never shows (issue #77). Treated as best-effort:
        // we ask for it, but its denial does not block the app like RECEIVE_SMS does.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (permissions.isEmpty()) {
            showList();
        } else {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_CODE);
        }

        // Programa el chequeo diario de actualizaciones en background (spec REQ-008).
        // Idempotente; el propio worker respeta el toggle de auto-update.
        UpdateCheckWorker.schedule(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PERMISSION_CODE) {
            return;
        }
        for (int i = 0; i < permissions.length; i++) {
            if (!permissions[i].equals(Manifest.permission.RECEIVE_SMS)) {
                continue;
            }

            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                showList();
            } else {
                showInfo(getResources().getString(R.string.permission_needed));
            }

            return;
        }

        // RECEIVE_SMS wasn't part of this result (it was already granted, and only
        // the best-effort POST_NOTIFICATIONS was requested), so proceed as long as
        // RECEIVE_SMS is in fact still granted. Re-checking also handles the
        // empty-array case Android delivers when the dialog is cancelled.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            showList();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Failures accrue in the background, so refresh the retry counter each
        // time the activity comes forward.
        invalidateOptionsMenu();
        // Rules can also change outside this screen (Settings -> import a backup),
        // so reload the list. Null until the permission flow has let showList() run.
        if (listAdapter != null) {
            listAdapter.clear();
            listAdapter.addAll(ForwardingConfig.getAll(this));
        }
        // El heartbeat y las entregas actualizan el estado en segundo plano, así que
        // refresca el banner cada vez que la pantalla vuelve al frente (spec REQ-009).
        updateStatusBanner();
        // Chequeo de actualización al abrir (spec REQ-007/008), una vez por sesión.
        maybeCheckUpdate();
    }

    // Consulta el manifest en background (si el toggle está ON) y, si hay versión
    // nueva, ofrece actualizar. Se ejecuta una sola vez por proceso para no repetir
    // el diálogo en cada onResume.
    private void maybeCheckUpdate() {
        if (updateChecked || !UpdateSettings.isAutoCheckEnabled(this)) {
            return;
        }
        updateChecked = true;
        new Thread(() -> {
            UpdateManifest manifest = UpdateChecker.fetch(this);
            if (UpdateChecker.hasUpdate(manifest)) {
                runOnUiThread(() -> showUpdateDialog(manifest));
            }
        }).start();
    }

    // Diálogo de actualización: "Actualizar" abre el APK en el navegador (REQ-010),
    // sin permisos de instalación.
    private void showUpdateDialog(UpdateManifest manifest) {
        if (isFinishing()) {
            return;
        }
        String message = manifest.notes == null || manifest.notes.isEmpty()
                ? getString(R.string.update_available_message_short, manifest.versionName)
                : getString(R.string.update_available_message, manifest.versionName, manifest.notes);
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_update_now,
                        (d, w) -> openUrl(manifest.apkUrl))
                .setNegativeButton(R.string.btn_update_later, null)
                .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e("SmsGateway", "open url failed: " + e);
        }
    }

    // Pinta el banner de conexión con QvaArbi a partir del último evento conocido
    // (heartbeat o entrega). Verde = OK, rojo = fallo con motivo, oculto si aún no
    // hay actividad — no queremos alarmar antes del primer ping.
    private void updateStatusBanner() {
        TextView banner = findViewById(R.id.status_banner);
        if (banner == null) {
            return;
        }
        ConnectionStatus status = ConnectionStatus.load(this);
        if (ConnectionStatus.STATE_UNKNOWN.equals(status.state)) {
            banner.setVisibility(View.GONE);
            return;
        }

        CharSequence when = DateUtils.getRelativeTimeSpanString(status.at);
        String text;
        int color;
        if (ConnectionStatus.STATE_OK.equals(status.state)) {
            text = getString(R.string.status_connected, when);
            color = ContextCompat.getColor(this, R.color.colorPrimary);
        } else {
            text = getString(R.string.status_error, status.detail, when);
            color = ContextCompat.getColor(this, R.color.colorDanger);
        }
        banner.setText(text);
        banner.setBackgroundColor(color);
        banner.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem retryItem = menu.findItem(R.id.action_bar_retry_failed);
        int count = FailedMessage.getCount(this);
        retryItem.setVisible(count > 0);
        if (count > 0) {
            retryItem.setTitle(getString(R.string.menu_retry_failed, count));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_bar_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.action_bar_retry_failed) {
            int count = FailedMessage.getCount(this);
            FailedMessage.retryAll(this);
            Toast.makeText(this, getString(R.string.retry_failed_toast, count), Toast.LENGTH_LONG).show();
            invalidateOptionsMenu();
            return true;
        }

        if (id == R.id.action_bar_syslogs) {
            // El "Registro" es ahora el historial persistente de mensajes reenviados
            // (MessageLogActivity), no un volcado de logcat. Lanzarlo por Intent evita
            // el NPE del antiguo AlertDialog.Builder(context): `context` sólo se asigna
            // en showList(), que no corre hasta conceder el permiso SMS — abrir el
            // Registro sin permiso crasheaba (BUG-001 / AC-001).
            startActivity(new Intent(this, MessageLogActivity.class));
            return true;
        }

        if (id == R.id.action_bar_help) {
            startActivity(new Intent(this, HelpActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showList() {
        context = this;
        ListView listview = findViewById(R.id.listView);

        ArrayList<ForwardingConfig> configs = ForwardingConfig.getAll(context);

        // First-run / empty state: point the user at the + button instead of
        // leaving a blank screen.
        showInfo(configs.isEmpty() ? getString(R.string.empty_list_hint) : "");

        listAdapter = new ListAdapter(configs, context);
        listview.setAdapter(listAdapter);

        // Keep the empty-state hint in sync as rules are added or deleted.
        listAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                showInfo(listAdapter.getCount() == 0 ? getString(R.string.empty_list_hint) : "");
            }
        });

        FloatingActionButton fab = findViewById(R.id.btn_add);
        fab.setOnClickListener(this.showAddDialog());

        if (!this.isServiceRunning()) {
            this.startService();
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (tech.bogomolov.incomingsmsgateway.SmsReceiverService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void startService() {
        Context appContext = getApplicationContext();
        Intent intent = new Intent(this, SmsReceiverService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }
    }

    private void showInfo(String text) {
        TextView notice = findViewById(R.id.info_notice);
        notice.setText(text);
        // The icon above the notice should disappear together with the text.
        findViewById(R.id.empty_state).setVisibility(
                text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private View.OnClickListener showAddDialog() {
        return v -> {
            (new ForwardingConfigDialog(context, getLayoutInflater(), listAdapter)).showNew();
        };
    }
}
