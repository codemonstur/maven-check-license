package checklicense;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared fixtures for the tests. */
final class Fixtures {

    private Fixtures() {}

    static Artifact artifact(final String groupId, final String artifactId, final String version) {
        return artifact(groupId, artifactId, version, "compile");
    }

    static Artifact artifact(final String groupId, final String artifactId, final String version, final String scope) {
        final var artifact = mock(Artifact.class);
        when(artifact.getGroupId()).thenReturn(groupId);
        when(artifact.getArtifactId()).thenReturn(artifactId);
        when(artifact.getVersion()).thenReturn(version);
        when(artifact.getScope()).thenReturn(scope);
        return artifact;
    }

    static License license(final String name, final String url) {
        final var license = new License();
        license.setName(name);
        license.setUrl(url);
        return license;
    }

    /** A log that records every info and warning message, so tests can assert on the output of the mojo. */
    static Log recordingLog(final List<String> messages) {
        final var log = mock(Log.class);
        doAnswer(invocation -> record(messages, invocation.getArgument(0))).when(log).info(any(CharSequence.class));
        doAnswer(invocation -> record(messages, invocation.getArgument(0))).when(log).warn(any(CharSequence.class));
        return log;
    }

    private static Object record(final List<String> messages, final Object message) {
        messages.add(message.toString());
        return null;
    }

}
