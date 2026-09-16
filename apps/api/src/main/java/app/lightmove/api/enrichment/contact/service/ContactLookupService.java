package app.lightmove.api.enrichment.contact.service;

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
import app.lightmove.api.enrichment.contact.constant.ContactChannel;
import app.lightmove.api.enrichment.contact.constant.ContactLookupOutcome;
import app.lightmove.api.enrichment.contact.dto.ContactLookupConfigResponse;
import app.lightmove.api.enrichment.contact.dto.ContactLookupResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One press of Find email or Find phone: decide whether anything needs asking, ask if so, and hand
 * the answer to {@code candidate} to write.
 *
 * <p><b>Deliberately not transactional.</b> The vendor call must not hold a database connection while
 * a permit wait and two retries run, so the read and the write each open their own.
 *
 * <p>The audit row's {@code asked} says whether the provider was called, not whether a credit was
 * spent: a profile they hold nothing on answers 404 and costs nothing.
 *
 * <p>The guard against paying twice for one row is the timestamp {@code candidate} stamps, checked
 * here and again inside the write. Two presses genuinely in flight at once can still both reach the
 * provider — the browser disables the button, and the per-user budget caps what that could cost — but
 * nothing short of holding a row lock across the vendor call would close it, and that is worse.
 */
@Service
@Slf4j
public class ContactLookupService {

    private final ContactFinder finder;
    private final CandidateService candidates;
    private final RateLimiter limiter;
    private final AuditService audit;
    private final ContactOutSettings config;

    public ContactLookupService(ContactFinder finder, CandidateService candidates, RateLimiter limiter,
                                AuditService audit, LightMoveProperties properties) {
        this.finder = finder;
        this.candidates = candidates;
        this.limiter = limiter;
        this.audit = audit;
        this.config = properties.enrichment().contactout();
    }

    public ContactLookupConfigResponse config() {
        return new ContactLookupConfigResponse(finder.isOffered());
    }

    public ContactLookupResponse findEmail(UUID userId, UUID workspaceId, UUID projectId,
                                           UUID candidateId, HttpServletRequest httpRequest) {
        CandidateContactState state = begin(ContactChannel.EMAIL, userId, workspaceId, projectId, candidateId);
        if (state.contacts().hasAskedForEmails()) {
            return answered(alreadyAsked(state.contacts().emails().isEmpty()), state.candidate(),
                    ContactChannel.EMAIL, false, userId, workspaceId, projectId, candidateId, httpRequest);
        }
        FoundEmails found = ask(() -> finder.findEmails(state.linkedinUrl()));
        CandidateResponse candidate = candidates.applyFoundEmails(projectId, candidateId, found);
        return answered(found.emails().isEmpty() ? ContactLookupOutcome.NONE : ContactLookupOutcome.FOUND,
                candidate, ContactChannel.EMAIL, true, userId, workspaceId, projectId, candidateId, httpRequest);
    }

    public ContactLookupResponse findPhone(UUID userId, UUID workspaceId, UUID projectId,
                                           UUID candidateId, HttpServletRequest httpRequest) {
        CandidateContactState state = begin(ContactChannel.PHONE, userId, workspaceId, projectId, candidateId);
        if (state.contacts().hasAskedForPhones()) {
            return answered(alreadyAsked(state.contacts().phones().isEmpty()), state.candidate(),
                    ContactChannel.PHONE, false, userId, workspaceId, projectId, candidateId, httpRequest);
        }
        FoundPhones found = ask(() -> finder.findPhones(state.linkedinUrl()));
        CandidateResponse candidate = candidates.applyFoundPhones(projectId, candidateId, found);
        return answered(found.phones().isEmpty() ? ContactLookupOutcome.NONE : ContactLookupOutcome.FOUND,
                candidate, ContactChannel.PHONE, true, userId, workspaceId, projectId, candidateId, httpRequest);
    }

    private CandidateContactState begin(ContactChannel channel, UUID userId, UUID workspaceId,
                                        UUID projectId, UUID candidateId) {
        if (!finder.isOffered()) {
            throw ApiException.of(ErrorCode.CONTACT_LOOKUP_UNAVAILABLE);
        }
        requireBudget(channel, userId);
        CandidateContactState state = candidates.contactStateOf(workspaceId, projectId, candidateId);
        if (LinkedInUrls.profileSlugOrNull(state.linkedinUrl()) == null) {
            throw ApiException.of(ErrorCode.CONTACT_LOOKUP_NO_PROFILE);
        }
        return state;
    }

    /**
     * Caps how often one person may spend credits, per channel so a run on one does not starve the
     * other. The stored guard stops one row being billed twice; this stops one caller scripting the
     * endpoint down a whole grid.
     */
    private void requireBudget(ContactChannel channel, UUID userId) {
        boolean isWithinBudget = limiter.tryAcquire(
                "contact-lookup-%s:user:%s".formatted(channel.value(), userId),
                config.lookupsPerUserPerMinute(), Duration.ofMinutes(1));
        if (!isWithinBudget) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }

    private static ContactLookupOutcome alreadyAsked(boolean foundNothing) {
        return foundNothing ? ContactLookupOutcome.NONE : ContactLookupOutcome.HELD;
    }

    private <T> T ask(Supplier<T> call) {
        try {
            return call.get();
        } catch (VendorException failed) {
            throw refusalFor(failed);
        }
    }

    /**
     * A spent quota is told apart from every other failure because only it leaves the lookup worth
     * running again: nothing was written, so a top-up makes the same press work.
     */
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
                                           ContactChannel channel, boolean asked, UUID userId,
                                           UUID workspaceId, UUID projectId, UUID candidateId,
                                           HttpServletRequest httpRequest) {
        audit.event(ProjectEventType.CANDIDATE_CONTACT_LOOKED_UP)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("channel", channel.value())
                .detail("outcome", outcome.value())
                .detail("asked", String.valueOf(asked))
                .record();
        return new ContactLookupResponse(outcome.value(), candidate);
    }
}
