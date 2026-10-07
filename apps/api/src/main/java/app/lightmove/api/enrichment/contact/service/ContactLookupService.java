package app.lightmove.api.enrichment.contact.service;

import app.lightmove.api.billing.credit.constant.CreditAction;
import app.lightmove.api.billing.credit.model.CreditCharge;
import app.lightmove.api.billing.credit.model.CreditReceipt;
import app.lightmove.api.billing.credit.service.CreditLedger;
import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.dto.CandidateEmailDto;
import app.lightmove.api.candidate.dto.CandidatePhoneDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.model.CandidateContactState;
import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ContactOutSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.contact.constant.ContactLookupOutcome;
import app.lightmove.api.enrichment.contact.dto.ContactLookupConfigResponse;
import app.lightmove.api.enrichment.contact.dto.ContactLookupResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One Find email / Find phone press. Not transactional: the vendor call must hold no connection
 * through a permit wait and retries. The guard against paying twice is the lookup timestamp, checked
 * here and again inside the write; two presses in flight can still both reach the provider, which the
 * per-user budget caps rather than a row lock across the call.
 *
 * <p>The workspace pays in contact credits only for what was found: the price is held before the provider
 * is asked, captured on a find and released on a miss or a failure. Presses in flight together share one
 * hold, so they spend once.
 */
@Service
@Slf4j
public class ContactLookupService {

    private final ContactFinder finder;
    private final CandidateService candidates;
    private final RateLimiter limiter;
    private final AuditService audit;
    private final CreditLedger ledger;
    private final ContactOutSettings config;

    public ContactLookupService(ContactFinder finder, CandidateService candidates, RateLimiter limiter,
                                AuditService audit, CreditLedger ledger, LightMoveProperties properties) {
        this.finder = finder;
        this.candidates = candidates;
        this.limiter = limiter;
        this.audit = audit;
        this.ledger = ledger;
        this.config = properties.enrichment().contactout();
    }

    public ContactLookupConfigResponse config() {
        return new ContactLookupConfigResponse(finder.isOffered());
    }

    public ContactLookupResponse findEmail(UUID userId, UUID workspaceId, UUID projectId,
                                           UUID candidateId, HttpServletRequest httpRequest) {
        CandidateContactState state = begin(ContactChannel.EMAIL, userId, workspaceId, projectId, candidateId);
        if (state.emailsAsked()) {
            return answered(alreadyAsked(!state.hasFoundEmails()), state.candidate(),
                    ContactChannel.EMAIL, false, 0, userId, workspaceId, projectId, candidateId, httpRequest);
        }
        requireBudget(ContactChannel.EMAIL, userId);
        CreditReceipt hold = holdFor(CreditAction.EMAIL_FOUND, ContactChannel.EMAIL, state, userId, workspaceId,
                projectId);
        CandidateResponse candidate;
        FoundEmails found;
        try {
            found = ask(() -> finder.findEmails(state.linkedinUrl()));
            candidate = candidates.applyFoundEmails(userId, projectId, candidateId, found);
        } catch (RuntimeException failed) {
            settle(workspaceId, hold, false);
            throw failed;
        }
        boolean foundNothing = foundNothing(
                candidate.contacts().emails().stream().map(CandidateEmailDto::source), found.source());
        long spent = settle(workspaceId, hold, !foundNothing);
        return answered(outcomeOf(foundNothing), candidate,
                ContactChannel.EMAIL, true, spent, userId, workspaceId, projectId, candidateId, httpRequest);
    }

    public ContactLookupResponse findPhone(UUID userId, UUID workspaceId, UUID projectId,
                                           UUID candidateId, HttpServletRequest httpRequest) {
        CandidateContactState state = begin(ContactChannel.PHONE, userId, workspaceId, projectId, candidateId);
        if (state.phonesAsked()) {
            return answered(alreadyAsked(!state.hasFoundPhones()), state.candidate(),
                    ContactChannel.PHONE, false, 0, userId, workspaceId, projectId, candidateId, httpRequest);
        }
        requireBudget(ContactChannel.PHONE, userId);
        CreditReceipt hold = holdFor(CreditAction.PHONE_FOUND, ContactChannel.PHONE, state, userId, workspaceId,
                projectId);
        CandidateResponse candidate;
        FoundPhones found;
        try {
            found = ask(() -> finder.findPhones(state.linkedinUrl()));
            candidate = candidates.applyFoundPhones(userId, projectId, candidateId, found);
        } catch (RuntimeException failed) {
            settle(workspaceId, hold, false);
            throw failed;
        }
        boolean foundNothing = foundNothing(
                candidate.contacts().phones().stream().map(CandidatePhoneDto::source), found.source());
        long spent = settle(workspaceId, hold, !foundNothing);
        return answered(outcomeOf(foundNothing), candidate,
                ContactChannel.PHONE, true, spent, userId, workspaceId, projectId, candidateId, httpRequest);
    }

    private CandidateContactState begin(ContactChannel channel, UUID userId, UUID workspaceId,
                                        UUID projectId, UUID candidateId) {
        if (!finder.isOffered()) {
            throw ApiException.of(ErrorCode.CONTACT_LOOKUP_UNAVAILABLE);
        }
        CandidateContactState state = candidates.contactStateOf(workspaceId, projectId, candidateId);
        if (state.doNotContact()) {
            throw ApiException.of(ErrorCode.PERSON_DO_NOT_CONTACT);
        }
        if (LinkedInUrls.profileSlugOrNull(state.linkedinUrl()) == null) {
            throw ApiException.of(ErrorCode.CONTACT_LOOKUP_NO_PROFILE);
        }
        return state;
    }

    /**
     * Per user and channel, so one caller cannot script the endpoint down a whole grid. Taken just before
     * the vendor call: a press answered off the row or refused for want of a profile must cost nothing.
     */
    private void requireBudget(ContactChannel channel, UUID userId) {
        boolean isWithinBudget = limiter.tryAcquire(
                "contact-lookup-%s:user:%s".formatted(channel.value(), userId),
                config.lookupsPerUserPerMinute(), Duration.ofMinutes(1));
        if (!isWithinBudget) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }

    /**
     * Keyed on the person, the channel and how many of their holds were released before: presses in flight
     * together share the key and so one hold, and a press after a failed one opens a fresh hold rather than
     * replaying the released one. Refused with {@code INSUFFICIENT_CREDITS} before the provider is asked.
     */
    private CreditReceipt holdFor(CreditAction action, ContactChannel channel, CandidateContactState state,
                                  UUID userId, UUID workspaceId, UUID projectId) {
        UUID personId = state.candidate().personId();
        long attempt = ledger.releasedHoldsOf(workspaceId, personId, action);
        String key = "contact:%s:%s:%d".formatted(personId, channel.value(), attempt);
        return ledger.hold(new CreditCharge(workspaceId, action, key, userId, projectId, personId));
    }

    /**
     * Captures the hold when something was found and releases it otherwise, answering the credits spent. A hold a
     * racing press settled the other way meanwhile is left as it stands.
     */
    private long settle(UUID workspaceId, CreditReceipt hold, boolean found) {
        try {
            if (!found) {
                ledger.release(workspaceId, hold.holdId());
                return 0;
            }
            return ledger.capture(workspaceId, hold.holdId()).credits();
        } catch (ApiException settled) {
            if (settled.getCode() != ErrorCode.CREDIT_HOLD_SETTLED) {
                throw settled;
            }
            log.warn("Contact lookup found its credit hold {} already settled", hold.holdId());
            return 0;
        }
    }

    private static ContactLookupOutcome alreadyAsked(boolean foundNothing) {
        return foundNothing ? ContactLookupOutcome.NONE : ContactLookupOutcome.HELD;
    }

    /** Read off the row the write answered with, so when two presses race the loser reports the winner's outcome. */
    private static ContactLookupOutcome outcomeOf(boolean foundNothing) {
        return foundNothing ? ContactLookupOutcome.NONE : ContactLookupOutcome.FOUND;
    }

    /** What a person typed is not the provider's answer. */
    private static boolean foundNothing(Stream<String> sources, String provider) {
        return sources.noneMatch(source -> source.equalsIgnoreCase(provider));
    }

    private <T> T ask(Supplier<T> call) {
        try {
            return call.get();
        } catch (VendorException failed) {
            throw refusalFor(failed);
        }
    }

    /** A spent quota is told apart: only it leaves the press worth retrying after a top-up. */
    private ApiException refusalFor(VendorException failed) {
        return switch (failed.getKind()) {
            case QUOTA_EXHAUSTED -> ApiException.of(ErrorCode.CONTACT_LOOKUP_NO_CREDITS);
            case CREDENTIALS -> {
                log.error("Contact lookup was refused by the provider: {}", failed.getKind());
                yield ApiException.of(ErrorCode.CONTACT_LOOKUP_UNAVAILABLE);
            }
            default -> {
                log.warn("Contact lookup failed: {}", failed.getKind());
                yield ApiException.of(ErrorCode.CONTACT_LOOKUP_FAILED);
            }
        };
    }

    private ContactLookupResponse answered(ContactLookupOutcome outcome, CandidateResponse candidate,
                                           ContactChannel channel, boolean asked, long creditsSpent, UUID userId,
                                           UUID workspaceId, UUID projectId, UUID candidateId,
                                           HttpServletRequest httpRequest) {
        audit.projectEvent(ProjectEventType.CANDIDATE_CONTACT_LOOKED_UP, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("channel", channel.value())
                .detail("outcome", outcome.value())
                .detail("asked", String.valueOf(asked))
                .detail("creditsSpent", String.valueOf(creditsSpent))
                .record();
        return new ContactLookupResponse(outcome.value(), candidate, creditsSpent,
                ledger.balanceOf(workspaceId).available());
    }
}
