package tech.bogomolov.incomingsmsgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test JVM puro de {@link UpdateManifest}: parseo del JSON y comparación de versión
 * (spec §4.3 / AC-004, AC-007). No toca Android.
 */
public class UpdateManifestTest {

    private static final String OK =
            "{\"available\":true,\"versionCode\":3,\"versionName\":\"1.2.0\","
                    + "\"apkUrl\":\"https://cdn/apk\",\"notes\":\"nuevo\",\"sha256\":\"ab\"}";

    @Test
    public void testParseCamposCompletos() {
        UpdateManifest m = UpdateManifest.parse(OK);
        assertEquals(3, m.versionCode);
        assertEquals("1.2.0", m.versionName);
        assertEquals("https://cdn/apk", m.apkUrl);
        assertEquals("nuevo", m.notes);
    }

    @Test
    public void testHayUpdateCuandoCodeMayor() {
        UpdateManifest m = UpdateManifest.parse(OK);
        assertTrue(m.isNewerThan(2));
        assertFalse(m.isNewerThan(3));
        assertFalse(m.isNewerThan(4));
    }

    @Test
    public void testAvailableFalseDevuelveNull() {
        assertNull(UpdateManifest.parse("{\"available\":false}"));
    }

    @Test
    public void testSinVersionCodeNull() {
        assertNull(UpdateManifest.parse("{\"versionName\":\"1.0\"}"));
    }

    @Test
    public void testJsonInvalidoNull() {
        assertNull(UpdateManifest.parse("no-json"));
        assertNull(UpdateManifest.parse(""));
        assertNull(UpdateManifest.parse(null));
    }
}
