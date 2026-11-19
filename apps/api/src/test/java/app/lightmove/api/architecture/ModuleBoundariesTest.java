package app.lightmove.api.architecture;

import app.lightmove.api.LightMoveApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

/**
 * The module boundaries, as Spring Modulith reads them off the compiled classes: no cycle between modules, no
 * dependency a module's {@code package-info.java} does not declare, and no reach into another module's internals.
 * How to fix each failure: {@code docs/architecture/modules.md}. The documentation test is tagged {@code docs} and
 * skipped by default: {@code ./mvnw test -Dtest=ModuleBoundariesTest -Dgroups=docs -DexcludedGroups=}.
 */
class ModuleBoundariesTest {

    static final ApplicationModules MODULES = ApplicationModules.of(LightMoveApplication.class);

    @Test
    @DisplayName("every module keeps to its declared dependencies and its neighbours' published API, with no cycle")
    void modulesKeepTheirBoundaries() {
        Violations violations = MODULES.detectViolations();
        if (violations.hasViolations()) {
            throw new AssertionError("""
                    Module boundaries broken — see docs/architecture/modules.md for what each message means and the \
                    fix (publish an event or a port, use the owner's facade, or move the type down a layer; never \
                    widen allowedDependencies upward or expose a repository).

                    """ + violations.getMessage());
        }
    }

    @Test
    @Tag("docs")
    @DisplayName("the module canvases and diagrams render, into target/spring-modulith-docs")
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
