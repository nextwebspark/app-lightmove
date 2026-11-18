package app.lightmove.api.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.LightMoveApplication;
import com.tngtech.archunit.core.domain.JavaClass;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.Modulithic;
import org.springframework.modulith.core.NamedInterface;

/**
 * The rules that keep {@link ModuleBoundariesTest} meaningful: what a module may publish, that every module declares
 * what it depends on, that the declared graph has no cycle, and that the shared kernel stays at the bottom. Modulith
 * leaves open modules out of its cycle check, so core and common are pinned here instead.
 */
class ModuleConventionTest {

    private static final String BASE = "app.lightmove.api";
    private static final String API = "api";
    private static final Set<String> KERNEL = Set.of("core", "common");

    @Test
    @DisplayName("a module publishes no repository, controller or configuration type")
    void apiHoldsNoInternals() {
        List<String> offenders = new ArrayList<>();
        ModuleBoundariesTest.MODULES.stream()
                .filter(module -> !module.isOpen())
                .forEach(module -> module.getNamedInterfaces().stream()
                        .filter(NamedInterface::isNamed)
                        .flatMap(NamedInterface::asJavaClasses)
                        .filter(ModuleConventionTest::isInternal)
                        .forEach(type -> offenders.add(module.getIdentifier() + ": " + type.getName())));
        assertThat(offenders)
                .as("publish a facade service or a DTO instead (docs/architecture/modules.md)")
                .isEmpty();
    }

    @Test
    @DisplayName("every published interface is called api")
    void everyNamedInterfaceIsCalledApi() {
        List<String> offenders = new ArrayList<>();
        ModuleBoundariesTest.MODULES.stream()
                .filter(module -> !module.isOpen())
                .forEach(module -> module.getNamedInterfaces().stream()
                        .filter(NamedInterface::isNamed)
                        .filter(named -> !named.getName().equals(API))
                        .forEach(named -> offenders.add(module.getIdentifier() + "::" + named.getName())));
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("every top-level package is a module that declares its dependencies")
    void everyModuleDeclaresItsDependencies() throws IOException, URISyntaxException {
        List<String> undeclared = new ArrayList<>();
        for (String module : topLevelPackages()) {
            ApplicationModule declaration = declarationOf(module);
            if (declaration == null) {
                undeclared.add(module + ": no @ApplicationModule on its package-info.java");
            } else if (Arrays.asList(declaration.allowedDependencies()).contains(ApplicationModule.OPEN_TOKEN)) {
                undeclared.add(module + ": allowedDependencies left open");
            } else if (declaration.type() == ApplicationModule.Type.OPEN && !KERNEL.contains(module)) {
                undeclared.add(module + ": only core and common may be OPEN");
            }
        }
        assertThat(undeclared).isEmpty();
    }

    @Test
    @DisplayName("the shared kernel sits at the bottom: core depends on nothing, common on core alone")
    void kernelStaysAtTheBottom() {
        Modulithic modulithic = LightMoveApplication.class.getAnnotation(Modulithic.class);
        assertThat(modulithic.sharedModules()).containsExactlyInAnyOrder("core", "common");

        ApplicationModule core = declarationOf("core");
        assertThat(core.type()).isEqualTo(ApplicationModule.Type.OPEN);
        assertThat(core.allowedDependencies()).isEmpty();

        ApplicationModule common = declarationOf("common");
        assertThat(common.type()).isEqualTo(ApplicationModule.Type.OPEN);
        assertThat(common.allowedDependencies()).containsExactly("core");
    }

    /** Modulith allows every module its shared ones and skips open modules' cycles, so the kernel is checked here. */
    @Test
    @DisplayName("in the compiled classes, core uses no other module and common uses core alone")
    void kernelUsesNothingAbove() {
        assertThat(actualDependenciesOf("core")).isEmpty();
        assertThat(actualDependenciesOf("common")).isSubsetOf("core");
    }

    @Test
    @DisplayName("the declared dependency graph has no cycle, nor any module naming itself")
    void declaredGraphIsAcyclic() throws IOException, URISyntaxException {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (String module : topLevelPackages()) {
            ApplicationModule declaration = declarationOf(module);
            Set<String> targets = new HashSet<>();
            if (declaration != null) {
                for (String allowed : declaration.allowedDependencies()) {
                    targets.add(allowed.split("::")[0].trim());
                }
            }
            if (!KERNEL.contains(module)) {
                targets.addAll(KERNEL);
            }
            graph.put(module, targets);
        }
        assertThat(cycleIn(graph)).as("a cycle in the declared allowedDependencies").isEmpty();
    }

    private static Set<String> actualDependenciesOf(String module) {
        return ModuleBoundariesTest.MODULES.getModuleByName(module).orElseThrow()
                .getDirectDependencies(ModuleBoundariesTest.MODULES)
                .uniqueModules()
                .map(dependency -> dependency.getIdentifier().toString())
                .collect(Collectors.toSet());
    }

    private static boolean isInternal(JavaClass type) {
        String pkg = type.getPackageName();
        return pkg.contains(".repository") || pkg.contains(".controller") || pkg.contains(".config")
                || type.isAssignableTo(org.springframework.data.repository.Repository.class)
                || type.isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                || type.isAnnotatedWith(org.springframework.stereotype.Controller.class)
                || type.isAnnotatedWith(org.springframework.context.annotation.Configuration.class);
    }

    private static ApplicationModule declarationOf(String module) {
        try {
            return Class.forName(BASE + "." + module + ".package-info").getAnnotation(ApplicationModule.class);
        } catch (ClassNotFoundException missing) {
            return null;
        }
    }

    private static List<String> topLevelPackages() throws IOException, URISyntaxException {
        Path root = Path.of(LightMoveApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve(BASE.replace('.', '/'));
        try (Stream<Path> children = Files.list(root)) {
            List<String> modules = children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
            assertThat(modules).hasSizeGreaterThan(20);
            return modules;
        }
    }

    /** The first cycle found, as the path that closes it, or empty. */
    private static List<String> cycleIn(Map<String, Set<String>> graph) {
        Map<String, Integer> state = new HashMap<>();
        for (String start : graph.keySet()) {
            Deque<String> path = new ArrayDeque<>();
            List<String> cycle = visit(start, graph, state, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private static List<String> visit(String node, Map<String, Set<String>> graph, Map<String, Integer> state,
                                      Deque<String> path) {
        Integer seen = state.get(node);
        if (seen != null && seen == 2) {
            return List.of();
        }
        path.addLast(node);
        if (seen != null && seen == 1) {
            List<String> trail = new ArrayList<>(path);
            return trail.subList(trail.indexOf(node), trail.size());
        }
        state.put(node, 1);
        for (String next : graph.getOrDefault(node, Set.of())) {
            List<String> cycle = visit(next, graph, state, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        state.put(node, 2);
        path.removeLast();
        return List.of();
    }
}
