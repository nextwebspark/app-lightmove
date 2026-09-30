package app.lightmove.api.enrichment.peoplesearch.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.dto.CandidateResponse;
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
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails;
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails.CompanyFacts;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleClient;
import app.lightmove.api.enrichment.common.service.ContactOutProfileDetailsReader;
import app.lightmove.api.enrichment.common.service.SearchHitFiling;
import app.lightmove.api.enrichment.common.model.ContactOutCount;
import app.lightmove.api.enrichment.peoplesearch.dto.AddPeopleRequest;
import app.lightmove.api.enrichment.peoplesearch.dto.AddPeopleResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleCountResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleSearchPageResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleSearchResultsResponse;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

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
    private final ObjectMapper json;

    public StrategyPeopleService(CachedContactOutPeopleQuery contactOut, StrategyService strategy,
                                 TriageCompanyReadService triage, TriageCompanyService triageCommands,
                                 ProfilePhotoDownloader photos, CandidateService candidates, RateLimiter limiter,
                                 AuditService audit, LightMoveProperties properties, ObjectMapper json) {
        this.contactOut = contactOut;
        this.strategy = strategy;
        this.triage = triage;
        this.triageCommands = triageCommands;
        this.photos = photos;
        this.candidates = candidates;
        this.limiter = limiter;
        this.audit = audit;
        this.config = properties.enrichment().contactout();
        this.json = json;
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
        List<String> declined = declinedCompanies(workspaceId, projectId);
        Map<String, Object> question = PeopleFilterBody.searchBody(filter, List.of());
        PeoplePage found = contactOut.cachedPage(question, page).orElseGet(() -> {
            requireBudget(userId);
            return ask(() -> contactOut.buyPage(question, PeopleFilterBody.searchBody(filter, declined), page));
        });

        audit.projectEvent(ProjectEventType.PEOPLE_SEARCH_PAGE_FETCHED, userId, workspaceId, projectId, httpRequest)
                .detail("page", Integer.toString(page))
                .detail("billed", Integer.toString(found.billed()))
                .detail("cached", Integer.toString(found.cached()))
                .record();

        return pageOf(found, page, candidates.mappedProfileCandidatesOf(workspaceId, projectId), declined);
    }

    /**
     * The pages of the stored filter already bought, read back in order until the first that is not, so
     * leaving Strategy and coming back shows what was paid for without a second press. Never buys.
     */
    public PeopleSearchResultsResponse results(UUID workspaceId, UUID projectId) {
        PeopleFilter filter = strategy.peopleFilterOf(workspaceId, projectId);
        if (!contactOut.isOffered() || filter.isEmpty()) {
            return new PeopleSearchResultsResponse(List.of());
        }
        Map<String, Object> question = PeopleFilterBody.searchBody(filter, List.of());
        List<String> declined = declinedCompanies(workspaceId, projectId);
        Map<String, UUID> held = candidates.mappedProfileCandidatesOf(workspaceId, projectId);
        List<PeopleSearchPageResponse> pages = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGE; page++) {
            Optional<PeoplePage> found = contactOut.cachedPage(question, page);
            if (found.isEmpty()) {
                break;
            }
            pages.add(pageOf(found.get(), page, held, declined));
        }
        return new PeopleSearchResultsResponse(pages);
    }

    /**
     * A cached page may predate a company being declined, so its people there are left out here, as a
     * fresh search would leave them out — unless the mandate already maps them.
     */
    private PeopleSearchPageResponse pageOf(PeoplePage found, int page, Map<String, UUID> held,
                                            List<String> declined) {
        Set<String> declinedNames = declined.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<String, String> sources = contactOut.sourceRecordsOf(found.people().stream()
                .map(BrightDataPerson::linkedinId).filter(Objects::nonNull).toList());
        return new PeopleSearchPageResponse(
                found.people().stream()
                        .map(person -> {
                            String slug = person.linkedinId() == null ? null
                                    : person.linkedinId().toLowerCase(Locale.ROOT);
                            return PersonResultDto.of(person, slug == null ? null : detailsOf(sources.get(slug)),
                                    slug == null ? null : held.get(slug));
                        })
                        .filter(person -> person.held() || person.companyName() == null
                                || !declinedNames.contains(person.companyName().toLowerCase(Locale.ROOT)))
                        .toList(),
                page, ContactOutPeopleClient.MAX_PAGE_SIZE, found.total(), found.billed(), found.cached());
    }

    /**
     * Files the ticked people into the mandate, each under their employer — filed into the universe
     * first, matched to the market where it can be. Read from the people cache, never the vendor: a
     * search already paid for them.
     */
    public AddPeopleResponse add(UUID userId, UUID workspaceId, UUID projectId, AddPeopleRequest request) {
        TriageCompanyStatus stage = TriageCompanyStatus.parseOrInUniverse(request.status());
        Set<String> held = candidates.mappedProfileSlugsOf(workspaceId, projectId);
        int added = 0;
        int skipped = 0;
        int unavailable = 0;
        int elsewhere = 0;
        List<AddPeopleResponse.Filed> filed = new ArrayList<>();
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
            Optional<Filing> filing = file(userId, workspaceId, projectId, person.get(), stage);
            if (filing.isEmpty()) {
                skipped++;
                continue;
            }
            added++;
            if (filing.get().atOtherStage()) {
                elsewhere++;
            }
            filed.add(new AddPeopleResponse.Filed(person.get().linkedinId(), filing.get().candidateId()));
        }
        return new AddPeopleResponse(added, skipped, unavailable, elsewhere, filed);
    }

    private record Filing(UUID candidateId, boolean atOtherStage) {}

    /**
     * The employer is filed at {@code stage} unless the mandate already holds it, where it stays put and
     * the person joins it there — the Companies screen's rule for a company already triaged. A company
     * the market does not carry is filed with the facts ContactOut gave about it. Empty when the mandate
     * already maps the person, found only now by the duplicate guards.
     */
    private Optional<Filing> file(UUID userId, UUID workspaceId, UUID projectId, BrightDataPerson person,
                                  TriageCompanyStatus stage) {
        EnrichedProfile research = BrightDataPersonProfiles.toEnrichedProfile(person, EnrichmentVendor.CONTACTOUT)
                .withPhoto(photos.fetchOrNull(person.usableAvatarUrl()));
        CompanyFacts company = Optional.ofNullable(detailsOf(contactOut.sourceRecordsOf(List.of(person.linkedinId()))
                        .get(person.linkedinId().toLowerCase(Locale.ROOT))))
                .map(ContactOutProfileDetails::company)
                .orElse(null);
        TriageCompanyResponse employer = research.employerName() == null ? null
                : triageCommands.captureFromResearch(projectId, userId,
                        employerDetails(research, company), TriageCompanySource.PEOPLE_SEARCH, stage);
        UUID employerId = employer == null ? null : employer.id();
        SaveCandidateRequest filing = SaveCandidateRequest.ofFoundExecutive(employerId,
                SearchHitFiling.nameOf(person), research.title(), person.profileUrl(), research.locationCountry(),
                research.locationCity());
        try {
            CandidateResponse candidate = candidates.addResearched(userId, workspaceId, projectId, filing, research,
                    ResearchedFiling.ofPeopleSearch());
            return Optional.of(new Filing(candidate.id(),
                    employer != null && !employer.status().equals(stage.value())));
        } catch (ApiException refused) {
            if (refused.getCode() != ErrorCode.CANDIDATE_ALREADY_MAPPED) {
                throw refused;
            }
            return Optional.empty();
        } catch (DataIntegrityViolationException raced) {
            if (!SearchHitFiling.isNameCollision(raced)) {
                throw raced;
            }
            return Optional.empty();
        }
    }

    private static CapturedCompanyDetails employerDetails(EnrichedProfile research, CompanyFacts company) {
        if (company == null) {
            return new CapturedCompanyDetails(research.employerName(), null, null, null, null, null, null,
                    research.employerLinkedinUrl(), null, null, research.employerLogoUrl(), null, null);
        }
        String website = company.website() != null ? company.website()
                : company.domain() == null ? null : "https://" + company.domain();
        return new CapturedCompanyDetails(research.employerName(), company.industry(), company.country(), null,
                null, null, website, research.employerLinkedinUrl(), company.foundedYear(), null,
                research.employerLogoUrl() != null ? research.employerLogoUrl() : company.logoUrl(), null, null);
    }

    private ContactOutProfileDetails detailsOf(String sourceRecord) {
        return ContactOutProfileDetailsReader.read(sourceRecord, json).orElse(null);
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
