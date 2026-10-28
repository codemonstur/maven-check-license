package checklicense;

import bobthebuildtool.pojos.buildfile.Dependency;
import bobthebuildtool.pojos.buildfile.Project;
import bobthebuildtool.pojos.error.DependencyResolutionFailed;
import bobthebuildtool.services.Log;
import bobthebuildtool.services.repository.Repository;
import static bobthebuildtool.services.repository.DependencyResolver.resolveDependencies;

import checklicense.model.GavPattern;
import checklicense.model.License;
import checklicense.model.Rule;
import checklicense.model.RuleStrategy;
import jcli.annotations.CliOption;
import jcli.errors.InvalidCommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static bobthebuildtool.services.Functions.isNullOrEmpty;

public enum BobPlugin {;

    public static void installPlugin(final Project project) {
        project.addCommand("check-license", "Checks licenses of dependencies", BobPlugin::checkLicense);
    }

    public static class CliCheckLicense {
        @CliOption(longName = "rules", defaultValue = "", description = "Comma separated license rules")
        public String rules;
        @CliOption(longName = "exclusions", defaultValue = "", description = "Comma separated exclusion patterns")
        public String exclusions;
        @CliOption(longName = "ignored", defaultValue = "", description = "Comma separated ignored patterns")
        public String ignored;
        @CliOption(longName = "failOnViolation", description = "Fails the build if violations were found")
        public boolean failOnViolation;
    }

    private static int checkLicense(final Project project, final java.util.Map<String, String> environment, final String[] args)
            throws InvalidCommandLine, IOException, DependencyResolutionFailed {
        final var cli = jcli.CliParserBuilder.newCliParser(CliCheckLicense::new).name("check-license").parse(args);

        final Set<Rule> parsedRules = parseRules(cli.rules);
        final Set<GavPattern> parsedExclusions = parsePatterns(cli.exclusions);
        final Set<GavPattern> parsedIgnored = parsePatterns(cli.ignored);

        final List<CheckedDependency> dependencies = new ArrayList<>();
        for (final var dep : flatten(resolveDependencies(project))) {
            if (isNullOrEmpty(dep.repository)) continue;
            final var parts = dep.repository.split(":");
            String groupId, artifactId, version;
            if (parts.length >= 3) {
                groupId = parts[0];
                artifactId = parts[1];
                version = parts[parts.length - 1];
            } else {
                continue;
            }
            final var licenses = loadLicenses(project, dep);
            dependencies.add(new BobCheckedDependency(groupId, artifactId, version, licenses));
        }

        final Logger logger = new BobLogger();
        final boolean hasFailed = LicenseChecker.checkAll(dependencies, List.of(), parsedRules, parsedExclusions,
                parsedIgnored, RuleStrategy.passOnMatch, true, true, false, false, logger);

        if (hasFailed && cli.failOnViolation) {
            throw new IllegalStateException("License violations found");
        }
        return 0;
    }

    private static List<Dependency> flatten(bobthebuildtool.utils.TreeRoot<Project, Dependency> tree) {
        final List<Dependency> list = new ArrayList<>();
        for (final var child : tree.children) flatten(list, child);
        return list;
    }

    private static void flatten(List<Dependency> list, bobthebuildtool.utils.TreeNode<Dependency> node) {
        list.add(node.value);
        if (node.children != null) {
            for (final var child : node.children) flatten(list, child);
        }
    }

    private static List<License> loadLicenses(final Project project, final Dependency dep) {
        final List<License> licenses = new ArrayList<>();
        try {
            final Path pomPath = Repository.retrievePom(project, dep);
            if (pomPath == null || !Files.exists(pomPath)) return licenses;
            final var xml = Files.readString(pomPath);
            // extract <license> blocks
            final var licensePattern = java.util.regex.Pattern.compile("<license>\\s*(.*?)\\s*</license>", java.util.regex.Pattern.DOTALL);
            final var matcher = licensePattern.matcher(xml);
            while (matcher.find()) {
                final var block = matcher.group(1);
                final var name = extractTag(block, "name");
                final var url = extractTag(block, "url");
                licenses.add(new License(name, url));
            }
        } catch (Exception e) {
            // ignore, treat as no license info
        }
        return licenses;
    }

    private static String extractTag(final String block, final String tag) {
        final var p = java.util.regex.Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", java.util.regex.Pattern.DOTALL);
        final var m = p.matcher(block);
        return m.find() ? m.group(1).trim() : null;
    }

    private static Set<Rule> parseRules(final String value) {
        final var set = new HashSet<Rule>();
        if (isNullOrEmpty(value)) return set;
        for (final var part : value.split(",")) {
            final var trimmed = part.trim();
            if (!trimmed.isEmpty()) set.add(new Rule(trimmed));
        }
        return set;
    }

    private static Set<GavPattern> parsePatterns(final String value) {
        final var set = new HashSet<GavPattern>();
        if (isNullOrEmpty(value)) return set;
        for (final var part : value.split(",")) {
            final var trimmed = part.trim();
            if (!trimmed.isEmpty()) set.add(new GavPattern(trimmed));
        }
        return set;
    }

    private record BobCheckedDependency(String groupId, String artifactId, String version,
                                       List<License> licenses) implements CheckedDependency {
    }

    private static class BobLogger implements Logger {
        public void info(final String message) { Log.logInfo(message); }
        public void warn(final String message) { Log.logWarning(message); }
    }
}
