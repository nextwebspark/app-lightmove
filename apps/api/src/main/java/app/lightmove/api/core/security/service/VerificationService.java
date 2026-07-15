package app.lightmove.api.core.security.service;

import app.lightmove.api.core.security.constant.TokenPurpose;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.VerificationToken;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.repository.VerificationTokenRepository;
import app.lightmove.api.core.security.model.EmailVerifiedEvent;
import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves that a user controls the email address they signed up with.
 *
 * <p>This is not a nicety here. A user's email domain decides which organisation they belong to, so
 * verification is the step that turns "I typed sara@nextwebspark.com" into evidence that the person
 * actually works at NextWebSpark. Until it happens, {@code require-verified-email} keeps them out of
 * every workspace endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final UserRepository users;
    private final VerificationTokenRepository tokens;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final ApplicationEventPublisher events;

    /**
     * Issues a fresh verification link and emails it.
     *
     * <p>Any outstanding token is burned first, so a user who clicks "resend" three times ends up with
     * exactly one working link rather than three live credentials scattered across their inbox.
     */
    @Transactional
    public void sendVerificationEmail(User user, HttpServletRequest request) {
        Instant now = Instant.now();
        tokens.consumeOutstanding(user.getId(), TokenPurpose.EMAIL_VERIFICATION, now);

        String plaintext = Tokens.generate();
        tokens.save(VerificationToken.issue(
                user.getId(),
                Tokens.hash(plaintext),
                TokenPurpose.EMAIL_VERIFICATION,
                now.plus(properties.auth().verificationTokenTtl())));

        // The link points at the SPA, not at the API. The frontend owns the "verifying…" screen and
        // then calls the API — which keeps the user inside the app rather than staring at raw JSON.
        String link = "%s/auth/verify?token=%s".formatted(
                properties.web().baseUrl(),
                URLEncoder.encode(plaintext, StandardCharsets.UTF_8));

        emailSender.send(templates.verifyEmail(user.getEmail(), user.getFullName(), link));

        audit.event(AuditEventType.EMAIL_VERIFICATION_SENT)
                .actor(user.getId())
                .from(request)
                .record();
    }

    /**
     * Redeems a verification link.
     *
     * @return the user who was verified.
     */
    @Transactional
    public User verify(String plaintextToken, HttpServletRequest request) {
        Instant now = Instant.now();

        VerificationToken token = tokens.findByTokenHash(Tokens.hash(plaintextToken))
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_INVALID));

        if (!token.isRedeemable(now)) {
            // Consumed and expired are separated for the user's sake: "this link has expired" tells
            // them to request another, where "not valid" would leave them stuck guessing.
            throw ApiException.of(token.getConsumedAt() != null
                    ? ErrorCode.TOKEN_INVALID
                    : ErrorCode.TOKEN_EXPIRED);
        }
        if (token.getPurpose() != TokenPurpose.EMAIL_VERIFICATION) {
            // A password-reset token must not double as a verification token, or the weaker flow
            // becomes a way into the stronger one.
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Wrong token purpose: " + token.getPurpose());
        }

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Token references a missing user"));

        token.consume(now);
        user.markEmailVerified(now);

        log.info("Email verified for user {}", user.getId());
        audit.event(AuditEventType.EMAIL_VERIFIED).actor(user.getId()).from(request).record();

        // The signup wizard, if they filled it in before verifying, becomes real here — the workspace is
        // created, or the join request reaches an admin's queue. Published rather than called: what
        // happens next belongs to the workspace feature, and auth has no business knowing it exists.
        //
        // Synchronous, so it commits with this transaction. A user who clicks their link and lands in a
        // verified account with no organisation, because a background listener failed quietly, has
        // nowhere to go and no way to retry.
        events.publishEvent(new EmailVerifiedEvent(user.getId(), request));

        return user;
    }

    /**
     * Resends the link.
     *
     * <p>Succeeds silently for an unknown address, and for one that is already verified. Reporting
     * either would turn this endpoint into an account-enumeration oracle: anyone could feed it a list
     * of addresses and learn which are LightMove customers. Rate limiting is applied by the caller.
     */
    @Transactional
    public void resend(String email, HttpServletRequest request) {
        String normalised = EmailAddressValidator.normalise(email);

        users.findByEmail(normalised)
                .filter(user -> !user.isEmailVerified())
                .ifPresentOrElse(
                        user -> sendVerificationEmail(user, request),
                        () -> log.debug("Verification resend requested for unknown or already-verified address"));
    }
}
