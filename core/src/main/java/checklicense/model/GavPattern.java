package checklicense.model;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Objects;
import java.util.regex.Pattern;

import static org.apache.maven.artifact.versioning.VersionRange.createFromVersionSpec;

/**
 * Matches artifacts by groupId, artifactId and version. Every field supports the wildcards '*' and '?', and the
 * version field additionally supports maven version ranges. Leaving out the version, or using '*', matches any
 * version.
 *
 * <p>Examples: {@code com.google.guava:30.1-jre}, {@code org.slf4j:*}, {@code org.slf4j:*:1.7.*},
 * {@code commons-logging:commons-logging:[1.0,)}, {@code com.google.guava:*:(30.0,32.0]}</p>
 */
public final class GavPattern {

    private final String pattern;
    private final String source;

    private final Pattern groupId;
    private final Pattern artifactId;
    private final VersionRange version;
    private final Pattern versionPattern;

    public GavPattern(final String pattern) {
        this(pattern, "the configuration");
    }

    public GavPattern(final String pattern, final String source) {
        this.pattern = pattern;
        this.source = source;
        final var parts = pattern.split(":");
        if (parts.length < 2 || parts.length > 3)
            throw new IllegalArgumentException("expected the format <groupId>:<artifactId>[:<version>]");
        this.groupId = glob(parts[0]);
        this.artifactId = glob(parts[1]);
        final var version = parts.length == 3 ? parts[2] : "*";
        if (isVersionRange(version)) {
            this.version = range(version);
            this.versionPattern = null;
        } else {
            this.version = null;
            this.versionPattern = glob(version);
        }
    }

    public boolean matches(final String groupId, final String artifactId, final String version) {
        return matches(this.groupId, groupId)
                && matches(this.artifactId, artifactId)
                && matchesVersion(version);
    }

    public boolean matches(final checklicense.CheckedDependency dependency) {
        return matches(dependency.groupId(), dependency.artifactId(), dependency.version());
    }

    private boolean matchesVersion(final String version) {
        if (version == null) return false;
        return this.version != null
                ? this.version.containsVersion(new DefaultArtifactVersion(version))
                : this.versionPattern.matcher(version).matches();
    }

    private static boolean matches(final Pattern pattern, final String value) {
        return value != null && pattern.matcher(value).matches();
    }

    /** A version field is treated as a maven version range when it starts with '[' or '(' or ends with '+'. */
    private static boolean isVersionRange(final String version) {
        return version.startsWith("[") || version.startsWith("(") || version.endsWith("+");
    }

    private static VersionRange range(final String range) {
        try {
            return createFromVersionSpec(range);
        } catch (final Exception e) {
            throw new IllegalArgumentException("invalid version range '" + range + "', " + e.getMessage());
        }
    }

    private static Pattern glob(final String glob) {
        if (glob.isEmpty() || glob.equals("*")) return Pattern.compile(".*");
        final var regex = new StringBuilder();
        for (var i = 0; i < glob.length(); i++) {
            final var c = glob.charAt(i);
            if (c == '*') regex.append(".*");
            else if (c == '?') regex.append('.');
            else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) regex.append('\\').append(c);
            else regex.append(c);
        }
        return Pattern.compile(regex.toString());
    }

    public String pattern() {
        return pattern;
    }

    /** Describes where this pattern was configured, for use in messages. */
    public String source() {
        return source;
    }

    @Override
    public String toString() {
        return pattern;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, source);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final GavPattern other = (GavPattern) o;
        return Objects.equals(pattern, other.pattern) && Objects.equals(source, other.source);
    }

}
