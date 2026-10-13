package checklicense;

import checklicense.model.GavPattern;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static checklicense.Fixtures.artifact;
import static org.junit.jupiter.api.Assertions.*;

public class FilterArtifactsTest {

    @Test
    public void filterRemovesArtifacts() {
        final var matched = new HashSet<GavPattern>();

        final var set = MavenCheckLicense.filter(Set.of(artifact("groupId", "artifactId", "version")),
                                                 Set.of(new GavPattern("groupId:artifactId:version")), matched);

        assertNotNull(set);
        assertTrue(set.isEmpty());
        assertEquals(1, matched.size());
    }

    @Test
    public void filterLeavesUntouched() {
        final var set = MavenCheckLicense.filter(Set.of(artifact("groupId", "artifactId", "version")),
                                                 Set.of(new GavPattern("groupId:artifactId:other")), new HashSet<>());

        assertNotNull(set);
        assertEquals(1, set.size());
    }

    @Test
    public void filterIgnoresMissingExclusions() {
        final var artifacts = Set.of(artifact("groupId", "artifactId", "version"));

        assertSame(artifacts, MavenCheckLicense.filter(artifacts, Set.of(), new HashSet<>()));
    }

    @Test
    public void filterRemovesArtifactMatchedByWildcard() {
        final var matched = new HashSet<GavPattern>();

        final var set = MavenCheckLicense.filter(Set.of(artifact("groupId", "artifactId", "1.0-SNAPSHOT")),
                                                 Set.of(new GavPattern("groupId:*")), matched);

        assertTrue(set.isEmpty());
        assertEquals(Set.of(new GavPattern("groupId:*")), matched);
    }

    @Test
    public void filterOnlyRemovesExactGav() {
        final var set = MavenCheckLicense.filter(Set.of(artifact("groupId", "artifactId", "1.0-SNAPSHOT")),
                                                 Set.of(new GavPattern("groupId:artifactId:1.0")), new HashSet<>());

        assertEquals(1, set.size());
    }

    @Test
    public void findMatchReturnsTheMatchingPattern() {
        final var patterns = Set.of(new GavPattern("other:artifactId:*"), new GavPattern("*:artifactId:version"));
        final var artifact = artifact("groupId", "artifactId", "version");

        assertEquals(new GavPattern("*:artifactId:version"), MavenCheckLicense.findMatch(artifact, patterns));
        assertNull(MavenCheckLicense.findMatch(artifact("groupId", "artifactId", "2.0"), patterns));
    }

}
