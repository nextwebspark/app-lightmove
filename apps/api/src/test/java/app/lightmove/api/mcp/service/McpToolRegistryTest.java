package app.lightmove.api.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.mcp.model.McpToolScopes;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

class McpToolRegistryTest {

    @Test
    @DisplayName("a tool declaring no scopes fails the boot rather than being served with none")
    void undeclaredScopesFailClosed() {
        assertThatThrownBy(() -> new McpToolRegistry(List.of(new Undeclared())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tool_without_scopes");
    }

    @Test
    @DisplayName("two tools under one name fail the boot rather than one overwriting the other's scopes")
    void duplicateNamesFailClosed() {
        assertThatThrownBy(() -> new McpToolRegistry(List.of(new Declared(), new Declared())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Two MCP tools are named");
    }

    @Test
    @DisplayName("a blank name is the method's, as the SDK names it, and an unknown name has no scopes to read")
    void namesAsTheSdkDoes() {
        McpToolRegistry registry = new McpToolRegistry(List.of(new Declared()));
        assertThat(registry.scopesOf("namedByMethod")).contains(Set.of(ApiKeyScope.COMPANIES_READ));
        assertThat(registry.scopesOf("open_tool")).contains(Set.of());
        assertThat(registry.scopesOf("")).isEmpty();
        assertThat(registry.scopesOf("no_such_tool")).isEmpty();
    }

    static class Undeclared {

        @McpTool(name = "tool_without_scopes", description = "Reads something")
        public String read() {
            return "";
        }
    }

    static class Declared {

        @McpTool(name = "", description = "Reads companies")
        @McpToolScopes(ApiKeyScope.COMPANIES_READ)
        public String namedByMethod() {
            return "";
        }

        @McpTool(name = "open_tool", description = "Reads nothing")
        @McpToolScopes({})
        public String open() {
            return "";
        }
    }
}
