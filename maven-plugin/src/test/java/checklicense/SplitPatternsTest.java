package checklicense;

import org.junit.jupiter.api.Test;

import java.util.List;

import static checklicense.MavenCheckLicense.splitPatterns;
import static org.junit.jupiter.api.Assertions.*;

public class SplitPatternsTest {

    @Test
    public void splitsOnCommas() {
        assertEquals(List.of("group:artifact:1.0", "other:artifact:*"), splitPatterns("group:artifact:1.0,other:artifact:*"));
    }

    @Test
    public void splitsOnCommasWithSurroundingWhitespace() {
        assertEquals(List.of("group:artifact:1.0", "other:artifact:*"),
                     splitPatterns("group:artifact:1.0, other:artifact:*\n"));
    }

    @Test
    public void keepsCommasInsideVersionRanges() {
        assertEquals(List.of("group:artifact:[1.0,2.0)", "other:artifact:*"),
                     splitPatterns("group:artifact:[1.0,2.0),other:artifact:*"));
    }

    @Test
    public void keepsCommasInsideRoundBracketRanges() {
        assertEquals(List.of("group:artifact:(3.0,4.0]", "other:artifact:*"),
                     splitPatterns("group:artifact:(3.0,4.0],other:artifact:*"));
    }

    @Test
    public void dropsEmptyEntries() {
        assertEquals(List.of(), splitPatterns(""));
        assertEquals(List.of("group:artifact:*"), splitPatterns(" , group:artifact:* , "));
    }

}
