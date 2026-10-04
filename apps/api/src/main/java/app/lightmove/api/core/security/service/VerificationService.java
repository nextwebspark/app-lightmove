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
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Proves a user controls their address; until then {@code require-verified-email} keeps them out of workspace endpoints. */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final UserRepository users;
    private final VerificationTokenRepository verificationTokens;
    private final WorkspaceSelection selection;
    private final TokenService tokens;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuditService audit;
    private final LightMoveProperties properties;

    /** Burns any outstanding token first, so repeated resends leave one live credential. */
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

        String link = "%s/auth/verify?token=%s".formatted(
                properties.web().baseUrl(),
                URLEncoder.encode(plaintext, StandardCharsets.UTF_8));

        emailSender.send(templates.buildVerificationEmail(user.getEmail(), user.getFullName(), link));

        audit.event(AuthEventType.EMAIL_VERIFICATION_SENT)
                .actor(user.getId())
                .from(request)
                .record();
    }

    /** Signs the user in, as a reset does: the link often opens in a browser without the session. */
    @Transactional
    public AuthenticatedSession verify(String plaintextToken, HttpServletRequest request) {
        Instant now = Instant.now();

        VerificationToken token = verificationTokens.findByTokenHash(Tokens.hash(plaintextToken))
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_INVALID));

        if (!token.isRedeemable(now)) {
            throw ApiException.of(token.getConsumedAt() != null
                    ? ErrorCode.TOKEN_INVALID
                    : ErrorCode.TOKEN_EXPIRED);
        }
        if (token.getPurpose() != TokenPurpose.EMAIL_VERIFICATION) {
            // A reset token must not double as a verification token.
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Wrong token purpose: " + token.getPurpose());
        }

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Token references a missing user"));

        // This issues a session, so it owes reset's status check, though nothing sets SUSPENDED yet.
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
            audit.event(AuthEventType.EMAIL_VERIFIED).failed().actor(user.getId()).from(request)
                    .reason("status_" + user.getStatus()).record();
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        token.consume(now);
        user.markEmailVerified(now);

        log.info("Email verified for user {}", user.getId());
        audit.event(AuthEventType.EMAIL_VERIFIED).actor(user.getId()).from(request).record();

        return tokens.issue(user, selection.signIn(user), request);
    }

    /** Silent for an unknown or already-verified address, or this is an enumeration oracle. */
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
