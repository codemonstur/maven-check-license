package checklicense;

import org.apache.maven.artifact.Artifact;
import org.junit.jupiter.api.Test;

import static checklicense.Fixtures.artifact;
import static org.junit.jupiter.api.Assertions.*;

public class SelectArtifactsTest {

    @Test
    public void everyScopeFollowsItsOwnFlag() {
        final var mojo = new MavenCheckLicense();
        mojo.includeCompileDependencies = true;
        mojo.includeRuntimeDependencies = false;
        mojo.includeProvidedDependencies = false;
        mojo.includeTestDependencies = false;
        mojo.includeSystemDependencies = false;

        assertTrue(mojo.selectArtifacts(scope("compile")));
        assertFalse(mojo.selectArtifacts(scope("runtime")));
        assertFalse(mojo.selectArtifacts(scope("provided")));
        assertFalse(mojo.selectArtifacts(scope("test")));
        assertFalse(mojo.selectArtifacts(scope("system")));
    }

    @Test
    public void testAndSystemScopesCanBeEnabled() {
        final var mojo = new MavenCheckLicense();
        mojo.includeTestDependencies = true;
        mojo.includeSystemDependencies = true;

        assertTrue(mojo.selectArtifacts(scope("test")));
        assertTrue(mojo.selectArtifacts(scope("system")));
    }

    @Test
    public void scopesAreMatchedCaseInsensitive() {
        final var mojo = new MavenCheckLicense();
        mojo.includeCompileDependencies = false;
        mojo.includeTestDependencies = true;

        assertFalse(mojo.selectArtifacts(scope("Compile")));
        assertTrue(mojo.selectArtifacts(scope("TEST")));
    }

    @Test
    public void unknownAndMissingScopesAreIncluded() {
        final var mojo = new MavenCheckLicense();

        assertTrue(mojo.selectArtifacts(scope(null)));
        assertTrue(mojo.selectArtifacts(scope("bogus")));
    }

    private static Artifact scope(final String scope) {
        return artifact(null, null, null, scope);
    }

}
