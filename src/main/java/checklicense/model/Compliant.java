package checklicense.model;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.License;

import java.util.List;

public record Compliant(String groupId, String artifactId, String version, List<License> licenses, Rule rule) {
    public Compliant(final Artifact artifact, final List<License> licenses, final Rule rule) {
        this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), licenses, rule);
    }

    public String toMessage() {
        return rule == null
                ? "License for " + gav() + " matches no rule"
                : "License for " + gav() + " matches rule '" + rule + "'";
    }

    private String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }

}
