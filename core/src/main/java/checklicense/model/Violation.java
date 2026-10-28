package checklicense.model;


import java.util.List;

public record Violation(String groupId, String artifactId, String version, List<checklicense.model.License> licenses, Rule rule) {
    public String toMessage() {
        final var builder = new StringBuilder();

        builder.append("License violation: ").append(gav()).append("\n");
        if (licenses.isEmpty())
            builder.append(" - no license information found in the pom\n");
        for (final var license : licenses) {
            builder.append(" - name: ").append(license.name()).append("\n")
                    .append("   url : ").append(license.url()).append("\n");
        }
        if (rule != null)
            builder.append("   rule: ").append(rule).append("\n");

        return builder.toString();
    }

    private String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
