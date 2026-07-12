package tech.bogomolov.incomingsmsgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Tests instrumentados para {@link TemplateSeeder} (spec §6 / AC-001..005).
 *
 * <p>Cada caso empieza con SharedPreferences de reglas y de seed limpios, y los
 * restaura en tearDown, para no contaminar otros tests que corran en la misma JVM
 * del emulador.
 */
@RunWith(AndroidJUnit4.class)
public class TemplateSeederTest {

    private final Context context =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() {
        // Limpia reglas y flag de seed antes de cada test.
        TemplateSeeder.clearAllRules(context);
        TemplateSeeder.resetFlag(context);
    }

    @After
    public void tearDown() {
        TemplateSeeder.clearAllRules(context);
        TemplateSeeder.resetFlag(context);
    }

    /**
     * AC-001: instalación limpia → 3 reglas (PAGOxMOVIL, ENZONA, ETECSA) sin URL,
     * firma HMAC ON, SMS habilitado.
     */
    @Test
    public void testSeedsThreeBankRulesOnFirstRun() {
        TemplateSeeder.seedIfNeeded(context);

        ArrayList<ForwardingConfig> all = ForwardingConfig.getAll(context);
        assertEquals("Deben existir exactamente 3 reglas", 3, all.size());

        boolean hasPago = false, hasEnzona = false, hasEtecsa = false;
        for (ForwardingConfig cfg : all) {
            String sender = cfg.getSender();
            // REQ-002: URL vacía (hereda webhook global).
            assertTrue("URL debe ser vacía para " + sender,
                    cfg.getUrl() == null || cfg.getUrl().isEmpty());
            assertTrue("HMAC debe estar ON para " + sender, cfg.getSignHmacSha256());
            assertTrue("SMS debe estar ON para " + sender, cfg.getIsSmsEnabled());

            if ("PAGOxMOVIL".equals(sender)) hasPago = true;
            if ("ENZONA".equals(sender))     hasEnzona = true;
            if ("ETECSA".equals(sender))     hasEtecsa = true;
        }
        assertTrue("Falta regla PAGOxMOVIL", hasPago);
        assertTrue("Falta regla ENZONA",     hasEnzona);
        assertTrue("Falta regla ETECSA",     hasEtecsa);
    }

    /**
     * AC-002 / REQ-003: flag persiste — un segundo seedIfNeeded no vuelve a crear
     * reglas aunque el usuario las haya borrado.
     */
    @Test
    public void testFlagPreventsSeedAfterRulesDeleted() {
        TemplateSeeder.seedIfNeeded(context);
        assertEquals(3, ForwardingConfig.getAll(context).size());

        // El usuario borra las reglas manualmente.
        TemplateSeeder.clearAllRules(context);
        assertEquals(0, ForwardingConfig.getAll(context).size());

        // Segundo arranque → el flag sigue activo → no se recrean.
        TemplateSeeder.seedIfNeeded(context);
        assertEquals("El seed no debe reaparecer tras borrado", 0,
                ForwardingConfig.getAll(context).size());
    }

    /**
     * AC-003 / REQ-004: un remitente preexistente no se duplica.
     */
    @Test
    public void testDoesNotDuplicatePreexistingSender() {
        // Simula un backup importado con ENZONA ya creada.
        ForwardingConfig existing = new ForwardingConfig(context);
        existing.setSender("ENZONA");
        existing.setUrl("https://custom.example/hook");
        existing.setTemplate(ForwardingConfig.getDefaultJsonTemplate());
        existing.setHeaders(ForwardingConfig.getDefaultJsonHeaders());
        existing.setRetriesNumber(ForwardingConfig.getDefaultRetriesNumber());
        existing.save();

        TemplateSeeder.seedIfNeeded(context);

        ArrayList<ForwardingConfig> all = ForwardingConfig.getAll(context);
        // Deben existir 3 en total: la preexistente + las 2 restantes sembradas.
        assertEquals("Deben existir 3 reglas (1 preexistente + 2 sembradas)", 3, all.size());

        // ENZONA no debe aparecer duplicada.
        int enzonaCount = 0;
        for (ForwardingConfig cfg : all) {
            if ("ENZONA".equals(cfg.getSender())) enzonaCount++;
        }
        assertEquals("ENZONA no debe estar duplicada", 1, enzonaCount);
    }

    /**
     * REQ-003 (complemento): idempotencia — correr seedIfNeeded dos veces seguidas
     * sin tocar las reglas no produce duplicados.
     */
    @Test
    public void testIdempotentOnSecondRun() {
        TemplateSeeder.seedIfNeeded(context);
        TemplateSeeder.seedIfNeeded(context);

        assertEquals("Dos corridas no deben duplicar reglas", 3,
                ForwardingConfig.getAll(context).size());
    }

    /**
     * REQ-005: el seed corre independientemente del permiso SMS; basta con que no
     * dependa de ninguna API protegida por ese permiso. Verificamos que las reglas
     * se crean con los valores correctos de la §4.
     */
    @Test
    public void testSeededRulesHaveCorrectDefaults() {
        TemplateSeeder.seedIfNeeded(context);

        for (ForwardingConfig cfg : ForwardingConfig.getAll(context)) {
            assertEquals("template incorrecto para " + cfg.getSender(),
                    ForwardingConfig.getDefaultJsonTemplate(), cfg.getTemplate());
            assertEquals("headers incorrecto para " + cfg.getSender(),
                    ForwardingConfig.getDefaultJsonHeaders(), cfg.getHeaders());
            assertEquals("reintentos incorrecto para " + cfg.getSender(),
                    ForwardingConfig.getDefaultRetriesNumber(), cfg.getRetriesNumber());
            assertTrue("chunkedMode debe ser true para " + cfg.getSender(),
                    cfg.getChunkedMode());
        }
    }
}
