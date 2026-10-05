package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationContext;

/**
 * What an MCP authorization request must carry beyond OAuth's own rules, checked before anyone is asked to sign in and
 * again at consent:
 * <ul>
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

    public McpAuthorizationRules(McpServerIdentity identity, WorkspaceAccess access) {
        this.identity = identity;
        this.access = access;
    }

    /** Runs after the framework's own validator, so the redirect URI an error is sent back to is already proven. */
    public void validateRequest(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        Map<String, Object> parameters = request.getAdditionalParameters();

        if (!identity.resourceUrl().equals(single(parameters.get(RESOURCE_PARAMETER)))) {
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
        OAuth2AuthorizationRequest request = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        OAuth2AuthorizationCodeRequestAuthenticationToken original = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                request.getAuthorizationUri(), request.getClientId(), principal, request.getRedirectUri(),
                request.getState(), request.getScopes(), request.getAdditionalParameters());
        throw refusal(OAuth2ErrorCodes.ACCESS_DENIED, "Not a workspace this account can connect", original);
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
