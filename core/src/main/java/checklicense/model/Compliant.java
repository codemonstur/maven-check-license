package checklicense.model;


import java.util.List;

public record Compliant(String groupId, String artifactId, String version, List<checklicense.model.License> licenses, Rule rule) {
    public String toMessage() {
        return rule == null
                ? "License for " + gav() + " matches no rule"
                : "License for " + gav() + " matches rule '" + rule + "'";
    }

    private String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }

}
