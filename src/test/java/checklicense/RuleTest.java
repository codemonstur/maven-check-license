package checklicense;

import checklicense.model.Rule;
import org.junit.jupiter.api.Test;

import static checklicense.Fixtures.license;
import static org.junit.jupiter.api.Assertions.*;

public class RuleTest {

    @Test
    public void equalMatchesExactly() {
        final var rule = new Rule("name:equal:The MIT License");

        assertTrue(rule.matches(license("The MIT License", null)));
        assertFalse(rule.matches(license("The MIT License, version 2", null)));
        assertFalse(rule.matches(license(null, "http://opensource.org/licenses/MIT")));
    }

    @Test
    public void regexMatchesTheWholeValue() {
        final var rule = new Rule("url:regex:http://(www\\.)?opensource\\.org/licenses/MIT");

        assertTrue(rule.matches(license(null, "http://opensource.org/licenses/MIT")));
        assertTrue(rule.matches(license(null, "http://www.opensource.org/licenses/MIT")));
        assertFalse(rule.matches(license(null, "https://opensource.org/licenses/MIT")));
    }

    @Test
    public void valuesMayContainColons() {
        final var rule = new Rule("url:equal:http://www.eclipse.org/org/documents/edl-v10.php");

        assertTrue(rule.matches(license(null, "http://www.eclipse.org/org/documents/edl-v10.php")));
    }

    @Test
    public void rulesWithoutAValueAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Rule("name:equal"));
        assertThrows(IllegalArgumentException.class, () -> new Rule("name"));
        assertThrows(IllegalArgumentException.class, () -> new Rule(""));
    }

    @Test
    public void unknownFieldsAndTypesAreRejected() {
        final var unknownField = assertThrows(IllegalArgumentException.class, () -> new Rule("license:equal:x"));
        assertTrue(unknownField.getMessage().contains("license"));
        assertTrue(unknownField.getMessage().contains("name"));

        final var unknownType = assertThrows(IllegalArgumentException.class, () -> new Rule("name:contains:x"));
        assertTrue(unknownType.getMessage().contains("contains"));
        assertTrue(unknownType.getMessage().contains("regex"));
    }

    @Test
    public void invalidRegexesAreRejected() {
        final var invalid = assertThrows(IllegalArgumentException.class, () -> new Rule("url:regex:["));

        assertTrue(invalid.getMessage().contains("["));
    }

    @Test
    public void rulesWithTheSameTextAreEqual() {
        final var rule = new Rule("name:equal:The MIT License");

        assertEquals(rule, rule);
        assertEquals(rule, new Rule("name:equal:The MIT License"));
        assertNotEquals(rule, new Rule("url:equal:The MIT License"));
        assertNotEquals(rule, "name:equal:The MIT License");
        assertNotEquals(rule, null);
    }

    @Test
    public void equalRulesHaveEqualHashCodes() {
        assertEquals(new Rule("name:equal:The MIT License").hashCode(),
                new Rule("name:equal:The MIT License").hashCode());
    }

    @Test
    public void toStringReturnsTheRuleText() {
        assertEquals("url:regex:http://opensource\\.org/.*", new Rule("url:regex:http://opensource\\.org/.*").toString());
    }

    @Test
    public void rulesWithAnEmptyValueMatchOnlyEmptyValues() {
        final var equal = new Rule("url:equal:");

        assertTrue(equal.matches(license(null, "")));
        assertFalse(equal.matches(license(null, "http://opensource.org/licenses/MIT")));
        assertFalse(equal.matches(license(null, null)));

        final var regex = new Rule("name:regex:");

        assertTrue(regex.matches(license("", null)));
        assertFalse(regex.matches(license("The MIT License", null)));
    }

}
