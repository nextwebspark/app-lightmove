package app.lightmove.api.core.security.registration;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.PendingRegistrationIssued;
import app.lightmove.api.core.security.model.ProviderIdentity;
import app.lightmove.api.core.security.token.Tokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds a first provider sign-in until the terms are ticked. Every refusal is {@link ErrorCode#REGISTRATION_EXPIRED},
 * unknown, spent or late alike: the cookie is the only thing that names a hold, and nothing was created either way.
 */
@Service
@RequiredArgsConstructor
public class PendingRegistrationService {

    /** Long enough to read the terms; short, because what it holds is a provider's word about someone. */
    public static final Duration TTL = Duration.ofMinutes(10);

    private final PendingRegistrationRepository registrations;
    private final Clock clock;

    @Transactional
    public PendingRegistrationIssued hold(ProviderIdentity identity) {
        Instant now = clock.instant();
        registrations.deleteExpiredBefore(now);
        registrations.deleteByProviderAndSubject(identity.provider(), identity.subject());

        String plaintext = Tokens.generate();
        registrations.save(PendingRegistration.hold(identity, Tokens.hash(plaintext), now, now.plus(TTL)));
        return new PendingRegistrationIssued(plaintext, TTL);
    }

    /** What the terms step shows: the provider's details, read without spending the hold. */
    @Transactional(readOnly = true)
    public ProviderIdentity peek(String token) {
        Instant now = clock.instant();
        return hashOf(token).flatMap(registrations::findByTokenHash)
                .filter(pending -> pending.isRedeemable(now))
                .map(PendingRegistration::identity)
                .orElseThrow(() -> ApiException.of(ErrorCode.REGISTRATION_EXPIRED));
    }

    /** Spent under its row lock, so two submits of one cookie register once. Joins the caller's transaction. */
    @Transactional
    public ProviderIdentity redeem(String token) {
        Instant now = clock.instant();
        PendingRegistration pending = hashOf(token).flatMap(registrations::findForUpdateByTokenHash)
                .filter(found -> found.isRedeemable(now))
                .orElseThrow(() -> ApiException.of(ErrorCode.REGISTRATION_EXPIRED));
        pending.consume(now);
        return pending.identity();
    }

    private static Optional<String> hashOf(String token) {
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(Tokens.hash(token));
    }
}
