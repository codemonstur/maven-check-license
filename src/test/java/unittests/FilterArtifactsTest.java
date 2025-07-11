package unittests;

import org.apache.maven.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FilterArtifactsTest {

    @Test
    public void filterRemovesArtifacts() {
        final var artifact = mock(Artifact.class);
        when(artifact.getGroupId()).thenReturn("groupId");
        when(artifact.getArtifactId()).thenReturn("artifactId");
        when(artifact.getVersion()).thenReturn("version");

        final var set = filter(Set.of(artifact), Set.of("groupId:artifactId:version"));

        assertNotNull(set);
        assertTrue(set.isEmpty());
    }

    @Test
    public void filterLeavesUntouched() {
        final var artifact = mock(Artifact.class);
        when(artifact.getGroupId()).thenReturn("groupId");
        when(artifact.getArtifactId()).thenReturn("artifactId");
        when(artifact.getVersion()).thenReturn("version");

        final var set = filter(Set.of(artifact), Set.of("groupId:artifactId:other"));

        assertNotNull(set);
        assertEquals(1, set.size());
    }

    private static Set<Artifact> filter(final Set<Artifact> artifacts, final Set<String> exclusions) {
        if (exclusions == null || exclusions.isEmpty()) return artifacts;
        return artifacts.stream()
            .filter(artifact -> !exclusions.contains(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion()))
            .collect(toSet());
    }

}
