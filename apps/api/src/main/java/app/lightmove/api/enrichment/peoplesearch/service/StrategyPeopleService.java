package app.lightmove.api.enrichment.peoplesearch.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.ResearchedFiling;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ContactOutSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataPersonProfiles;
import app.lightmove.api.enrichment.candidate.service.ProfilePhotoDownloader;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleClient;
import app.lightmove.api.enrichment.common.service.SearchHitFiling;
import app.lightmove.api.enrichment.common.model.ContactOutCount;
import app.lightmove.api.enrichment.peoplesearch.dto.AddPeopleRequest;
import app.lightmove.api.enrichment.peoplesearch.dto.AddPeopleResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleCountResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleSearchPageResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PersonResultDto;
import app.lightmove.api.enrichment.peoplesearch.model.PeoplePage;
import app.lightmove.api.strategy.model.PeopleFilter;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Strategy's People mode: the stored people filter counted free and searched a page at a time over
 * ContactOut, cache first. Not transactional — a vendor call holds no connection — and nothing is
 * searched from a client payload: the filter is the one the mandate saved, so a page and a saved search
 * always agree about the question.
 */
@Slf4j
@Service
public class StrategyPeopleService {

    /** ContactOut states no page limit; forty pages is a thousand people, well past a mapping's reading. */
    static final int MAX_PAGE = 40;

    /** Declined companies sent as exclusions, beside the researcher's own; ContactOut caps a list at fifty. */
    private static final int MAX_DECLINED_EXCLUDED = 50;

    private final CachedContactOutPeopleQuery contactOut;
    private final StrategyService strategy;
    private final TriageCompanyReadService triage;
    private final TriageCompanyService triageCommands;
    private final ProfilePhotoDownloader photos;
    private final CandidateService candidates;
    private final RateLimiter limiter;
    private final AuditService audit;
    private final ContactOutSettings config;

    public StrategyPeopleService(CachedContactOutPeopleQuery contactOut, StrategyService strategy,
                                 TriageCompanyReadService triage, TriageCompanyService triageCommands,
                                 ProfilePhotoDownloader photos, CandidateService candidates, RateLimiter limiter,
                                 AuditService audit, LightMoveProperties properties) {
        this.contactOut = contactOut;
        this.strategy = strategy;
        this.triage = triage;
        this.triageCommands = triageCommands;
        this.photos = photos;
        this.candidates = candidates;
        this.limiter = limiter;
        this.audit = audit;
        this.config = properties.enrichment().contactout();
    }

    public PeopleCountResponse count(UUID workspaceId, UUID projectId) {
        PeopleFilter filter = strategy.peopleFilterOf(workspaceId, projectId);
        if (!contactOut.isOffered() || filter.isEmpty()) {
            return PeopleCountResponse.none(contactOut.isOffered());
        }
        ContactOutCount count = ask(() -> contactOut.count(
                PeopleFilterBody.countBody(filter, declinedCompanies(workspaceId, projectId))));
        return new PeopleCountResponse(true, count.total(), orZero(count.estimatedPersonalEmails()),
                orZero(count.estimatedWorkEmails()), orZero(count.estimatedPhones()));
    }

    /** A page already bought is answered free; only a page ContactOut must be asked for spends the budget. */
    public PeopleSearchPageResponse search(UUID userId, UUID workspaceId, UUID projectId, int page,
                                           HttpServletRequest httpRequest) {
        if (!contactOut.isOffered()) {
            throw ApiException.of(ErrorCode.PEOPLE_SEARCH_UNAVAILABLE);
        }
        if (page < 1 || page > MAX_PAGE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be between 1 and " + MAX_PAGE);
        }
        PeopleFilter filter = strategy.peopleFilterOf(workspaceId, projectId);
        if (filter.isEmpty()) {
            throw ApiException.of(ErrorCode.PEOPLE_SEARCH_EMPTY_FILTER);
        }
        Map<String, Object> body = PeopleFilterBody.searchBody(filter, declinedCompanies(workspaceId, projectId));
        PeoplePage found = contactOut.cachedPage(body, page).orElseGet(() -> {
            requireBudget(userId);
            return ask(() -> contactOut.buyPage(body, page));
        });

        audit.projectEvent(ProjectEventType.PEOPLE_SEARCH_PAGE_FETCHED, userId, workspaceId, projectId, httpRequest)
                .detail("page", Integer.toString(page))
                .detail("billed", Integer.toString(found.billed()))
                .detail("cached", Integer.toString(found.cached()))
                .record();

        Set<String> held = candidates.mappedProfileSlugsOf(workspaceId, projectId);
        return new PeopleSearchPageResponse(
                found.people().stream()
                        .map(person -> PersonResultDto.of(person, person.linkedinId() != null
                                && held.contains(person.linkedinId().toLowerCase(Locale.ROOT))))
                        .toList(),
                page, ContactOutPeopleClient.MAX_PAGE_SIZE, found.total(), found.billed(), found.cached());
    }

    /**
     * Files the ticked people into the mandate, each under their employer — filed into the universe
     * first, matched to the market where it can be. Read from the people cache, never the vendor: a
     * search already paid for them.
     */
    public AddPeopleResponse add(UUID userId, UUID workspaceId, UUID projectId, AddPeopleRequest request) {
        Set<String> held = candidates.mappedProfileSlugsOf(workspaceId, projectId);
        int added = 0;
        int skipped = 0;
        int unavailable = 0;
        for (String slug : new LinkedHashSet<>(request.linkedinSlugs())) {
            if (held.contains(slug.toLowerCase(Locale.ROOT))) {
                skipped++;
                continue;
            }
            Optional<BrightDataPerson> person = contactOut.onFile(slug);
            if (person.isEmpty()) {
                unavailable++;
                continue;
            }
            if (file(userId, workspaceId, projectId, person.get())) {
                added++;
            } else {
                skipped++;
            }
        }
        return new AddPeopleResponse(added, skipped, unavailable);
    }

    /** False when the mandate already maps them, found only now by the duplicate guards. */
    private boolean file(UUID userId, UUID workspaceId, UUID projectId, BrightDataPerson person) {
        EnrichedProfile research = BrightDataPersonProfiles.toEnrichedProfile(person, EnrichmentVendor.CONTACTOUT)
                .withPhoto(photos.fetchOrNull(person.usableAvatarUrl()));
        UUID employerId = research.employerName() == null ? null
                : triageCommands.captureFromResearch(projectId, userId, new CapturedCompanyDetails(
                        research.employerName(), null, null, null, null, null, null,
                        research.employerLinkedinUrl(), null, null, research.employerLogoUrl(), null, null),
                        TriageCompanySource.PEOPLE_SEARCH).id();
        SaveCandidateRequest filing = SaveCandidateRequest.ofFoundExecutive(employerId,
                SearchHitFiling.nameOf(person), research.title(), person.profileUrl(), research.locationCountry(),
                research.locationCity());
        try {
            candidates.addResearched(userId, workspaceId, projectId, filing, research,
                    ResearchedFiling.ofPeopleSearch());
            return true;
        } catch (ApiException refused) {
            if (refused.getCode() != ErrorCode.CANDIDATE_ALREADY_MAPPED) {
                throw refused;
            }
            return false;
        } catch (DataIntegrityViolationException raced) {
            if (!SearchHitFiling.isNameCollision(raced)) {
                throw raced;
            }
            return false;
        }
    }

    private List<String> declinedCompanies(UUID workspaceId, UUID projectId) {
        return triage.listAllOfStage(workspaceId, projectId, TriageCompanyStatus.DECLINED,
                        TriageCompanyFilters.none(), MAX_DECLINED_EXCLUDED)
                .companies().stream()
                .map(TriageCompanyResponse::companyName)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Per user, so one caller cannot script the pager down a whole result set. */
    private void requireBudget(UUID userId) {
        boolean isWithinBudget = limiter.tryAcquire("people-search:user:%s".formatted(userId),
                config.searchPagesPerUserPerMinute(), Duration.ofMinutes(1));
        if (!isWithinBudget) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }

    private <T> T ask(Supplier<T> call) {
        try {
            return call.get();
        } catch (VendorException failed) {
            throw refusalFor(failed);
        }
    }

    /** A spent quota is told apart: only it leaves the search worth retrying after a top-up. */
    private static ApiException refusalFor(VendorException failed) {
        return switch (failed.getKind()) {
            case QUOTA_EXHAUSTED -> ApiException.of(ErrorCode.PEOPLE_SEARCH_NO_CREDITS);
            case BAD_REQUEST -> {
                log.warn("People search was refused by the provider as a bad request");
                yield ApiException.of(ErrorCode.PEOPLE_SEARCH_REJECTED);
            }
            case CREDENTIALS -> {
                log.error("People search was refused by the provider: {}", failed.getKind());
                yield ApiException.of(ErrorCode.PEOPLE_SEARCH_UNAVAILABLE);
            }
            default -> {
                log.warn("People search failed: {}", failed.getKind());
                yield ApiException.of(ErrorCode.PEOPLE_SEARCH_FAILED);
            }
        };
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
