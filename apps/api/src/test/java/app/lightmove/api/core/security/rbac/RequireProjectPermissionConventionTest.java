package app.lightmove.api.core.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code @RequireProjectPermission} binds {@code #projectId} by parameter name. A handler naming it
 * anything else compiles, and SpEL would hand the authorizer a null project.
 */
class RequireProjectPermissionConventionTest {

    @Test
    @DisplayName("every project-gated handler declares a UUID parameter named projectId")
    void everyGatedHandlerNamesItsProjectId() throws ClassNotFoundException {
        List<String> gated = new ArrayList<>();
        List<String> offenders = new ArrayList<>();
        for (Method method : controllerMethods()) {
            if (!method.isAnnotationPresent(RequireProjectPermission.class)) {
                continue;
            }
            String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            gated.add(name);
            boolean named = Arrays.stream(method.getParameters())
                    .anyMatch(parameter -> isProjectId(parameter));
            if (!named) {
                offenders.add(name);
            }
        }
        assertThat(gated).hasSizeGreaterThan(50);
        assertThat(offenders).isEmpty();
    }

    private static boolean isProjectId(Parameter parameter) {
        return parameter.isNamePresent() && parameter.getName().equals("projectId")
                && parameter.getType() == UUID.class;
    }

    private static List<Method> controllerMethods() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Method> methods = new ArrayList<>();
        for (var definition : scanner.findCandidateComponents("app.lightmove.api")) {
            methods.addAll(Arrays.asList(Class.forName(definition.getBeanClassName()).getDeclaredMethods()));
        }
        return methods;
    }
}
