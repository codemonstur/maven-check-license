package checklicense;

import checklicense.model.Rule;
import checklicense.model.Violation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static checklicense.Fixtures.license;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ViolationTest {

    private static final Rule RULE = new Rule("name:equal:The MIT License");

    @Test
    public void printsEveryLicenseAndThenTheRule() {
        final var violation = new Violation("com.example", "demo", "1.0",
                List.of(license("The MIT License", "http://opensource.org/licenses/MIT"),
                        license("Apache License 2.0", "http://www.apache.org/licenses/LICENSE-2.0")),
                RULE);

        final var message = violation.toMessage();

        assertTrue(message.startsWith("License violation: com.example:demo:1.0\n"));
        assertTrue(message.contains(" - name: The MIT License\n   url : http://opensource.org/licenses/MIT\n"));
        assertTrue(message.contains(" - name: Apache License 2.0\n"
                + "   url : http://www.apache.org/licenses/LICENSE-2.0\n"));
        assertEquals(1, occurrences(message, "   rule: "),
                "the rule must be printed once, not once per license");
        assertTrue(message.endsWith("   rule: name:equal:The MIT License\n"));
    }

    @Test
    public void reportsMissingLicenseInformation() {
        final var violation = new Violation("com.example", "demo", "1.0", List.of(), null);

        final var message = violation.toMessage();

        assertEquals("License violation: com.example:demo:1.0\n"
                + " - no license information found in the pom\n", message);
    }

    @Test
    public void omitsTheRuleLineWithoutAMatchingRule() {
        final var violation = new Violation("com.example", "demo", "1.0",
                List.of(license("The MIT License", "http://opensource.org/licenses/MIT")), null);

        final var message = violation.toMessage();

        assertFalse(message.contains("rule:"));
    }

    private static long occurrences(final String message, final String search) {
        return message.lines().filter(line -> line.contains(search)).count();
    }

}
