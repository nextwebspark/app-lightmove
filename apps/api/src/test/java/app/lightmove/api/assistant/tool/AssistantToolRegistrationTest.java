package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.assistant.service.AssistantEventSink;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ReflectionUtils;

/**
 * That no tool can be reached without the guard.
 *
 * <p>Two ways one could be, and both are closed here. A {@code @Tool} method on a class the toolset
 * never collects would never have its permission read, so the startup check that refuses an
 * undeclared tool would never see it. And an undecorated {@code ToolCallback} left anywhere in the
 * context is resolvable <i>by tool name</i> through Spring AI's shared resolver, which is why the
 * toolset builds them per turn and hands out nothing else.
 */
class AssistantToolRegistrationTest {

    private static final String FEATURE_PACKAGE = "app.lightmove.api";

    @Test
    @DisplayName("every @Tool method in the application is on a subject the toolset collects")
    void everyToolIsCollected() {
        Set<Class<?>> carryingTools = classesWithToolMethods();

        assertThat(carryingTools)
                .as("a @Tool outside AssistantToolSubject is never read for a permission, so it "
                        + "would ship unguarded")
                .allMatch(AssistantToolSubject.class::isAssignableFrom);
    }

    @Test
    @DisplayName("the toolset hands out guarded callbacks and nothing else")
    void handsOutOnlyGuardedCallbacks() {
        AssistantToolset toolset = new AssistantToolset(
                List.of(new AuthorisingToolCallbackTest.GuardedTools()), null, mockAudit());

        ToolCallback[] callbacks = toolset.forTurn(
                new AssistantToolCaller(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                noTrace());

        assertThat(callbacks).isNotEmpty().allMatch(AuthorisingToolCallback.class::isInstance);
    }

    @Test
    @DisplayName("the tool order is stable whatever order Spring hands the subjects in")
    void ordersToolsStably() {
        AssistantToolSubject zebra = new ZebraTools();
        AssistantToolSubject alpha = new AlphaTools();

        List<String> oneWay = toolNamesOf(List.of(zebra, alpha));
        List<String> theOther = toolNamesOf(List.of(alpha, zebra));

        // The tool list is the head of the cacheable prefix. Spring's injection order is not
        // contractual across restarts, and a prefix that reshuffles costs a cache miss per turn
        // while answering perfectly correctly — so nothing but this notices.
        assertThat(oneWay).isEqualTo(theOther).isSorted();
    }

    private static List<String> toolNamesOf(List<AssistantToolSubject> subjects) {
        return Arrays.stream(new AssistantToolset(subjects, null, mockAudit())
                        .forTurn(new AssistantToolCaller(UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID()), noTrace()))
                .map(tool -> tool.getToolDefinition().name())
                .toList();
    }

    @Test
    @DisplayName("every collected tool declares a permission, checked when the toolset is built")
    void everyCollectedToolDeclaresAPermission() {
        Set<Class<?>> subjects = classesWithToolMethods();

        assertThat(subjects).isNotEmpty();
        for (Class<?> subject : subjects) {
            Set<String> declared = new ToolPermissions(List.of(instantiate(subject))).toolNames();
            assertThat(declared).as(subject.getSimpleName() + " declares a permission per tool")
                    .hasSize(toolMethodCount(subject));
        }
    }

    /**
     * Scanned rather than listed. A list would be a second place to remember, and the failure it is
     * meant to catch is someone forgetting the first.
     */
    private static Set<Class<?>> classesWithToolMethods() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        return scanner.findCandidateComponents(FEATURE_PACKAGE).stream()
                .map(AssistantToolRegistrationTest::resolve)
                // Production classes only. This test's own fixtures are deliberately malformed tools,
                // and a scan that swept them up would fail on exactly what they exist to prove.
                .filter(AssistantToolRegistrationTest::isShipped)
                .filter(type -> toolMethodCount(type) > 0)
                .collect(Collectors.toSet());
    }

    private static boolean isShipped(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        return source != null && !source.getLocation().getPath().contains("test-classes");
    }

    private static int toolMethodCount(Class<?> type) {
        int[] found = {0};
        ReflectionUtils.doWithMethods(type, method -> {
            if (method.getAnnotation(Tool.class) != null) {
                found[0]++;
            }
        });
        return found[0];
    }

    private static Class<?> resolve(BeanDefinition definition) {
        try {
            return Class.forName(definition.getBeanClassName());
        } catch (ClassNotFoundException | NoClassDefFoundError unreadable) {
            return Object.class;
        }
    }

    /**
     * Built without its collaborators: only the annotations are read here, and a tool body would
     * need a database this test deliberately does not have.
     */
    private static Object instantiate(Class<?> subject) {
        return Arrays.stream(subject.getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == 0)
                .findFirst()
                .map(constructor -> {
                    try {
                        constructor.setAccessible(true);
                        return (Object) constructor.newInstance();
                    } catch (ReflectiveOperationException unusable) {
                        throw new IllegalStateException(unusable);
                    }
                })
                .orElseGet(() -> (Object) org.mockito.Mockito.mock(subject));
    }

    /** Named so an unsorted list would come back in subject order and give the game away. */
    static class ZebraTools implements AssistantToolSubject {

        @Tool(description = "zebra")
        @RequiresWorkspaceAction(WorkspaceAction.PROJECT_BROWSE)
        public String zebraTool(String query) {
            return query;
        }
    }

    static class AlphaTools implements AssistantToolSubject {

        @Tool(description = "alpha")
        @RequiresWorkspaceAction(WorkspaceAction.PROJECT_BROWSE)
        public String alphaTool(String query) {
            return query;
        }
    }

    private static AuditService mockAudit() {
        return org.mockito.Mockito.mock(AuditService.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    }

    private static AssistantEventSink noTrace() {
        return text -> {
        };
    }
}
