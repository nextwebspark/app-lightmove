package app.lightmove.api.mcp.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.PublicApiProblemWriter;
import app.lightmove.api.mcp.constant.McpCredentialKind;
import app.lightmove.api.mcp.model.McpCaller;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MCP spec's step-up: an OAuth token calling a tool it lacks a scope for is answered 403 {@code insufficient_scope},
 * naming the scopes, so its client can authorize again for them. An API key cannot step up — its owner edits it — so it
 * passes on to the tool guard, which answers with a result naming the scope. Reads the body the request-limit filter
 * buffered, so the transport still reads it whole.
 */
@RequiredArgsConstructor
public class McpScopeStepUpFilter extends OncePerRequestFilter {

    private final McpToolRegistry tools;
    private final JsonMapper json;
    private final String resourceMetadataUrl;
    private final PublicApiProblemWriter problems;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof McpCaller caller)
                || caller.credentialKind() != McpCredentialKind.OAUTH) {
            chain.doFilter(request, response);
            return;
        }
        String toolName = calledToolOf(request);
        Set<ApiKeyScope> needed = toolName == null ? Set.of() : tools.scopesOf(toolName);
        if (needed.stream().allMatch(caller::holds)) {
            chain.doFilter(request, response);
            return;
        }
        String scopes = needed.stream().map(ApiKeyScope::value).sorted().collect(Collectors.joining(" "));
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\", scope=\"" + scopes
                + "\", resource_metadata=\"" + resourceMetadataUrl + "\"");
        problems.write(request, response, ErrorCode.MCP_SCOPE_INSUFFICIENT);
    }

    /** The tool a single {@code tools/call} names; anything else, a batch or a body that is not JSON, names none. */
    private String calledToolOf(HttpServletRequest request) throws IOException {
        JsonNode message;
        try {
            message = json.readTree(request.getInputStream());
        } catch (JacksonException notJson) {
            return null;
        }
        if (message == null || !message.isObject() || !"tools/call".equals(message.path("method").asString(null))) {
            return null;
        }
        return message.path("params").path("name").asString(null);
    }
}
