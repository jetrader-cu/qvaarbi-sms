package tech.bogomolov.incomingsmsgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-JVM unit tests for {@link ForwardingConfig#prepareMessage}.
 * <p>
 * prepareMessage does template placeholder substitution and JSON-escaping and
 * touches no Android APIs, so it can run on the local JVM without a device.
 * The constructor stores the Context but prepareMessage never reads it, so a
 * null context is fine here.
 */
public class ForwardingConfigPrepareMessageTest {

    private ForwardingConfig configWithTemplate(String template) {
        ForwardingConfig config = new ForwardingConfig(null);
        config.setTemplate(template);
        return config;
    }

    @Test
    public void testSubstitutesAllPlaceholders() {
        ForwardingConfig config = configWithTemplate(
                "from=%from% text=%text% sent=%sentStamp% recv=%receivedStamp% sim=%sim%");

        long before = System.currentTimeMillis();
        String result = config.prepareMessage("alice", "hi", "sim2", 42L);
        long after = System.currentTimeMillis();

        assertTrue(result, result.contains("from=alice "));
        assertTrue(result, result.contains(" text=hi "));
        assertTrue(result, result.contains(" sent=42 "));
        assertTrue(result, result.endsWith(" sim=sim2"));

        // %receivedStamp% is filled with the current time at call time.
        Matcher matcher = Pattern.compile("recv=(\\d+)").matcher(result);
        assertTrue("receivedStamp not found in: " + result, matcher.find());
        long received = Long.parseLong(matcher.group(1));
        assertTrue(received >= before && received <= after);
    }

    @Test
    public void testEscapesJsonInText() {
        ForwardingConfig config = configWithTemplate("\"text\":\"%text%\"");

        String result = config.prepareMessage("123", "he said \"hi\"", "sim1", 0L);

        // The embedded quotes must be JSON-escaped so the template stays valid JSON.
        assertEquals("\"text\":\"he said \\\"hi\\\"\"", result);
    }

    @Test
    public void testDollarSignInTextIsLiteral() {
        // %text% is wrapped in Matcher.quoteReplacement, so a '$' in the SMS body
        // must not be interpreted as a regex group reference.
        ForwardingConfig config = configWithTemplate("[%text%]");

        String result = config.prepareMessage("123", "$100", "sim1", 0L);

        assertEquals("[$100]", result);
    }

    @Test
    public void testBackslashInTextIsEscapedAndLiteral() {
        ForwardingConfig config = configWithTemplate("[%text%]");

        String result = config.prepareMessage("123", "a\\b", "sim1", 0L);

        // escapeJson turns the backslash into "\\"; quoteReplacement keeps it literal.
        assertEquals("[a\\\\b]", result);
    }

    @Test
    public void testPlaceholderInSmsBodyIsNotResubstituted() {
        // %text% is substituted last, so a placeholder-looking SMS body is left as-is.
        ForwardingConfig config = configWithTemplate("from=%from% text=%text%");

        String result = config.prepareMessage("alice", "call %from% now", "sim1", 0L);

        assertEquals("from=alice text=call %from% now", result);
    }

    @Test
    public void testDollarSignInSenderIsLiteral() {
        // Alphanumeric SMS sender IDs can contain '$'. %from% is not regex-escaped
        // by the template author, so prepareMessage must quote the replacement to
        // avoid a regex group reference (which would throw IndexOutOfBoundsException).
        ForwardingConfig config = configWithTemplate("from=%from%");

        String result = config.prepareMessage("ab$1cd", "hi", "sim1", 0L);

        assertEquals("from=ab$1cd", result);
    }

    @Test
    public void testDollarSignInSimIsLiteral() {
        ForwardingConfig config = configWithTemplate("sim=%sim%");

        String result = config.prepareMessage("123", "hi", "$im$1", 0L);

        assertEquals("sim=$im$1", result);
    }

    @Test
    public void testNewlineInTextIsEscaped() {
        ForwardingConfig config = configWithTemplate("[%text%]");

        String result = config.prepareMessage("123", "line1\nline2", "sim1", 0L);

        assertEquals("[line1\\nline2]", result);
    }
}
