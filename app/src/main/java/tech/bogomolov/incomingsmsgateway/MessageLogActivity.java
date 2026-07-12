package tech.bogomolov.incomingsmsgateway;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Pantalla "Registro" (spec REQ-004): historial persistente de cada SMS reenviado a
 * QvaArbi con su estado, agrupado por regla. Reemplaza el antiguo volcado de
 * {@code logcat} de MainActivity —que además crasheaba sin permiso SMS (BUG-001)—
 * por un historial de negocio real leído de {@link MessageLog}.
 *
 * <p>No requiere el permiso RECEIVE_SMS ni el servicio en marcha: sólo lee un
 * SharedPreferences, así que abre siempre (AC-001).
 */
public class MessageLogActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_log);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.menu_syslog);
        }

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Los estados cambian en segundo plano (entregas/reintentos), así que
        // recargar al volver al frente mantiene el registro al día.
        render();
    }

    private void render() {
        List<MessageLog.Entry> entries = MessageLog.getAll(this);

        ListView list = findViewById(R.id.log_list);
        TextView empty = findViewById(R.id.log_empty);

        if (entries.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
        } else {
            empty.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            list.setAdapter(new MessageLogAdapter(this, entries));
        }
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.message_log_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Sin nada que limpiar, oculta la papelera.
        MenuItem clear = menu.findItem(R.id.action_log_clear);
        if (clear != null) {
            clear.setVisible(MessageLog.getCount(this) > 0);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == R.id.action_log_clear) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.log_clear_title)
                    .setMessage(R.string.log_clear_message)
                    .setPositiveButton(R.string.btn_clear, (dialog, which) -> {
                        MessageLog.clear(this);
                        render();
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
