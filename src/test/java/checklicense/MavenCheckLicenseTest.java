package checklicense;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.License;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import checklicense.model.RuleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static checklicense.Fixtures.artifact;
import static checklicense.Fixtures.license;
import static checklicense.Fixtures.recordingLog;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MavenCheckLicenseTest {

    private static final License MIT = license("The MIT License", "http://opensource.org/licenses/MIT");
    private static final License GPL = license("GNU General Public License v3.0", "https://www.gnu.org/licenses/gpl-3.0.html");

    private final List<String> messages = new ArrayList<>();
    private final Map<Artifact, List<License>> licenses = new HashMap<>();
    private final Set<Artifact> artifacts = new LinkedHashSet<>();
    private final Set<Artifact> dependencyArtifacts = new LinkedHashSet<>();
    private final Set<Artifact> pluginArtifacts = new LinkedHashSet<>();

    @TempDir
    Path tempDir;

    @AfterEach
    public void clearProperties() {
        System.clearProperty("checklicense.ignored");
        System.clearProperty("checklicense.exclusions");
    }

    // ---------------------------------------------------------------- strategy

    @Test
    public void whitelistPassesAnArtifactMatchingARule() throws Exception {
        givenArtifact("com.example", "demo", "1.0", MIT);

        final var mojo = mojo();
        mojo.printCompliant = true;
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();

        assertTrue(messages.contains("Checked 1 artifact(s), found 0 license violation(s) and 0 ignored violation(s)."));
        assertTrue(messages.contains("License for com.example:demo:1.0 matches rule 'name:equal:The MIT License'"));
    }

    @Test
    public void compliantMessagesAreHiddenByDefault() throws Exception {
        givenArtifact("com.example", "demo", "1.0", MIT);

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();

        assertTrue(messages.stream().noneMatch(message -> message.contains("matches rule")));
    }

    @Test
    public void whitelistFailsAnArtifactWithoutAMatchingRule() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals("Violations found", failure.getMessage());
        assertTrue(messages.contains("License violation: com.example:demo:1.0"));
    }

    @Test
    public void blacklistFailsAnArtifactMatchingARule() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);

        final var mojo = mojo();
        mojo.strategy = RuleStrategy.failOnMatch;
        mojo.rules = Set.of("url:regex:https://www\\.gnu\\.org/licenses/gpl-3\\.0\\.html");

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals("Violations found", failure.getMessage());
        assertTrue(messages.contains("License violation: com.example:demo:1.0"));
    }

    @Test
    public void blacklistPassesAnArtifactWithoutAMatchingRule() throws Exception {
        givenArtifact("com.example", "demo", "1.0", MIT);

        final var mojo = mojo();
        mojo.strategy = RuleStrategy.failOnMatch;
        mojo.printCompliant = true;
        mojo.rules = Set.of("url:regex:https://www\\.gnu\\.org/licenses/gpl-3\\.0\\.html");

        mojo.execute();

        assertTrue(messages.contains("License for com.example:demo:1.0 matches no rule"));
    }

    @Test
    public void artifactWithoutLicenseInformationIsAViolationForBothStrategies() throws Exception {
        givenArtifact("com.example", "demo", "1.0");

        final var mojo = mojo();
        mojo.strategy = RuleStrategy.failOnMatch;
        mojo.rules = Set.of("url:equal:http://example.com/never-matches");

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals("Violations found", failure.getMessage());
        assertTrue(messages.contains("License violation: com.example:demo:1.0"));
        assertTrue(messages.contains(" - no license information found in the pom"));
    }

    @Test
    public void violationsDoNotFailTheBuildWhenConfiguredNotTo() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);

        final var mojo = mojo();
        mojo.failBuildOnViolation = false;
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();

        assertTrue(messages.contains("License violation: com.example:demo:1.0"));
    }

    @Test
    public void disabledMojoChecksNothing() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);

        final var mojo = mojo();
        mojo.enabled = false;
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();

        assertTrue(messages.isEmpty());
    }

    // ---------------------------------------------------------------- what gets checked

    @Test
    public void codeDependenciesAreOnlyCheckedWhenEnabled() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);

        final var mojo = mojo();
        mojo.checkCodeDependencies = false;
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();

        assertTrue(messages.isEmpty());
    }

    @Test
    public void pluginDependenciesAreOnlyCheckedWhenEnabled() throws Exception {
        final var plugin = artifact("com.example", "plugin", "1.0");
        pluginArtifacts.add(plugin);
        licenses.put(plugin, List.of(GPL));

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();
        assertTrue(messages.isEmpty());

        messages.clear();
        final var withPlugins = mojo();
        withPlugins.checkPluginDependencies = true;
        withPlugins.rules = Set.of("name:equal:The MIT License");

        assertThrows(MojoFailureException.class, withPlugins::execute);
        assertTrue(messages.contains("License violation: com.example:plugin:1.0"));
    }

    @Test
    public void testScopedDependenciesAreOnlyCheckedWhenEnabled() throws Exception {
        artifacts.add(artifact("com.example", "test-dep", "1.0", "test"));

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();
        assertTrue(messages.stream().noneMatch(message -> message.contains("test-dep")));

        final var withTests = mojo();
        withTests.includeTestDependencies = true;
        withTests.failBuildOnViolation = false;
        withTests.rules = Set.of("name:equal:The MIT License");

        withTests.execute();
        assertTrue(messages.contains("License violation: com.example:test-dep:1.0"));
    }

    @Test
    public void excludedArtifactsAreNotChecked() throws Exception {
        givenArtifact("com.example", "excluded", "1.0", GPL);
        givenArtifact("com.example", "included", "1.0", MIT);

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");
        mojo.exclusions = Set.of("com.example:excluded");

        mojo.execute();

        assertTrue(messages.contains("Checked 1 artifact(s), found 0 license violation(s) and 0 ignored violation(s)."));
    }

    @Test
    public void ignoredPatternsSuppressViolationsWithoutFailingTheBuild() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");
        mojo.ignored = Set.of("com.example:*");

        mojo.execute();

        assertTrue(messages.contains("Ignored License violation: com.example:demo:1.0"
                + " (matched 'com.example:*' from the configuration)"));
        assertTrue(messages.contains("Checked 1 artifact(s), found 0 license violation(s) and 1 ignored violation(s)."));
    }

    @Test
    public void patternsThatMatchNothingAreReported() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");
        mojo.ignored = Set.of("com.other:*");
        mojo.exclusions = Set.of("*:never-used:*");

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals("Violations found", failure.getMessage());
        assertTrue(messages.contains("The ignored pattern 'com.other:*' from the configuration"
                + " did not match any violation"));
        assertTrue(messages.contains("The exclusions pattern '*:never-used:*' from the configuration"
                + " did not match any artifact"));
    }

    @Test
    public void rulesThatMatchNothingAreReported() throws Exception {
        givenArtifact("com.example", "demo", "1.0", MIT);

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License", "name:equal:Nonexistent License");

        mojo.execute();

        assertTrue(messages.contains("The rule 'name:equal:Nonexistent License' from the configuration"
                + " did not match any artifact"));
        assertTrue(messages.stream().noneMatch(message -> message
                .startsWith("The rule 'name:equal:The MIT License'")));
    }

    @Test
    public void unmatchedRuleWarningsCanBeDisabled() throws Exception {
        givenArtifact("com.example", "demo", "1.0", MIT);

        final var mojo = mojo();
        mojo.warnUnmatchedRules = false;
        mojo.rules = Set.of("name:equal:The MIT License", "name:equal:Nonexistent License");

        mojo.execute();

        assertTrue(messages.stream().noneMatch(message -> message.startsWith("The rule ")));
    }

    @Test
    public void rulesMatchingTheSameLicenseAreBothCountedAsMatched() throws Exception {
        givenArtifact("com.example", "demo", "1.0", MIT);

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License", "name:regex:The MIT.*");

        mojo.execute();

        assertTrue(messages.stream().noneMatch(message -> message.startsWith("The rule ")));
    }

    @Test
    public void projectsWithoutAnyArtifactSetsAreSupported() throws Exception {
        final var mojo = new MavenCheckLicense();
        mojo.project = new MavenProject();
        mojo.session = session();
        mojo.projectBuilder = builder(licenses);
        mojo.setLog(recordingLog(messages));
        applyParameterDefaults(mojo);
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();

        assertTrue(messages.isEmpty());
    }

    // ---------------------------------------------------------------- configuration

    @Test
    public void withoutConfiguredRulesEveryLicensedArtifactIsAViolation() throws Exception {
        givenArtifact("com.example", "demo", "1.0", MIT);

        final var mojo = mojo();
        mojo.rules = null;

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals("Violations found", failure.getMessage());
        assertTrue(messages.contains("License violation: com.example:demo:1.0"));
    }

    @Test
    public void anEmptyRuleSetBehavesLikeNoRules() throws Exception {
        givenArtifact("com.example", "demo", "1.0", MIT);

        final var mojo = mojo();
        mojo.rules = Set.of();

        assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(messages.contains("License violation: com.example:demo:1.0"));
    }

    @Test
    public void violationsAreNotPrintedWhenPrintingIsDisabled() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);

        final var mojo = mojo();
        mojo.printViolations = false;
        mojo.rules = Set.of("name:equal:The MIT License");

        assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(messages.contains("Checked 1 artifact(s), found 1 license violation(s)"
                + " and 0 ignored violation(s)."));
        assertTrue(messages.stream().noneMatch(message -> message.startsWith("License violation:")));
    }

    @Test
    public void ignoredViolationsAreNotPrintedWhenPrintingIsDisabled() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);

        final var mojo = mojo();
        mojo.printIgnored = false;
        mojo.rules = Set.of("name:equal:The MIT License");
        mojo.ignored = Set.of("com.example:*");

        mojo.execute();

        assertTrue(messages.contains("Checked 1 artifact(s), found 0 license violation(s)"
                + " and 1 ignored violation(s)."));
        assertTrue(messages.stream().noneMatch(message -> message.startsWith("Ignored License violation:")));
    }

    @Test
    public void blankSystemPropertiesAreIgnored() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);
        System.setProperty("checklicense.ignored", "   ");

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");

        assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(messages.contains("License violation: com.example:demo:1.0"));
    }

    @Test
    public void aNullLicenseListIsTreatedAsNoLicenseInformation() throws Exception {
        final var artifact = artifact("com.example", "demo", "1.0");
        artifacts.add(artifact);
        licenses.put(artifact, null);

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");

        assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(messages.contains(" - no license information found in the pom"));
    }

    @Test
    public void aProjectWithoutAnyArtifactSetIsSupported() throws Exception {
        final var project = mock(MavenProject.class);
        when(project.getArtifacts()).thenReturn(null);
        when(project.getDependencyArtifacts()).thenReturn(null);
        when(project.getPluginArtifacts()).thenReturn(null);

        final var mojo = new MavenCheckLicense();
        mojo.project = project;
        mojo.session = session();
        mojo.projectBuilder = builder(licenses);
        mojo.setLog(recordingLog(messages));
        applyParameterDefaults(mojo);
        mojo.checkPluginDependencies = true;
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();

        assertTrue(messages.isEmpty());
    }

    // ---------------------------------------------------------------- configuration

    @Test
    public void patternsCanBeReadFromAFile() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);
        final var ignores = tempDir.resolve("license-ignores.txt");
        Files.writeString(ignores, "# allowed violations\n\ncom.example:*\n");

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");
        mojo.ignoredFile = ignores.toFile();

        mojo.execute();

        assertTrue(messages.contains("Ignored License violation: com.example:demo:1.0"
                + " (matched 'com.example:*' from '" + ignores + "')"));
    }

    @Test
    public void patternsCanBeReadFromASystemProperty() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);
        System.setProperty("checklicense.ignored", "com.example:demo:[1.0,2.0),com.other:*");

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();

        assertTrue(messages.contains("Ignored License violation: com.example:demo:1.0"
                + " (matched 'com.example:demo:[1.0,2.0)' from 'checklicense.ignored')"));
    }

    @Test
    public void exclusionsCanBeReadFromASystemProperty() throws Exception {
        givenArtifact("com.example", "demo", "1.0", GPL);
        givenArtifact("com.example", "other", "1.0", MIT);
        System.setProperty("checklicense.exclusions", "com.example:demo");

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");

        mojo.execute();

        assertTrue(messages.contains("Checked 1 artifact(s), found 0 license violation(s)"
                + " and 0 ignored violation(s)."));
        assertTrue(messages.stream().noneMatch(message -> message.contains("com.example:demo:1.0")));
    }

    @Test
    public void invalidRulesAreReportedAsConfigurationException() throws ProjectBuildingException {
        final var mojo = mojo();
        mojo.rules = Set.of("name:contains:x");

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(failure.getMessage().startsWith("Invalid rule 'name:contains:x'"));
    }

    @Test
    public void invalidPatternsAreReportedAsConfigurationException() throws ProjectBuildingException {
        final var mojo = mojo();
        mojo.ignored = Set.of("com.example");

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(failure.getMessage().startsWith("Invalid ignored pattern 'com.example'"));
    }

    @Test
    public void aMissingPatternFileIsReportedAsConfigurationException() throws ProjectBuildingException {
        final var mojo = mojo();
        mojo.ignoredFile = tempDir.resolve("does-not-exist.txt").toFile();

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(failure.getMessage().startsWith("The ignored file '"));
    }

    @Test
    public void anUnreadablePatternFileIsReportedAsConfigurationException() throws Exception {
        final var ignores = tempDir.resolve("binary-ignores.txt");
        Files.write(ignores, new byte[] {(byte) 0xC3, (byte) 0x28, 0x0A}); // not valid UTF-8

        final var mojo = mojo();
        mojo.ignoredFile = ignores.toFile();

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(failure.getMessage().startsWith("Failed to read the ignored file '"));
    }

    @Test
    public void anUnreadablePomIsReportedAsMissingLicenseInformation() throws Exception {
        final var artifact = artifact("com.example", "demo", "1.0");
        artifacts.add(artifact);

        final var mojo = mojo();
        mojo.rules = Set.of("name:equal:The MIT License");
        mojo.projectBuilder = mock(ProjectBuilder.class);
        when(mojo.projectBuilder.build(any(Artifact.class), any(ProjectBuildingRequest.class)))
                .thenThrow(new ProjectBuildingException("com.example:demo:1.0",
                        "Error resolving project artifact", (Throwable) null));

        final var failure = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals("Violations found", failure.getMessage());
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("Could not read the pom of com.example:demo:1.0")));
        assertTrue(messages.contains("License violation: com.example:demo:1.0"));
    }

    // ---------------------------------------------------------------- fixtures

    private void givenArtifact(final String groupId, final String artifactId, final String version,
                               final License... licenses) {
        final var artifact = artifact(groupId, artifactId, version);
        artifacts.add(artifact);
        this.licenses.put(artifact, List.of(licenses));
    }

    private MavenCheckLicense mojo() throws ProjectBuildingException {
        final var project = new MavenProject();
        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(dependencyArtifacts);
        project.setPluginArtifacts(pluginArtifacts);

        final var mojo = new MavenCheckLicense();
        mojo.project = project;
        mojo.session = session();
        mojo.projectBuilder = builder(licenses);
        mojo.setLog(recordingLog(messages));
        applyParameterDefaults(mojo);
        return mojo;
    }

    /**
     * The values Maven injects for the @Parameter(defaultValue=...) annotations. The fields are false/null
     * otherwise, because that injection does not happen in unit tests.
     */
    private static void applyParameterDefaults(final MavenCheckLicense mojo) {
        mojo.enabled = true;
        mojo.printViolations = true;
        mojo.printIgnored = true;
        mojo.printCompliant = false;
        mojo.failBuildOnViolation = true;
        mojo.warnUnmatchedRules = true;
        mojo.checkCodeDependencies = true;
        mojo.checkPluginDependencies = false;
        mojo.includeCompileDependencies = true;
        mojo.includeRuntimeDependencies = true;
        mojo.includeProvidedDependencies = false;
        mojo.includeTestDependencies = false;
        mojo.includeSystemDependencies = false;
        mojo.strategy = RuleStrategy.passOnMatch;
    }

    private static MavenSession session() {
        final var session = mock(MavenSession.class);
        when(session.getProjectBuildingRequest()).thenReturn(new DefaultProjectBuildingRequest());
        return session;
    }

    private static ProjectBuilder builder(final Map<Artifact, List<License>> licenses)
            throws ProjectBuildingException {
        final var builder = mock(ProjectBuilder.class);
        when(builder.build(any(Artifact.class), any(ProjectBuildingRequest.class))).thenAnswer(invocation -> {
            final Artifact artifact = invocation.getArgument(0);
            final var dependencyProject = new MavenProject();
            dependencyProject.setLicenses(licenses.getOrDefault(artifact, List.of()));
            final var result = mock(ProjectBuildingResult.class);
            when(result.getProject()).thenReturn(dependencyProject);
            return result;
        });
        return builder;
    }

}
