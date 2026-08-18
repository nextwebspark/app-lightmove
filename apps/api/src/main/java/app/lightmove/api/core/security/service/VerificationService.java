package app.lightmove.api.core.security.service;

import app.lightmove.api.core.security.constant.TokenPurpose;
import app.lightmove.api.core.security.constant.UserStatus;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.VerificationToken;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.repository.VerificationTokenRepository;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final VerificationTokenRepository verificationTokens;
    private final WorkspaceMemberRepository members;
    private final TokenService tokens;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuditService audit;
    private final LightMoveProperties properties;

    /**
     * Issues a fresh verification link and emails it.
     *
     * <p>Any outstanding token is burned first, so a user who clicks "resend" three times ends up with
     * exactly one working link rather than three live credentials scattered across their inbox.
     */
    @Transactional
    public void sendVerificationEmail(User user, HttpServletRequest request) {
        Instant now = Instant.now();
        verificationTokens.consumeOutstanding(user.getId(), TokenPurpose.EMAIL_VERIFICATION, now);

        String plaintext = Tokens.generate();
        verificationTokens.save(VerificationToken.issue(
                user.getId(),
                Tokens.hash(plaintext),
                TokenPurpose.EMAIL_VERIFICATION,
                now.plus(properties.auth().verificationTokenTtl())));

        // The link points at the SPA, not at the API. The frontend owns the "verifying…" screen and
        // then calls the API — which keeps the user inside the app rather than staring at raw JSON.
        String link = "%s/auth/verify?token=%s".formatted(
                properties.web().baseUrl(),
                URLEncoder.encode(plaintext, StandardCharsets.UTF_8));

        emailSender.send(templates.buildVerificationEmail(user.getEmail(), user.getFullName(), link));

        audit.event(AuthEventType.EMAIL_VERIFICATION_SENT)
                .actor(user.getId())
                .from(request)
                .record();
    }

    /**
     * Redeems a verification link and signs the user straight in.
     *
     * <p>Issuing a session here is the same judgement {@code PasswordResetService.reset} and invited
     * signup already made: a token mailed only to this address proves what a login would prove. It
     * matters because the mail client opens the link in whatever browser it likes — usually not the one
     * that filled in signup, which has the only session that existed.
     *
     * @return a session for the user who was verified.
     */
    @Transactional
    public AuthenticatedSession verify(String plaintextToken, HttpServletRequest request) {
        Instant now = Instant.now();

        VerificationToken token = verificationTokens.findByTokenHash(Tokens.hash(plaintextToken))
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

        // Before consume, and mirroring PasswordResetService.reset: this method issues a session, so it
        // owes the same status check every other session-minting path makes. Nothing sets SUSPENDED
        // today, which is exactly why it is easy to leave out — and why whoever builds the suspension
        // surface would inherit a link that signs a suspended account straight back in.
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
            audit.event(AuthEventType.EMAIL_VERIFIED).failed().actor(user.getId()).from(request)
                    .reason("status_" + user.getStatus()).record();
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        token.consume(now);
        user.markEmailVerified(now);

        log.info("Email verified for user {}", user.getId());
        audit.event(AuthEventType.EMAIL_VERIFIED).actor(user.getId()).from(request).record();

        // Usually null: verification gates the creator, whose organisation is the step after this one.
        WorkspaceMember membership = members.findByUserIdAndStatus(user.getId(), MemberStatus.ACTIVE)
                .orElse(null);

        return tokens.issue(user, membership, request);
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
