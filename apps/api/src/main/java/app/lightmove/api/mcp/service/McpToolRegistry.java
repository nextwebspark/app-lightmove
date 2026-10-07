package app.lightmove.api.mcp.service;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.mcp.model.McpToolScopes;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * The {@code @McpTool} beans the server publishes, and the scopes each one declares. Fails closed at startup: a tool
 * declaring no {@link McpToolScopes}, or two tools under one name, would otherwise be served with no scope check.
 */
public class McpToolRegistry {

    private final List<Object> toolBeans;
    private final Map<String, Set<ApiKeyScope>> scopesByTool = new HashMap<>();

    public McpToolRegistry(List<Object> toolBeans) {
        this.toolBeans = List.copyOf(toolBeans);
        for (Object bean : toolBeans) {
            ReflectionUtils.doWithMethods(AopUtils.getTargetClass(bean), this::record,
                    method -> method.isAnnotationPresent(McpTool.class));
        }
    }

    /** Empty for a name no tool carries, which the transport answers as an unknown tool. */
    public Optional<Set<ApiKeyScope>> scopesOf(String toolName) {
        return Optional.ofNullable(scopesByTool.get(toolName));
    }

    public List<SyncToolSpecification> specificationsGuardedBy(McpToolGuard guard) {
        return SyncMcpAnnotationProviders.statelessToolSpecifications(toolBeans).stream()
                .map(tool -> SyncToolSpecification.builder().tool(withNullableEnums(tool.tool()))
                        .callHandler(tool.callHandler()).build())
                .map(tool -> guard.guard(tool, scopesOf(tool.tool().name()).orElseThrow(() ->
                        new IllegalStateException("MCP tool " + tool.tool().name() + " has no recorded scopes"))))
                .toList();
    }

    /**
     * The generator marks a nullable field {@code "type": ["string", "null"]} but leaves its {@code enum} without null,
     * so a public field such as an unrecorded gender would fail the SDK's output validation. A schema that allows null
     * allows it in its enum too.
     */
    private static Tool withNullableEnums(Tool tool) {
        if (tool.outputSchema() == null) {
            return tool;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) allowingNullInEnums(tool.outputSchema());
        return new Tool(tool.name(), tool.title(), tool.description(), tool.inputSchema(), schema, tool.annotations(),
                tool.meta(), tool.icons());
    }

    private static Object allowingNullInEnums(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), allowingNullInEnums(value)));
            if (copy.get("enum") instanceof List<?> values && !values.contains(null)
                    && copy.get("type") instanceof Collection<?> types && types.contains("null")) {
                List<Object> withNull = new ArrayList<>(values);
                withNull.add(null);
                copy.put("enum", withNull);
            }
            return copy;
        }
        if (node instanceof List<?> list) {
            return list.stream().map(McpToolRegistry::allowingNullInEnums).toList();
        }
        return node;
    }

    /** Named as the SDK names it: the annotation's name, or the method's where that is blank. */
    private void record(Method method) {
        String name = StringUtils.hasText(method.getAnnotation(McpTool.class).name())
                ? method.getAnnotation(McpTool.class).name() : method.getName();
        McpToolScopes scopes = method.getAnnotation(McpToolScopes.class);
        if (scopes == null) {
            throw new IllegalStateException("MCP tool " + name + " declares no @McpToolScopes; declare {} for none");
        }
        if (scopesByTool.putIfAbsent(name, Set.of(scopes.value())) != null) {
            throw new IllegalStateException("Two MCP tools are named " + name);
        }
    }
}
