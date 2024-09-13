package checklicense.model;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.License;
import org.apache.maven.project.MavenProject;

import java.util.List;

public record Compliant(String groupId, String artifactId, String version, List<License> licenses, Rule rule) {
    public Compliant(Artifact artifact, MavenProject artifactProject, Rule rule) {
        this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifactProject.getLicenses(), rule);
    }

    public String toMessage() {
        return "License for " + groupId + ":" + artifactId + ":" + version + " matches rule '" + rule + "'";
    }

}
