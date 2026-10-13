package checklicense.model;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.License;

import java.util.List;

public record Ignored(String groupId, String artifactId, String version, List<License> licenses, Rule rule,
                      GavPattern pattern) {
    public Ignored(final Artifact artifact, final List<License> licenses, final Rule rule, final GavPattern pattern) {
        this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), licenses, rule, pattern);
    }
    public String toMessage() {
        final var builder = new StringBuilder();

        builder.append("Ignored License violation: ").append(gav())
                .append(" (matched '").append(pattern.pattern()).append("' from ").append(pattern.source()).append(")")
                .append("\n");
        if (licenses.isEmpty())
            builder.append(" - no license information found in the pom\n");
        for (final var license : licenses) {
            builder.append(" - name: ").append(license.getName()).append("\n")
                    .append("   url : ").append(license.getUrl()).append("\n");
        }
        if (rule != null)
            builder.append("   rule: ").append(rule).append("\n");

        return builder.toString();
    }

    private String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
