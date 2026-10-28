package checklicense;

import checklicense.model.License;

import java.util.List;

public interface CheckedDependency {
    String groupId();
    String artifactId();
    String version();
    List<License> licenses();
}
