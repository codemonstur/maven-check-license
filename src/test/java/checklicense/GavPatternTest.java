package checklicense;

import checklicense.model.GavPattern;
import org.junit.jupiter.api.Test;

import static checklicense.Fixtures.artifact;
import static org.junit.jupiter.api.Assertions.*;

public class GavPatternTest {

    @Test
    public void exactGavMatchesExactly() {
        final var pattern = new GavPattern("com.google.guava:guava:30.1-jre");

        assertTrue(pattern.matches(artifact("com.google.guava", "guava", "30.1-jre")));
        assertFalse(pattern.matches(artifact("com.google.guava", "guava", "30.2-jre")));
        assertFalse(pattern.matches(artifact("com.google.guava", "guava", "30.1-jre-sources")));
    }

    @Test
    public void missingVersionMatchesAnyVersion() {
        final var pattern = new GavPattern("org.slf4j:slf4j-api");

        assertTrue(pattern.matches(artifact("org.slf4j", "slf4j-api", "1.7.36")));
        assertTrue(pattern.matches(artifact("org.slf4j", "slf4j-api", "2.0.9")));
        assertFalse(pattern.matches(artifact("org.slf4j", "slf4j-simple", "1.7.36")));
    }

    @Test
    public void wildcardFieldMatchesAnyValue() {
        final var pattern = new GavPattern("*:*:*");

        assertTrue(pattern.matches(artifact("com.google.guava", "guava", "30.1-jre")));
        assertTrue(pattern.matches(artifact("org.slf4j", "slf4j-api", "1.7.36")));
    }

    @Test
    public void wildcardsWorkPerField() {
        assertTrue(new GavPattern("com.google.*:*:*")
                .matches(artifact("com.google.guava", "guava", "30.1-jre")));
        assertFalse(new GavPattern("com.google.*:*:*")
                .matches(artifact("org.apache.commons", "commons-lang3", "3.14.0")));

        assertTrue(new GavPattern("*:slf4j-*:1.7.*")
                .matches(artifact("org.slf4j", "slf4j-api", "1.7.36")));
        assertFalse(new GavPattern("*:slf4j-*:1.7.*")
                .matches(artifact("org.slf4j", "slf4j-api", "2.0.9")));
    }

    @Test
    public void singleCharacterWildcardMatchesOneCharacter() {
        final var pattern = new GavPattern("org.slf4j:slf4j-api:1.?.36");

        assertTrue(pattern.matches(artifact("org.slf4j", "slf4j-api", "1.7.36")));
        assertFalse(pattern.matches(artifact("org.slf4j", "slf4j-api", "1.77.36")));
    }

    @Test
    public void wildcardsDoNotTreatDotsAsSpecial() {
        final var pattern = new GavPattern("com.google.guava:guava:30.*");

        assertTrue(pattern.matches(artifact("com.google.guava", "guava", "30.1-jre")));
        assertFalse(pattern.matches(artifact("com.googleXguava", "guava", "30.1-jre")));
    }

    @Test
    public void versionRangesAreSupported() {
        assertTrue(new GavPattern("org.apache.commons:commons-lang3:[3.0,4.0)")
                .matches(artifact("org.apache.commons", "commons-lang3", "3.14.0")));
        assertFalse(new GavPattern("org.apache.commons:commons-lang3:[3.0,4.0)")
                .matches(artifact("org.apache.commons", "commons-lang3", "4.0")));
        assertFalse(new GavPattern("org.apache.commons:commons-lang3:[3.0,4.0)")
                .matches(artifact("org.apache.commons", "commons-lang3", "2.9")));

        assertTrue(new GavPattern("commons-logging:commons-logging:[1.0,)")
                .matches(artifact("commons-logging", "commons-logging", "1.3.0")));
        assertTrue(new GavPattern("commons-logging:commons-logging:1.0+")
                .matches(artifact("commons-logging", "commons-logging", "1.3.0")));
        assertTrue(new GavPattern("junit:junit:[4.13]")
                .matches(artifact("junit", "junit", "4.13")));
        assertFalse(new GavPattern("junit:junit:[4.13]")
                .matches(artifact("junit", "junit", "4.13.2")));
    }

    @Test
    public void aPlainVersionIsNotARange() {
        final var pattern = new GavPattern("junit:junit:4.13");

        assertTrue(pattern.matches(artifact("junit", "junit", "4.13")));
        assertFalse(pattern.matches(artifact("junit", "junit", "4.13.2")));
        assertFalse(pattern.matches(artifact("junit", "junit", "5.0")));
    }

    @Test
    public void invalidPatternsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GavPattern("groupId"));
        assertThrows(IllegalArgumentException.class, () -> new GavPattern("group:artifact:1.0:extra"));
        assertThrows(IllegalArgumentException.class, () -> new GavPattern("group:artifact:[3.0,4.0"));
    }

    @Test
    public void missingArtifactFieldsNeverMatch() {
        final var pattern = new GavPattern("*:*:*");

        assertFalse(pattern.matches(artifact(null, "artifactId", "1.0")));
        assertFalse(pattern.matches(artifact("groupId", null, "1.0")));
        assertFalse(pattern.matches(artifact("groupId", "artifactId", null)));
    }

    @Test
    public void patternsWithTheSameTextAndSourceAreEqual() {
        assertEquals(new GavPattern("group:artifact:*"), new GavPattern("group:artifact:*"));
        assertNotEquals(new GavPattern("group:artifact:*", "'ignores.txt'"),
                        new GavPattern("group:artifact:*", "the configuration"));
    }

    @Test
    public void exclusiveRangeBoundsAreRespected() {
        final var pattern = new GavPattern("org.apache.commons:commons-lang3:(3.0,4.0]");

        assertTrue(pattern.matches(artifact("org.apache.commons", "commons-lang3", "3.14.0")));
        assertFalse(pattern.matches(artifact("org.apache.commons", "commons-lang3", "3.0")),
                "the lower bound is exclusive");
        assertTrue(pattern.matches(artifact("org.apache.commons", "commons-lang3", "4.0")),
                "the upper bound is inclusive");
        assertFalse(pattern.matches(artifact("org.apache.commons", "commons-lang3", "4.0.1")));
    }

    @Test
    public void emptyFieldsMatchAnything() {
        assertTrue(new GavPattern(":guava:30.1-jre").matches(artifact("com.google.guava", "guava", "30.1-jre")));
        assertTrue(new GavPattern("com.google.guava:guava:").matches(artifact("com.google.guava", "guava", "30.1-jre")));
        assertTrue(new GavPattern("com.google.guava:guava:").matches(artifact("com.google.guava", "guava", "1.0")));
    }

    @Test
    public void versionsWithSeparatorCharactersMatchLiterally() {
        assertTrue(new GavPattern("org.apache.maven:maven-core-artifact:9999.0-*")
                .matches(artifact("org.apache.maven", "maven-core-artifact",
                        "9999.0-empty-to-avoid-conflict-with-guava")));
        assertTrue(new GavPattern("com.example:demo:1.0-b+1")
                .matches(artifact("com.example", "demo", "1.0-b+1")));
        assertFalse(new GavPattern("com.example:demo:1.0-b+1")
                .matches(artifact("com.example", "demo", "1.0-b+2")));
    }

    @Test
    public void patternAndSourceExposeTheOriginalText() {
        final var pattern = new GavPattern("group:artifact:*", "'ignores.txt'");

        assertEquals("group:artifact:*", pattern.pattern());
        assertEquals("'ignores.txt'", pattern.source());
        assertEquals("group:artifact:*", pattern.toString());
        assertEquals("the configuration", new GavPattern("group:artifact:*").source());
    }

    @Test
    public void patternsDoNotEqualOtherTypesOrNull() {
        final var pattern = new GavPattern("group:artifact:*");

        assertEquals(pattern, pattern);
        assertNotEquals(pattern, null);
        assertNotEquals(pattern, "group:artifact:*");
        assertEquals(pattern, new GavPattern("group:artifact:*"));
    }

}
