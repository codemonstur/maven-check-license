package checklicense;

import checklicense.model.Compliant;
import checklicense.model.GavPattern;
import checklicense.model.Ignored;
import checklicense.model.License;
import checklicense.model.Rule;
import checklicense.model.RuleStrategy;
import checklicense.model.Violation;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static checklicense.model.RuleStrategy.passOnMatch;

public enum LicenseChecker {
    ;

    public static boolean checkAll(final Collection<? extends CheckedDependency> codeDependencies,
                                  final Collection<? extends CheckedDependency> pluginDependencies,
                                  final Set<Rule> rules, final Set<GavPattern> exclusions,
                                  final Set<GavPattern> ignored, final RuleStrategy strategy,
                                  final boolean warnUnmatchedRules, final boolean printViolations,
                                  final boolean printIgnored, final boolean printCompliant,
                                  final Logger log) {
        final var matchedExclusions = new HashSet<GavPattern>();
        final var matchedIgnored = new HashSet<GavPattern>();
        final var matchedRules = new HashSet<Rule>();
        final var checkedCode = filter(codeDependencies, exclusions, matchedExclusions);
        final var checkedPlugin = filter(pluginDependencies, exclusions, matchedExclusions);

        boolean hasFailed = false;
        hasFailed |= check(checkedCode, rules, ignored, strategy, printViolations, printIgnored, printCompliant,
                matchedIgnored, matchedRules, log);
        hasFailed |= check(checkedPlugin, rules, ignored, strategy, printViolations, printIgnored, printCompliant,
                matchedIgnored, matchedRules, log);

        final int examined = codeDependencies.size() + pluginDependencies.size();
        if (warnUnmatchedRules && examined != 0) warnUnusedRules(log, rules, matchedRules, examined);
        if (examined != 0) {
            warnUnusedPatterns(log, "exclusions", exclusions, matchedExclusions, examined, "artifact");
            warnUnusedPatterns(log, "ignored", ignored, matchedIgnored, examined, "violation");
        }
        return hasFailed;
    }

    private static boolean check(final Collection<? extends CheckedDependency> artifacts, final Set<Rule> rules,
                                final Set<GavPattern> ignoredPatterns, final RuleStrategy strategy,
                                final boolean printViolations, final boolean printIgnored,
                                final boolean printCompliant, final Set<GavPattern> matchedIgnored,
                                final Set<Rule> matchedRules, final Logger log) {
        final var violations = new HashSet<Violation>();
        final var ignoredViolations = new HashSet<Ignored>();
        final var compliant = new HashSet<Compliant>();

        for (final var artifact : artifacts) {
            final var licenses = artifact.licenses() == null ? List.<License>of() : artifact.licenses();
            final var rule = findMatchingRule(licenses, rules, matchedRules);

            if (isViolation(licenses, rule, strategy)) {
                final var ignorePattern = findMatch(artifact, ignoredPatterns);
                if (ignorePattern != null) {
                    matchedIgnored.add(ignorePattern);
                    ignoredViolations.add(new Ignored(artifact.groupId(), artifact.artifactId(), artifact.version(),
                            licenses, rule, ignorePattern));
                } else {
                    violations.add(new Violation(artifact.groupId(), artifact.artifactId(), artifact.version(), licenses, rule));
                }
            } else {
                compliant.add(new Compliant(artifact.groupId(), artifact.artifactId(), artifact.version(), licenses, rule));
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

    public static Set<CheckedDependency> filter(final Collection<? extends CheckedDependency> artifacts,
                                               final Set<GavPattern> exclusions, final Set<GavPattern> matched) {
        if (exclusions == null || exclusions.isEmpty()) return new HashSet<>(artifacts);

        final var filtered = new HashSet<CheckedDependency>();
        for (final var artifact : artifacts) {
            final var pattern = findMatch(artifact, exclusions);
            if (pattern == null) filtered.add(artifact);
            else matched.add(pattern);
        }
        return filtered;
    }

    public static GavPattern findMatch(final CheckedDependency artifact, final Set<GavPattern> patterns) {
        if (patterns == null) return null;
        for (final var pattern : patterns) {
            if (pattern.matches(artifact)) return pattern;
        }
        return null;
    }

    public static GavPattern findMatch(final String groupId, final String artifactId, final String version,
                                       final Set<GavPattern> patterns) {
        if (patterns == null) return null;
        for (final var pattern : patterns) {
            if (pattern.matches(groupId, artifactId, version)) return pattern;
        }
        return null;
    }

    private static boolean isViolation(final List<License> licenses, final Rule rule, final RuleStrategy strategy) {
        if (licenses.isEmpty()) return true;
        return strategy == passOnMatch ? rule == null : rule != null;
    }

    private static Rule findMatchingRule(final List<License> licenses, final Set<Rule> rules,
                                         final Set<Rule> matchedRules) {
        if (rules == null || rules.isEmpty() || licenses.isEmpty()) return null;

        Rule first = null;
        for (final var license : licenses) {
            for (final var rule : rules) {
                if (rule.matches(license)) {
                    matchedRules.add(rule);
                    if (first == null) first = rule;
                }
            }
        }
        return first;
    }

    private static void warnUnusedRules(final Logger log, final Set<Rule> rules, final Set<Rule> matched,
                                        final int examined) {
        if (examined == 0) return;

        for (final var rule : rules) {
            if (!matched.contains(rule))
                log.warn("The rule '" + rule + "' from the configuration did not match any artifact");
        }
    }

    public static void warnUnusedPatterns(final Logger log, final String what, final Set<GavPattern> patterns,
                                         final Set<GavPattern> matched, final int examined, final String target) {
        if (examined == 0) return;

        for (final var pattern : patterns) {
            if (!matched.contains(pattern))
                log.warn("The " + what + " pattern '" + pattern + "' from " + pattern.source()
                        + " did not match any " + target);
        }
    }
}
