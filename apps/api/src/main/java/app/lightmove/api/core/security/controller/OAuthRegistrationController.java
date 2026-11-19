package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.dto.CompleteRegistrationRequest;
import app.lightmove.api.core.security.dto.MfaChallengeResponse;
import app.lightmove.api.core.security.dto.PendingRegistrationResponse;
import app.lightmove.api.core.security.mfa.MfaChallengeCookieFactory;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.MfaChallengeIssued;
import app.lightmove.api.core.security.model.ProviderIdentity;
import app.lightmove.api.core.security.registration.PendingRegistrationCookieFactory;
import app.lightmove.api.core.security.registration.PendingRegistrationService;
import app.lightmove.api.core.security.service.AuthResponseAssembler;
import app.lightmove.api.core.security.service.ProviderSignInService;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The terms step of a first sign-in with a provider (#928). Authenticated by the held registration's cookie alone, as
 * the code step is by its challenge's; the submit keeps the auth chain's double-submit check, since that cookie is
 * what creates an account.
 */
@RestController
@RequestMapping("/api/v1/auth/oauth/registration")
@RequiredArgsConstructor
public class OAuthRegistrationController {

    private final PendingRegistrationService pendingRegistrations;
    private final ProviderSignInService providerSignIn;
    private final PendingRegistrationCookieFactory pendingRegistrationCookie;
    private final RefreshCookieFactory refreshCookie;
    private final MfaChallengeCookieFactory mfaChallengeCookie;
    private final AuthResponseAssembler assembler;

    @GetMapping
    public PendingRegistrationResponse pending(
            @CookieValue(name = PendingRegistrationCookieFactory.NAME, required = false) String registrationToken) {
        ProviderIdentity identity = pendingRegistrations.peek(registrationToken);
        return new PendingRegistrationResponse(
                identity.provider().toLowerCase(Locale.ROOT), identity.email(), identity.fullName());
    }

    /** A session, or a challenge where the address turned out to be a factor holder's account meanwhile. */
    @PostMapping
    public ResponseEntity<?> complete(
            @Valid @RequestBody CompleteRegistrationRequest request,
            @CookieValue(name = PendingRegistrationCookieFactory.NAME, required = false) String registrationToken,
            HttpServletRequest httpRequest) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, pendingRegistrationCookie.expire().toString());
        return switch (providerSignIn.completeRegistration(registrationToken, httpRequest)) {
            case AuthenticatedSession session -> response
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.create(session.tokens().refreshToken()).toString())
                    .body(assembler.assemble(session.tokens(), session.user(), session.membership()));
            case MfaChallengeIssued challenge -> response
                    .header(HttpHeaders.SET_COOKIE, mfaChallengeCookie.create(challenge).toString())
                    .body(new MfaChallengeResponse(true, challenge.expiresIn().toSeconds()));
        };
    }
}
