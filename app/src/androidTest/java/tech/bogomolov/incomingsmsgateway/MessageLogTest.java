package tech.bogomolov.incomingsmsgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests instrumentados de {@link MessageLog}: registrar → actualizar el ciclo de
 * estado, la cota de tamaño y el clear. Necesitan un SharedPreferences real, así
 * que corren en dispositivo/emulador (spec §6).
 */
@RunWith(AndroidJUnit4.class)
public class MessageLogTest {

    private final Context context =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setup() {
        MessageLog.clear(context);
    }

    @After
    public void tearDown() {
        MessageLog.clear(context);
    }

    @Test
    public void testRecordCreaEntradaPending() {
        String logKey = MessageLog.record(context, "rule-1", "PAGOxMOVIL", "PAGOxMOVIL", "hola");
        assertNotNull(logKey);
        assertEquals(1, MessageLog.getCount(context));

        MessageLog.Entry entry = MessageLog.getAll(context).get(0);
        assertEquals(MessageLog.STATUS_PENDING, entry.status);
        assertEquals("PAGOxMOVIL", entry.ruleName);
        assertEquals("hola", entry.body);
        assertEquals(-1, entry.httpCode);
    }

    @Test
    public void testUpdateActualizaEstadoYHttp() {
        String logKey = MessageLog.record(context, "rule-1", "ENZONA", "ENZONA", "pago");
        MessageLog.update(context, logKey, MessageLog.STATUS_SENT, 200, 1);

        MessageLog.Entry entry = MessageLog.getAll(context).get(0);
        assertEquals(MessageLog.STATUS_SENT, entry.status);
        assertEquals(200, entry.httpCode);
        assertEquals(1, entry.attempts);
    }

    @Test
    public void testUpdateLogKeyInexistenteNoOp() {
        // No debe crashear ni crear entradas fantasma.
        MessageLog.update(context, "no-existe", MessageLog.STATUS_FAILED, 500, 3);
        assertEquals(0, MessageLog.getCount(context));
    }

    @Test
    public void testOrdenMasRecientePrimero() throws InterruptedException {
        MessageLog.record(context, "r", "A", "A", "primero");
        Thread.sleep(5);
        MessageLog.record(context, "r", "B", "B", "segundo");

        List<MessageLog.Entry> all = MessageLog.getAll(context);
        assertEquals("segundo", all.get(0).body);
        assertEquals("primero", all.get(1).body);
    }

    @Test
    public void testTrimCapAlMaximo() {
        for (int i = 0; i < MessageLog.MAX_STORED + 5; i++) {
            MessageLog.record(context, "r", "A", "A", "m" + i);
        }
        assertEquals(MessageLog.MAX_STORED, MessageLog.getCount(context));
    }
}
