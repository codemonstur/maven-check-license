package checklicense;

import checklicense.model.*;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.License;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
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

import static checklicense.model.RuleStrategy.passOnMatch;
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
        final var examined = codeArtifacts.size() + pluginArtifacts.size();

        final var matchedExclusions = new HashSet<GavPattern>();
        final var checkedCodeArtifacts = filter(codeArtifacts, parsedExclusions, matchedExclusions);
        final var checkedPluginArtifacts = filter(pluginArtifacts, parsedExclusions, matchedExclusions);

        final var matchedIgnored = new HashSet<GavPattern>();
        boolean hasFailed = false;
        hasFailed |= checkArtifacts(log, checkedCodeArtifacts, parsedRules, parsedIgnored, matchedIgnored);
        hasFailed |= checkArtifacts(log, checkedPluginArtifacts, parsedRules, parsedIgnored, matchedIgnored);

        warnUnusedPatterns(log, "exclusions", parsedExclusions, matchedExclusions, examined, "artifact");
        warnUnusedPatterns(log, "ignored", parsedIgnored, matchedIgnored, examined, "violation");

        if (failBuildOnViolation && hasFailed) {
            throw new MojoFailureException("Violations found");
        }
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
    static List<String> splitPatterns(final String value) {
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

    private static void warnUnusedPatterns(final Log log, final String what, final Set<GavPattern> patterns,
                                           final Set<GavPattern> matched, final int examined, final String target) {
        if (examined == 0) return;

        for (final var pattern : patterns) {
            if (!matched.contains(pattern))
                log.warn("The " + what + " pattern '" + pattern + "' from " + pattern.source()
                        + " did not match any " + target);
        }
    }

    private boolean checkArtifacts(final Log log, final Set<Artifact> artifacts, final Set<Rule> rules,
                                   final Set<GavPattern> ignoredPatterns, final Set<GavPattern> matchedIgnored) {
        final var violations = new HashSet<Violation>();
        final var ignoredViolations = new HashSet<Ignored>();
        final var compliant = new HashSet<Compliant>();

        for (final var artifact : artifacts) {
            final var licenses = loadLicensesFor(artifact);
            final var rule = findMatchingRule(licenses, rules);

            if (isViolation(licenses, rule)) {
                final var ignorePattern = findMatch(artifact, ignoredPatterns);
                if (ignorePattern != null) {
                    matchedIgnored.add(ignorePattern);
                    ignoredViolations.add(new Ignored(artifact, licenses, rule, ignorePattern));
                } else {
                    violations.add(new Violation(artifact, licenses, rule));
                }
            } else {
                compliant.add(new Compliant(artifact, licenses, rule));
            }
        }

        if (!artifacts.isEmpty())
            log.info("Checked " + artifacts.size() + " artifact(s), found " + violations.size()
                    + " license violation(s) and " + ignoredViolations.size() + " ignored violation(s).");

        if (printViolations && !violations.isEmpty()) {
            for (final var violation : violations) {
                for (final var line : violation.toMessage().split("\n"))
                    log.warn(line);
            }
        }
        if (printIgnored && !ignoredViolations.isEmpty()) {
            for (final var ignore : ignoredViolations) {
                for (final var line : ignore.toMessage().split("\n"))
                    log.warn(line);
            }
        }
        if (printCompliant && !compliant.isEmpty()) {
            for (final var license : compliant) {
                for (final var line : license.toMessage().split("\n"))
                    log.info(line);
            }
        }

        return !violations.isEmpty();
    }

    /**
     * An artifact is a violation when it does not match a rule (whitelist strategy), when it matches a rule
     * (blacklist strategy), or when no license information is available at all: an unknown license can never
     * be proven acceptable, so it is a violation for both strategies.
     */
    private boolean isViolation(final List<License> licenses, final Rule rule) {
        if (licenses.isEmpty()) return true;
        return strategy == passOnMatch ? rule == null : rule != null;
    }

    private Set<Artifact> loadCodeDependencies() {
        final var set = new HashSet<Artifact>();
        final var artifacts = project.getArtifacts();
        if (artifacts != null) set.addAll(artifacts);
        // The resolved artifacts contain the transitive dependencies, the dependency artifacts contain the
        // direct dependencies as declared in the pom (these can carry information the resolved set does not)
        final var dependencyArtifacts = project.getDependencyArtifacts();
        if (dependencyArtifacts != null) set.addAll(dependencyArtifacts);
        return set.stream().filter(this::selectArtifacts).collect(toSet());
    }

    private Set<Artifact> loadPluginDependencies() {
        final var pluginArtifacts = project.getPluginArtifacts();
        return pluginArtifacts == null ? emptySet() : pluginArtifacts;
    }

    static Set<Artifact> filter(final Set<Artifact> artifacts, final Set<GavPattern> exclusions,
                                final Set<GavPattern> matched) {
        if (exclusions.isEmpty()) return artifacts;

        final var filtered = new HashSet<Artifact>();
        for (final var artifact : artifacts) {
            final var pattern = findMatch(artifact, exclusions);
            if (pattern == null) filtered.add(artifact);
            else matched.add(pattern);
        }
        return filtered;
    }

    static GavPattern findMatch(final Artifact artifact, final Set<GavPattern> patterns) {
        for (final var pattern : patterns) {
            if (pattern.matches(artifact)) return pattern;
        }
        return null;
    }

    boolean selectArtifacts(final Artifact artifact) {
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

    private static Rule findMatchingRule(final List<License> licenses, final Set<Rule> rules) {
        if (rules == null || rules.isEmpty() || licenses.isEmpty()) return null;

        for (final var license : licenses) {
            for (final var rule : rules) {
                if (rule.matches(license))
                    return rule;
            }
        }

        return null;
    }

    private static String values(final Enum<?>... constants) {
        return Arrays.stream(constants).map(Enum::name).collect(joining(", "));
    }

}
