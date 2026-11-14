package app.lightmove.api.core.security.service;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.avatar.ProviderAvatarOffered;
import app.lightmove.api.core.security.constant.PrivacyPolicy;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.ProviderIdentity;
import app.lightmove.api.core.security.model.SignInOutcome;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.registration.PendingRegistrationService;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A provider's proven identity becomes a session, a code challenge, or — for someone we have never met — nothing yet:
 * the account is created only once they tick the terms (#928). Nothing here names a provider.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderSignInService {

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final SignInCompletion signIn;
    private final AuditService audit;
    private final UnverifiedAccountWipe unverifiedAccounts;
    private final ApplicationEventPublisher events;
    private final PendingRegistrationService pendingRegistrations;
    private final Clock clock;

    /**
     * Empty when nobody holds this identity or its address, which is the one case that needs the terms first. Matched
     * on the provider's subject before the address: matching on email alone would attach the account to whoever now
     * holds a recycled address.
     */
    @Transactional
    public Optional<SignInOutcome> signInKnown(ProviderIdentity identity, HttpServletRequest request) {
        Optional<User> user = identities.findByProviderAndProviderUserId(identity.provider(), identity.subject())
                .flatMap(found -> users.findById(found.getUserId()))
                .or(() -> users.findByEmail(identity.email()).map(existing -> link(existing, identity, request)));
        return user.map(found -> complete(found, identity, request));
    }

    /**
     * The terms step's submit: spends the hold and creates the account with the tick's time — unless the identity or
     * its address arrived meanwhile (another tab, a password signup), when it signs in or links exactly as the
     * callback would have. Two first sign-ins racing past the lookups lose on the unique address, as they always did.
     */
    @Transactional
    public SignInOutcome completeRegistration(String registrationToken, HttpServletRequest request) {
        ProviderIdentity identity = pendingRegistrations.redeem(registrationToken);
        return signInKnown(identity, request).orElseGet(() -> complete(register(identity, request), identity, request));
    }

    private User link(User existing, ProviderIdentity identity, HttpServletRequest request) {
        // The provider verified the address, so only the mailbox's owner could be here — but an unverified account may
        // have been opened by someone else on that address, and linking it with their password still working handed
        // them the owner's account (pre-account takeover).
        boolean wiped = !existing.isEmailVerified() && unverifiedAccounts.wipe(existing, request);
        identities.save(UserIdentity.link(existing.getId(), identity.provider(), identity.subject(),
                identity.email()));

        existing.markEmailVerified(clock.instant());

        log.info("Linked {} account to existing user {}", identity.provider(), existing.getId());
        audit.event(AuthEventType.OAUTH_ACCOUNT_LINKED).actor(existing.getId()).from(request)
                .detail("provider", identity.provider()).detail("unverifiedCredentialsWiped", wiped).record();
        return existing;
    }

    /** The work-email rule was applied when the hold was made, before anyone was asked to tick anything. */
    private User register(ProviderIdentity identity, HttpServletRequest request) {
        Instant now = clock.instant();
        User user = users.save(User.registerFederated(
                identity.email(), identity.fullName(), now, now, PrivacyPolicy.CURRENT_VERSION));
        identities.save(UserIdentity.link(user.getId(), identity.provider(), identity.subject(), identity.email()));

        String domain = EmailAddressValidator.domainOf(identity.email());
        log.info("Registered user {} via {}, claiming domain {}", user.getId(), identity.provider(), domain);
        audit.event(AuthEventType.USER_SIGNED_UP).actor(user.getId()).from(request)
                .detail("provider", identity.provider()).detail("domain", domain).record();
        return user;
    }

    private SignInOutcome complete(User user, ProviderIdentity identity, HttpServletRequest request) {
        if (!user.getStatus().canAuthenticate()) {
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        // Only backfilled, never overwritten: the name is editable here.
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            user.setFullName(identity.fullName());
        }

        // A challenge leaves the failed-attempt count as it is: only the right code clears it, as after a password.
        SignInOutcome outcome = signIn.complete(user, request);
        if (outcome instanceof AuthenticatedSession session) {
            offerPicture(user, identity);
            user.recordSuccessfulLogin(clock.instant());
            audit.event(AuthEventType.OAUTH_LOGIN_SUCCEEDED).actor(user.getId()).workspace(session.workspaceId())
                    .from(request).detail("provider", identity.provider()).record();
        }
        return outcome;
    }

    /**
     * A completed sign-in only: one stopped at the code step changes nothing of the account. Copied once the sign-in
     * commits — downloading here would hold its transaction open on the provider's CDN.
     */
    private void offerPicture(User user, ProviderIdentity identity) {
        if (identity.pictureUrl() != null && user.mayTakeAvatarFrom(identity.provider())) {
            events.publishEvent(new ProviderAvatarOffered(user.getId(), identity.provider(), identity.pictureUrl()));
        }
    }
}
