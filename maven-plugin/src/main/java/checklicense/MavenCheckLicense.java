package checklicense;

import checklicense.model.*;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.License;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.maven.model.building.ModelBuildingRequest.*;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.TEST;

@Mojo( defaultPhase = VALIDATE, name = "check", threadSafe = true,
       requiresDependencyCollection = TEST, requiresDependencyResolution = TEST )
public final class MavenCheckLicense extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    public MavenProject project;
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    public MavenSession session;
    @Component
    public ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "true")
    public boolean enabled;
    @Parameter(defaultValue = "true")
    public boolean printViolations;
    @Parameter(defaultValue = "true")
    public boolean printIgnored;
    @Parameter(defaultValue = "false")
    public boolean printCompliant;
    @Parameter(defaultValue = "true")
    public boolean failBuildOnViolation;
    /** Warns in the log for every configured rule that did not match any checked artifact. */
    @Parameter(defaultValue = "true")
    public boolean warnUnmatchedRules;

    @Parameter(defaultValue = "true")
    public boolean checkCodeDependencies;
    @Parameter(defaultValue = "false")
    public boolean checkPluginDependencies;

    @Parameter(defaultValue = "true")
    public boolean includeCompileDependencies;
    @Parameter(defaultValue = "true")
    public boolean includeRuntimeDependencies;
    @Parameter(defaultValue = "false")
    public boolean includeProvidedDependencies;
    @Parameter(defaultValue = "false")
    public boolean includeTestDependencies;
    @Parameter(defaultValue = "false")
    public boolean includeSystemDependencies;

    @Parameter(defaultValue = "passOnMatch")
    public RuleStrategy strategy;

    @Parameter
    public Set<String> rules;
    @Parameter
    public Set<String> exclusions;
    @Parameter
    public Set<String> ignored;
    /** Optional file with one exclusion pattern per line, '#' starts a comment. */
    @Parameter
    public File exclusionsFile;
    /** Optional file with one ignored pattern per line, '#' starts a comment. */
    @Parameter
    public File ignoredFile;

    public void execute() throws MojoFailureException {
        if (!enabled) return;

        final var log = getLog();
        final Set<Rule> parsedRules = parseRules();
        final Set<GavPattern> parsedExclusions =
                parsePatterns("exclusions", exclusions, exclusionsFile, "checklicense.exclusions");
        final Set<GavPattern> parsedIgnored =
                parsePatterns("ignored", ignored, ignoredFile, "checklicense.ignored");

        final Set<Artifact> codeArtifacts = checkCodeDependencies ? loadCodeDependencies() : Set.of();
        final Set<Artifact> pluginArtifacts = checkPluginDependencies ? loadPluginDependencies() : Set.of();

        final var codeDependencies = toCheckedDependencies(codeArtifacts);
        final var pluginDependencies = toCheckedDependencies(pluginArtifacts);

        final boolean hasFailed = LicenseChecker.checkAll(codeDependencies, pluginDependencies, parsedRules,
                parsedExclusions, parsedIgnored, strategy, warnUnmatchedRules, printViolations, printIgnored,
                printCompliant, new MavenLogger(log));

        if (failBuildOnViolation && hasFailed) {
            throw new MojoFailureException("Violations found");
        }
    }

    private List<CheckedDependency> toCheckedDependencies(final Set<Artifact> artifacts) {
        final var result = new ArrayList<CheckedDependency>();
        for (final var artifact : artifacts) {
            final var licenses = loadLicensesFor(artifact).stream()
                    .map(license -> new checklicense.model.License(license.getName(), license.getUrl()))
                    .toList();
            result.add(new MavenCheckedDependency(artifact.getGroupId(), artifact.getArtifactId(),
                    artifact.getVersion(), licenses));
        }
        return result;
    }

    private Set<Rule> parseRules() throws MojoFailureException {
        if (rules == null || rules.isEmpty()) return emptySet();

        final var parsedRules = new HashSet<Rule>();
        for (final var rule : rules) {
            try {
                parsedRules.add(new Rule(rule));
            } catch (final IllegalArgumentException e) {
                throw new MojoFailureException("Invalid rule '" + rule + "', " + e.getMessage()
                        + ". A rule must use one of the fields [" + values(MatchField.values())
                        + "] and one of the types [" + values(MatchType.values()) + "].", e);
            }
        }
        return parsedRules;
    }

    private static Set<GavPattern> parsePatterns(final String what, final Set<String> patterns, final File file,
                                                 final String property) throws MojoFailureException {
        final var parsedPatterns = new LinkedHashSet<GavPattern>();
        if (patterns != null) {
            for (final var pattern : patterns)
                parsedPatterns.add(parsePattern(what, pattern, "the configuration"));
        }
        if (file != null) parsedPatterns.addAll(readPatternFile(what, file));
        parsedPatterns.addAll(readPatternProperty(what, property));
        return parsedPatterns;
    }

    private static GavPattern parsePattern(final String what, final String pattern, final String source)
            throws MojoFailureException {
        try {
            return new GavPattern(pattern, source);
        } catch (final IllegalArgumentException e) {
            throw new MojoFailureException("Invalid " + what + " pattern '" + pattern + "', " + e.getMessage()
                    + ". Every field supports the wildcards '*' and '?', and the version field supports maven"
                    + " version ranges like '[1.0,2.0)'.", e);
        }
    }

    private static Set<GavPattern> readPatternFile(final String what, final File file) throws MojoFailureException {
        if (!file.isFile())
            throw new MojoFailureException("The " + what + " file '" + file + "' does not exist");

        try {
            final var parsedPatterns = new LinkedHashSet<GavPattern>();
            for (final var line : Files.readAllLines(file.toPath())) {
                final var pattern = line.trim();
                if (pattern.isEmpty() || pattern.startsWith("#")) continue;
                parsedPatterns.add(parsePattern(what, pattern, "'" + file + "'"));
            }
            return parsedPatterns;
        } catch (final IOException e) {
            throw new MojoFailureException("Failed to read the " + what + " file '" + file + "'", e);
        }
    }

    private static Set<GavPattern> readPatternProperty(final String what, final String property) throws MojoFailureException {
        final var value = System.getProperty(property);
        if (value == null || value.isBlank()) return emptySet();

        final var parsedPatterns = new LinkedHashSet<GavPattern>();
        for (final var pattern : splitPatterns(value))
            parsedPatterns.add(parsePattern(what, pattern, "'" + property + "'"));
        return parsedPatterns;
    }

    /**
     * Splits a comma separated list of patterns. Commas inside a version range like '[1.0,2.0)' do not split,
     * because they are inside brackets.
     */
    public static List<String> splitPatterns(final String value) {
        final var patterns = new ArrayList<String>();
        final var pattern = new StringBuilder();
        var depth = 0;
        for (final var c : value.toCharArray()) {
            if (c == '[' || c == '(') depth++;
            else if (c == ']' || c == ')') depth = Math.max(0, depth - 1);

            if (c == ',' && depth == 0) {
                patterns.add(pattern.toString().trim());
                pattern.setLength(0);
            } else {
                pattern.append(c);
            }
        }
        patterns.add(pattern.toString().trim());
        return patterns.stream().filter(p -> !p.isEmpty()).toList();
    }

    private Set<Artifact> loadCodeDependencies() {
        // Multi-module builds are aggregated: every module in the reactor/collected project set is inspected,
        // and artifacts are deduplicated by their coordinates so a shared dependency is only checked once.
        final var artifacts = new ArrayList<Artifact>();
        for (final var moduleProject : projectsToCheck()) {
            final var resolvedArtifacts = moduleProject.getArtifacts();
            if (resolvedArtifacts != null) artifacts.addAll(resolvedArtifacts);
            // The resolved artifacts contain the transitive dependencies, the dependency artifacts contain the
            // direct dependencies as declared in the pom (these can carry information the resolved set does not)
            final var dependencyArtifacts = moduleProject.getDependencyArtifacts();
            if (dependencyArtifacts != null) artifacts.addAll(dependencyArtifacts);
        }

        final var seen = new HashSet<String>();
        final var result = new LinkedHashSet<Artifact>();
        for (final var artifact : artifacts) {
            if (!selectArtifacts(artifact)) continue;
            final var key = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            if (seen.add(key)) result.add(artifact);
        }
        return result;
    }

    private Set<Artifact> loadPluginDependencies() {
        final var artifacts = new ArrayList<Artifact>();
        for (final var moduleProject : projectsToCheck()) {
            final var pluginArtifacts = moduleProject.getPluginArtifacts();
            if (pluginArtifacts != null) artifacts.addAll(pluginArtifacts);
        }

        final var seen = new HashSet<String>();
        final var result = new LinkedHashSet<Artifact>();
        for (final var artifact : artifacts) {
            final var key = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            if (seen.add(key)) result.add(artifact);
        }
        return result;
    }

    /**
     * Returns the set of projects whose dependencies should be checked. In a multi-module build this is the
     * collected/reactor project set; for a single module it is just the current project.
     */
    private List<MavenProject> projectsToCheck() {
        final var collectedProjects = project.getCollectedProjects();
        if (collectedProjects != null && !collectedProjects.isEmpty()) {
            return collectedProjects;
        }
        return List.of(project);
    }

    public boolean selectArtifacts(final Artifact artifact) {
        final var scope = artifact.getScope();
        if ("compile".equalsIgnoreCase(scope)) return includeCompileDependencies;
        if ("runtime".equalsIgnoreCase(scope)) return includeRuntimeDependencies;
        if ("provided".equalsIgnoreCase(scope)) return includeProvidedDependencies;
        if ("test".equalsIgnoreCase(scope)) return includeTestDependencies;
        if ("system".equalsIgnoreCase(scope)) return includeSystemDependencies;
        return true;
    }

    private List<License> loadLicensesFor(final Artifact artifact) {
        final var buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest())
                .setProcessPlugins(false)
                .setValidationLevel(VALIDATION_LEVEL_MINIMAL);
        try {
            final var licenses = projectBuilder.build(artifact, buildingRequest).getProject().getLicenses();
            return licenses == null ? emptyList() : licenses;
        } catch (final ProjectBuildingException e) {
            // Not every artifact has a readable pom (system scoped dependencies for example), those are
            // reported as having no license information instead of aborting the whole check.
            getLog().warn("Could not read the pom of " + artifact.getGroupId() + ":" + artifact.getArtifactId()
                    + ":" + artifact.getVersion() + ": " + e.getMessage());
            return emptyList();
        }
    }

    public static Set<Artifact> filter(final Set<Artifact> artifacts, final Set<GavPattern> exclusions,
                                     final Set<GavPattern> matched) {
        if (exclusions == null || exclusions.isEmpty()) return artifacts;

        final var map = new java.util.HashMap<Artifact, CheckedDependency>();
        final var deps = new ArrayList<CheckedDependency>();
        for (final var artifact : artifacts) {
            final var dep = new MavenCheckedDependency(artifact.getGroupId(), artifact.getArtifactId(),
                    artifact.getVersion(), List.of());
            map.put(artifact, dep);
            deps.add(dep);
        }

        final var filtered = LicenseChecker.filter(deps, exclusions, matched);
        final var result = new HashSet<Artifact>();
        for (final var artifact : artifacts) {
            if (filtered.contains(map.get(artifact))) result.add(artifact);
        }
        return result;
    }

    public static GavPattern findMatch(final Artifact artifact, final Set<GavPattern> patterns) {
        return LicenseChecker.findMatch(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), patterns);
    }

    private static String values(final Enum<?>... constants) {
        return Arrays.stream(constants).map(Enum::name).collect(joining(", "));
    }

    private record MavenCheckedDependency(String groupId, String artifactId, String version,
                                         List<checklicense.model.License> licenses)
            implements CheckedDependency {
    }

    private record MavenLogger(org.apache.maven.plugin.logging.Log log) implements Logger {
        public void info(final String message) {
            log.info(message);
        }

        public void warn(final String message) {
            log.warn(message);
        }
    }

}
