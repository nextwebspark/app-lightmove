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
 * Gives Spring Security's 403 a body.
 *
 * <p>Spring Security denies a request from inside the <b>filter chain</b>, long before the
 * DispatcherServlet. So a {@code @RestControllerAdvice} never sees it, and the default handler answers
 * {@code sendError(403)} — an empty body, {@code content-type: text/html}, no code, no detail. The
 * frontend switches on {@code code} and there was none, so every filter-level refusal reached the user
 * as "That request could not be completed."
 *
 * <p>That was not a cosmetic problem. The most consequential refusal in the product — an unverified user
 * touching workspace data — arrived as an unreadable 403, and the SPA had no way to tell them the one
 * thing they needed to hear: check your inbox. The right error already existed
 * ({@link ErrorCode#EMAIL_NOT_VERIFIED}, "Please verify your email address to continue") and nothing
 * could ever produce it, because the filter rejected the request before any code that could.
 *
 * <p>Bodies are built by {@link Problems}, the same factory {@link GlobalExceptionHandler} uses, so the
 * two routes cannot drift into two different error shapes.
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

    /**
     * Why the request was refused — which is the whole reason this class exists.
     *
     * <p>An authenticated user without {@code SCOPE_VERIFIED} was refused for one reason only: they have
     * not proved their email address. Telling them that is the difference between a screen they can act
     * on and a dead end.
     */
    private ErrorCode classify(AccessDeniedException denial, Authentication authentication) {
        // A rejected CSRF token is not a permissions problem, and must not be reported as one. The SPA
        // recovers from it by re-fetching /auth/csrf; it cannot recover from "you lack permission".
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
