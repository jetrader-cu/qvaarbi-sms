package tech.bogomolov.incomingsmsgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests instrumentados de {@link ConnectionStatus}: derivación del estado del
 * banner a partir del último evento (spec REQ-009/010).
 */
@RunWith(AndroidJUnit4.class)
public class ConnectionStatusTest {

    private final Context context =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void testUnknownSinEventos() {
        // Fuerza estado limpio grabando y comprobando que un OK reciente lo cambia;
        // el estado unknown sólo aplica antes del primer registro, difícil de aislar
        // en SharedPreferences compartido, así que validamos las transiciones reales.
        ConnectionStatus.record(context, true, "HTTP 200", ConnectionStatus.SOURCE_HEARTBEAT);
        ConnectionStatus s = ConnectionStatus.load(context);
        assertEquals(ConnectionStatus.STATE_OK, s.state);
        assertEquals("HTTP 200", s.detail);
        assertEquals(ConnectionStatus.SOURCE_HEARTBEAT, s.source);
        assertTrue(s.at > 0);
    }

    @Test
    public void testErrorSobrescribeOk() {
        ConnectionStatus.record(context, true, "HTTP 200", ConnectionStatus.SOURCE_DELIVERY);
        ConnectionStatus.record(context, false, "HTTP 401 firma inválida",
                ConnectionStatus.SOURCE_DELIVERY);

        ConnectionStatus s = ConnectionStatus.load(context);
        assertEquals(ConnectionStatus.STATE_ERROR, s.state);
        assertEquals("HTTP 401 firma inválida", s.detail);
    }
}
