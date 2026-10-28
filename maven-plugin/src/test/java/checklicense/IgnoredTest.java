package checklicense;

import checklicense.model.GavPattern;
import checklicense.model.Ignored;
import checklicense.model.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static checklicense.Fixtures.license;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IgnoredTest {

    @Test
    public void namesThePatternThatSuppressedTheViolation() {
        final var ignored = new Ignored("com.example", "demo", "1.0",
                List.of(license("GPL-3.0", "https://www.gnu.org/licenses/gpl-3.0.html")),
                new Rule("name:equal:The MIT License"),
                new GavPattern("com.example:*", "the configuration"));

        final var message = ignored.toMessage();

        assertTrue(message.startsWith("Ignored License violation: com.example:demo:1.0"
                + " (matched 'com.example:*' from the configuration)\n"));
        assertTrue(message.contains(" - name: GPL-3.0\n   url : https://www.gnu.org/licenses/gpl-3.0.html\n"));
        assertTrue(message.endsWith("   rule: name:equal:The MIT License\n"));
    }

    @Test
    public void namesTheFileThePatternCameFrom() {
        final var ignored = new Ignored("com.example", "demo", "1.0", List.of(), null,
                new GavPattern("com.example:*", "'/tmp/ignores.txt'"));

        final var message = ignored.toMessage();

        assertEquals("Ignored License violation: com.example:demo:1.0"
                + " (matched 'com.example:*' from '/tmp/ignores.txt')\n"
                + " - no license information found in the pom\n", message);
    }

}
