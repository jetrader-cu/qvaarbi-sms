package tech.bogomolov.incomingsmsgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests instrumentados de {@link WebhookGlobal}: persistencia y la resolución
 * per-regla vs global (spec REQ-007/008). SharedPreferences real → emulador.
 */
@RunWith(AndroidJUnit4.class)
public class WebhookGlobalTest {

    private final Context context =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @After
    public void tearDown() {
        new WebhookGlobal("", "").save(context);
    }

    @Test
    public void testSaveLoadRoundTrip() {
        new WebhookGlobal("https://q/api/v1/webhooks/sms/tok", "sec").save(context);

        WebhookGlobal loaded = WebhookGlobal.load(context);
        assertEquals("https://q/api/v1/webhooks/sms/tok", loaded.getUrl());
        assertEquals("sec", loaded.getSecret());
        assertTrue(loaded.isConfigured());
    }

    @Test
    public void testResolveUsaGlobalCuandoReglaVacia() {
        new WebhookGlobal("https://global/hook", "gsecret").save(context);

        assertEquals("https://global/hook", WebhookGlobal.resolveUrl(context, ""));
        assertEquals("https://global/hook", WebhookGlobal.resolveUrl(context, null));
        assertEquals("gsecret", WebhookGlobal.resolveSecret(context, ""));
    }

    @Test
    public void testReglaOverrideGanaSobreGlobal() {
        new WebhookGlobal("https://global/hook", "gsecret").save(context);

        assertEquals("https://rule/hook", WebhookGlobal.resolveUrl(context, "https://rule/hook"));
        assertEquals("rsecret", WebhookGlobal.resolveSecret(context, "rsecret"));
    }

    @Test
    public void testNoConfiguradoPorDefecto() {
        new WebhookGlobal("", "").save(context);
        assertFalse(WebhookGlobal.load(context).isConfigured());
    }
}
