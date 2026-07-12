package tech.bogomolov.incomingsmsgateway;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Pantalla Ayuda (spec REQ-011): muestra la versión instalada y accesos directos a
 * QvaArbi (bot de Telegram, canal, correo, sitio web). Los enlaces son constantes de
 * la app (espejo de frontend/lib/content/help.ts), no requieren red.
 */
public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.menu_help);
        }

        TextView version = findViewById(R.id.help_version);
        version.setText(getString(R.string.help_version_label)
                + ": " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        bindLink(R.id.help_bot, R.string.qvaarbi_telegram_bot);
        bindLink(R.id.help_channel, R.string.qvaarbi_telegram_channel);
        bindLink(R.id.help_email, R.string.qvaarbi_support_email); // mailto se arma abajo
        bindLink(R.id.help_website, R.string.qvaarbi_website);
    }

    private void bindLink(int viewId, int urlRes) {
        findViewById(viewId).setOnClickListener(v -> {
            String value = getString(urlRes);
            String uri = value.contains("@") && !value.startsWith("http")
                    ? "mailto:" + value
                    : value;
            openUrl(uri);
        });
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e("SmsGateway", "help open url failed: " + e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
