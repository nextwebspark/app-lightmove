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
import java.util.function.Function;
import java.util.function.Predicate;
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
 * <p>The price is held before the provider is asked and spent only on a find.
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
        Press press = new Press(ContactChannel.EMAIL, CreditAction.EMAIL_FOUND, userId, workspaceId, projectId,
                candidateId);
        return lookUp(press, CandidateContactState::emailsAsked, CandidateContactState::hasFoundEmails, state -> {
            FoundEmails found = ask(() -> finder.findEmails(state.linkedinUrl()));
            CandidateResponse candidate = candidates.applyFoundEmails(userId, projectId, candidateId, found);
            return new Stored(candidate, foundNothing(
                    candidate.contacts().emails().stream().map(CandidateEmailDto::source), found.source()));
        }, httpRequest);
    }

    public ContactLookupResponse findPhone(UUID userId, UUID workspaceId, UUID projectId,
                                           UUID candidateId, HttpServletRequest httpRequest) {
        Press press = new Press(ContactChannel.PHONE, CreditAction.PHONE_FOUND, userId, workspaceId, projectId,
                candidateId);
        return lookUp(press, CandidateContactState::phonesAsked, CandidateContactState::hasFoundPhones, state -> {
            FoundPhones found = ask(() -> finder.findPhones(state.linkedinUrl()));
            CandidateResponse candidate = candidates.applyFoundPhones(userId, projectId, candidateId, found);
            return new Stored(candidate, foundNothing(
                    candidate.contacts().phones().stream().map(CandidatePhoneDto::source), found.source()));
        }, httpRequest);
    }

    private ContactLookupResponse lookUp(Press press, Predicate<CandidateContactState> asked,
                                         Predicate<CandidateContactState> hasFound,
                                         Function<CandidateContactState, Stored> askAndStore,
                                         HttpServletRequest httpRequest) {
        CandidateContactState state = begin(press);
        if (asked.test(state)) {
            return answered(press, alreadyAsked(!hasFound.test(state)), state.candidate(), false, 0, httpRequest);
        }
        requireBudget(press.channel(), press.userId());
        UUID personId = state.candidate().personId();
        CreditReceipt hold = ledger.hold(chargeOf(press, personId));
        Stored stored;
        try {
            stored = askAndStore.apply(state);
        } catch (RuntimeException failed) {
            try {
                release(press, hold);
            } catch (RuntimeException releaseFailed) {
                failed.addSuppressed(releaseFailed);
            }
            throw failed;
        }
        long spent = stored.foundNothing() ? releaseAfterMiss(press, hold) : capture(press, hold, personId);
        return answered(press, outcomeOf(stored.foundNothing()), stored.candidate(), true, spent, httpRequest);
    }

    private CandidateContactState begin(Press press) {
        if (!finder.isOffered()) {
            throw ApiException.of(ErrorCode.CONTACT_LOOKUP_UNAVAILABLE);
        }
        CandidateContactState state =
                candidates.contactStateOf(press.workspaceId(), press.projectId(), press.candidateId());
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
     * together share the key and so one hold, and a press after a released one gets a fresh key. Two presses
     * straddling a release can still take different keys and both be charged.
     */
    private CreditCharge chargeOf(Press press, UUID personId) {
        long attempt = ledger.releasedHoldsOf(press.workspaceId(), personId, press.action());
        String key = "contact:%s:%s:%d".formatted(personId, press.channel().value(), attempt);
        return new CreditCharge(press.workspaceId(), press.action(), key, press.userId(), press.projectId(),
                personId);
    }

    /**
     * Spends a find's price. The contact is already stored, so a spend that fails is logged and the find goes
     * uncharged rather than answering an error over it; a hold a racing press released meanwhile is charged afresh.
     */
    private long capture(Press press, CreditReceipt hold, UUID personId) {
        try {
            return ledger.capture(press.workspaceId(), hold.holdId()).credits();
        } catch (RuntimeException failed) {
            if (failed instanceof ApiException refused && refused.getCode() == ErrorCode.CREDIT_HOLD_SETTLED) {
                return chargeAfresh(press, personId);
            }
            log.error("Contact lookup could not spend credit hold {}; the find goes uncharged", hold.holdId(), failed);
            return 0;
        }
    }

    private long chargeAfresh(Press press, UUID personId) {
        try {
            return ledger.charge(chargeOf(press, personId)).credits();
        } catch (RuntimeException refused) {
            log.warn("Contact lookup could not charge a find whose hold was released by a racing press", refused);
            return 0;
        }
    }

    /** The miss is already stored and a hold left open is swept after {@code hold-ttl}: a failure is only logged. */
    private long releaseAfterMiss(Press press, CreditReceipt hold) {
        try {
            release(press, hold);
        } catch (RuntimeException failed) {
            log.error("Contact lookup could not release credit hold {}", hold.holdId(), failed);
        }
        return 0;
    }

    /** A hold a racing press already captured is left spent. */
    private void release(Press press, CreditReceipt hold) {
        try {
            ledger.release(press.workspaceId(), hold.holdId());
        } catch (ApiException settled) {
            if (settled.getCode() != ErrorCode.CREDIT_HOLD_SETTLED) {
                throw settled;
            }
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

    private ContactLookupResponse answered(Press press, ContactLookupOutcome outcome, CandidateResponse candidate,
                                           boolean asked, long creditsSpent, HttpServletRequest httpRequest) {
        audit.projectEvent(ProjectEventType.CANDIDATE_CONTACT_LOOKED_UP, press.userId(), press.workspaceId(),
                        press.projectId(), httpRequest)
                .detail("candidateId", press.candidateId().toString())
                .detail("channel", press.channel().value())
                .detail("outcome", outcome.value())
                .detail("asked", String.valueOf(asked))
                .detail("creditsSpent", String.valueOf(creditsSpent))
                .record();
        return new ContactLookupResponse(outcome.value(), candidate, creditsSpent,
                ledger.balanceOf(press.workspaceId()).available());
    }

    private record Press(ContactChannel channel, CreditAction action, UUID userId, UUID workspaceId, UUID projectId,
                         UUID candidateId) {
    }

    private record Stored(CandidateResponse candidate, boolean foundNothing) {
    }
}
