package tech.bogomolov.incomingsmsgateway;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test JVM puro de {@link Heartbeat#heartbeatUrlFor}: derivación de la URL del
 * endpoint de heartbeat desde la URL del webhook (spec §4.3). No toca Android.
 */
public class HeartbeatUrlTest {

    @Test
    public void testAppendHeartbeat() {
        assertEquals(
                "https://q/api/v1/webhooks/sms/tok/heartbeat",
                Heartbeat.heartbeatUrlFor("https://q/api/v1/webhooks/sms/tok"));
    }

    @Test
    public void testTrailingSlashSinDuplicar() {
        assertEquals(
                "https://q/api/v1/webhooks/sms/tok/heartbeat",
                Heartbeat.heartbeatUrlFor("https://q/api/v1/webhooks/sms/tok/"));
    }

    @Test
    public void testNullVacio() {
        assertEquals("", Heartbeat.heartbeatUrlFor(null));
    }
}
