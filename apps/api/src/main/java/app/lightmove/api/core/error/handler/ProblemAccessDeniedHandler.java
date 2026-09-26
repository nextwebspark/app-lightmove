package app.lightmove.api.core.error.handler;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.service.Problems;

import app.lightmove.api.core.security.jwt.JwtPrincipalConverter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Gives Spring Security's filter-chain 403 a ProblemDetail body, which no {@code @RestControllerAdvice}
 * can; without it an unverified user got an empty 403 instead of {@link ErrorCode#EMAIL_NOT_VERIFIED}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper json;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException denial) throws IOException {

        ErrorCode code = classify(denial, SecurityContextHolder.getContext().getAuthentication());
        ProblemDetail problem = Problems.of(code);
        problem.setInstance(java.net.URI.create(request.getRequestURI()));

        log.debug("Denied {} {} → {}", request.getMethod(), request.getRequestURI(), code);

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(problem));
    }

    private ErrorCode classify(AccessDeniedException denial, Authentication authentication) {
        // Not a permissions problem: the SPA recovers by re-fetching /auth/csrf.
        if (denial instanceof CsrfException) {
            return ErrorCode.CSRF_TOKEN_INVALID;
        }

        if (isRealUser(authentication) && !isVerified(authentication)) {
            return ErrorCode.EMAIL_NOT_VERIFIED;
        }

        return ErrorCode.FORBIDDEN;
    }

    /** Anonymous authentication is still an Authentication, and {@code isAuthenticated()} returns true. */
    private static boolean isRealUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static boolean isVerified(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> JwtPrincipalConverter.VERIFIED_AUTHORITY.equals(granted.getAuthority()));
    }
}
