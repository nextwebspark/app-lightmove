package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.McpSettings;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationContext;
import org.springframework.util.StringUtils;

/**
 * What an MCP authorization request must carry beyond OAuth's own rules, checked before anyone is asked to sign in and
 * again at consent:
 * <ul>
 *   <li>a redirect URI the client registered, exactly — or, for a listener on this machine, in any port
 *       ({@link RedirectUriRules});</li>
 *   <li>{@code resource} (RFC 8707) naming the MCP endpoint and nothing else — it becomes the token's audience;</li>
 *   <li>a PKCE challenge, S256 only;</li>
 *   <li>once someone is signed in, a {@code workspace_id} where they are staff ({@code API_KEY_MANAGE}, the same door
 *       an API key needs) — a pure client representative connects no AI app.</li>
 * </ul>
 */
public class McpAuthorizationRules {

    public static final String RESOURCE_PARAMETER = "resource";
    public static final String INVALID_TARGET = "invalid_target";
    private static final String S256 = "S256";

    private final McpServerIdentity identity;
    private final WorkspaceAccess access;
    private final OAuth2AuthorizationService authorizations;

    public McpAuthorizationRules(McpServerIdentity identity, WorkspaceAccess access,
                                 OAuth2AuthorizationService authorizations) {
        this.identity = identity;
        this.access = access;
        this.authorizations = authorizations;
    }

    /** The authorize request's whole check: the redirect, the framework's own scope check, then ours. */
    public Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> requestValidator() {
        Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> redirect = this::validateRedirectUri;
        return redirect.andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR)
                .andThen(this::validateRequest);
    }

    /**
     * Stands in for the framework's redirect check, which lets only an IP literal change port and so refused
     * {@code http://localhost} on the port a command-line client picked. A refusal names no redirect, so the error is
     * shown here and never sent to an address nobody proved.
     */
    public void validateRedirectUri(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        String requested = request.getRedirectUri();
        if (!StringUtils.hasText(requested)) {
            OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR.accept(context);
            return;
        }
        if (!RedirectUriRules.matches(context.getRegisteredClient().getRedirectUris(), requested)) {
            OAuth2AuthorizationCodeRequestAuthenticationToken unredirectable =
                    new OAuth2AuthorizationCodeRequestAuthenticationToken(request.getAuthorizationUri(),
                            request.getClientId(), (Authentication) request.getPrincipal(), null, request.getState(),
                            request.getScopes(), request.getAdditionalParameters());
            unredirectable.setAuthenticated(true);
            throw refusal(OAuth2ErrorCodes.INVALID_REQUEST, "OAuth 2.0 Parameter: " + OAuth2ParameterNames.REDIRECT_URI,
                    unredirectable);
        }
    }

    /** Runs after the redirect and scope checks, so the redirect URI an error is sent back to is already proven. */
    public void validateRequest(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        Map<String, Object> parameters = request.getAdditionalParameters();

        if (!namesResource(identity, single(parameters.get(RESOURCE_PARAMETER)))) {
            throw refusal(INVALID_TARGET, "resource must name the Uncava MCP server", request);
        }
        if (single(parameters.get(PkceParameterNames.CODE_CHALLENGE)) == null) {
            throw refusal(OAuth2ErrorCodes.INVALID_REQUEST, "code_challenge is required", request);
        }
        if (!S256.equals(single(parameters.get(PkceParameterNames.CODE_CHALLENGE_METHOD)))) {
            throw refusal(OAuth2ErrorCodes.INVALID_REQUEST, "code_challenge_method must be S256", request);
        }

        Authentication principal = (Authentication) request.getPrincipal();
        if (isSignedIn(principal) && !isEligible(principal.getName(),
                HashingAuthorizationService.parseUuid(single(parameters.get(
                        HashingAuthorizationService.WORKSPACE_PARAMETER))))) {
            throw refusal(OAuth2ErrorCodes.ACCESS_DENIED, "Not a workspace this account can connect", request);
        }
    }

    /** The consent may come long after the request: membership is read again, as every guard reads it. */
    public void validateConsent(OAuth2AuthorizationConsentAuthenticationContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        Authentication principal = (Authentication) context.getAuthentication().getPrincipal();
        if (isEligible(principal.getName(), HashingAuthorizationService.workspaceOf(authorization))) {
            return;
        }
        // The request is spent either way: left behind, it would sit until the purge with nothing able to use it.
        authorizations.remove(authorization);
        OAuth2AuthorizationRequest request = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        OAuth2AuthorizationCodeRequestAuthenticationToken original = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                request.getAuthorizationUri(), request.getClientId(), principal, request.getRedirectUri(),
                request.getState(), request.getScopes(), request.getAdditionalParameters());
        throw refusal(OAuth2ErrorCodes.ACCESS_DENIED, "Not a workspace this account can connect", original);
    }

    /** RFC 8707 compares URIs; one trailing slash more or less names the same resource. */
    static boolean namesResource(McpServerIdentity identity, String resource) {
        return resource != null && McpSettings.stripTrailingSlash(identity.resourceUrl())
                .equals(McpSettings.stripTrailingSlash(resource));
    }

    private boolean isEligible(String principalName, Optional<UUID> workspaceId) {
        Optional<UUID> userId = HashingAuthorizationService.parseUuid(principalName);
        return userId.isPresent() && workspaceId.isPresent()
                && access.holdsAction(userId.get(), workspaceId.get(), WorkspaceAction.API_KEY_MANAGE);
    }

    private static boolean isSignedIn(Authentication principal) {
        return principal != null && principal.isAuthenticated() && !(principal instanceof AnonymousAuthenticationToken);
    }

    private static String single(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException refusal(
            String code, String description, OAuth2AuthorizationCodeRequestAuthenticationToken request) {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error(code, description, null),
                request);
    }
}
