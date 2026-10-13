package checklicense;

import checklicense.model.Compliant;
import checklicense.model.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static checklicense.Fixtures.license;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompliantTest {

    @Test
    public void namesTheRuleThatAcceptedTheLicense() {
        final var compliant = new Compliant("com.example", "demo", "1.0",
                List.of(license("The MIT License", "http://opensource.org/licenses/MIT")),
                new Rule("name:equal:The MIT License"));

        assertEquals("License for com.example:demo:1.0 matches rule 'name:equal:The MIT License'",
                compliant.toMessage());
    }

    @Test
    public void reportsThatNoRuleMatchedInBlacklistMode() {
        final var compliant = new Compliant("com.example", "demo", "1.0", List.of(), null);

        assertEquals("License for com.example:demo:1.0 matches no rule", compliant.toMessage());
    }

}
