package checklicense;

import checklicense.model.Compliant;
import checklicense.model.Rule;
import checklicense.model.RuleStrategy;
import checklicense.model.Violation;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static checklicense.model.RuleStrategy.failOnMatch;
import static checklicense.model.RuleStrategy.passOnMatch;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.apache.maven.model.building.ModelBuildingRequest.*;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE_PLUS_RUNTIME;

@Mojo( defaultPhase = VALIDATE, name = "check",
       requiresDependencyCollection = COMPILE_PLUS_RUNTIME,
       requiresDependencyResolution = COMPILE_PLUS_RUNTIME )
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

    @Parameter(defaultValue = "passOnMatch")
    public RuleStrategy strategy;

    @Parameter
    public Set<String> rules;

    public void execute() throws MojoFailureException {
        if (!enabled) return;

        final var log = getLog();
        final var codeArtifacts = loadCodeDependencies();
        final var pluginArtifacts = loadPluginDependencies();
        final Set<Rule> parsedRules = rules == null ? emptySet() :
                rules.stream().map(Rule::new).collect(toSet());

        boolean hasFailed = false;
        hasFailed |= checkCodeDependencies && checkArtifacts(log, codeArtifacts, parsedRules);
        hasFailed |= checkPluginDependencies && checkArtifacts(log, pluginArtifacts, parsedRules);

        if (failBuildOnViolation && hasFailed) {
            throw new MojoFailureException("Violations found");
        }
    }

    private boolean checkArtifacts(final Log log, final Set<Artifact> artifacts, final Set<Rule> rules) throws MojoFailureException {
        final var violations = new HashSet<Violation>();
        final var compliant = new HashSet<Compliant>();

        for (final var artifact : artifacts) {
            final var licenses = loadLicensesFor(artifact);
            final var rule = findMatchingRule(licenses, rules);

            if ((strategy == passOnMatch && rule != null) || (strategy == failOnMatch && rule == null))
                compliant.add(new Compliant(artifact, licenses, rule));
            if ((strategy == passOnMatch && rule == null) || (strategy == failOnMatch && rule != null))
                violations.add(new Violation(artifact, licenses, rule));
        }

        log.info("Found " + artifacts.size() + " artifacts with " + violations.size() + " total license violation(s).");

        if (printViolations && !violations.isEmpty()) {
            for (final var violation : violations) {
                for (final var line : violation.toMessage().split("\n"))
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

    private Set<Artifact> loadCodeDependencies() {
        final var set = new HashSet<Artifact>();
        set.addAll(project.getArtifacts());
        set.addAll(project.getDependencyArtifacts());
        return set.stream().filter(this::selectArtifacts).collect(toSet());
    }

    private Set<Artifact> loadPluginDependencies() {
        return project.getPluginArtifacts();
    }

    private boolean selectArtifacts(final Artifact artifact) {
        final var scope = artifact.getScope();
        if ("runtime".equalsIgnoreCase(scope)) return includeRuntimeDependencies;
        if ("compile".equalsIgnoreCase(scope)) return includeCompileDependencies;
        if ("provided".equalsIgnoreCase(scope)) return includeProvidedDependencies;
        if ("test".equalsIgnoreCase(scope)) return includeTestDependencies;
        return true;
    }

    private List<License> loadLicensesFor(final Artifact artifact) throws MojoFailureException {
        final var buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest())
                .setValidationLevel(VALIDATION_LEVEL_MINIMAL);
        try {
            return projectBuilder.build(artifact, buildingRequest).getProject().getLicenses();
        } catch (final ProjectBuildingException e) {
            throw new MojoFailureException(e);
        }
    }

    private static Rule findMatchingRule(final List<License> licenses, final Set<Rule> rules) {
        if (rules == null || rules.isEmpty()) return null;

        for (final var license : licenses) {
            for (final var rule : rules) {
                if (rule.matches(license))
                    return rule;
            }
        }

        return null;
    }

}