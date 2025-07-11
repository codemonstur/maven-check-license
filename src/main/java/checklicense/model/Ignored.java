package checklicense.model;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.License;

import java.util.List;

public record Ignored(String groupId, String artifactId, String version, List<License> licenses, Rule rule) {
    public Ignored(final Artifact artifact, final List<License> licenses, final Rule rule) {
        this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), licenses, rule);
    }
    public String toMessage() {
        final var builder = new StringBuilder();

        builder.append("Ignored License violation: ")
                .append(groupId).append(":").append(artifactId).append(":").append(version)
                .append("\n");
        for (final var license : licenses) {
            builder.append(" - name: ").append(license.getName()).append("\n")
                    .append("   url : ").append(license.getUrl()).append("\n");
            if (rule != null)
                builder.append("   rule: ").append(rule).append("\n");
        }

        return builder.toString();
    }
}
