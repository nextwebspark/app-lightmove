package app.lightmove.api.enrichment.peoplesearch.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.billing.usage.service.UsageRecorder;
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
import app.lightmove.api.enrichment.common.model.ContactOutCount;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleClient;
import app.lightmove.api.enrichment.common.service.ContactOutProfileDetailsReader;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleCountResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleSearchPageResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleSearchResultsResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PersonResultDto;
import app.lightmove.api.enrichment.peoplesearch.model.DeclinedEmployers;
import app.lightmove.api.enrichment.peoplesearch.model.PeoplePage;
import app.lightmove.api.strategy.model.PeopleFilter;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Strategy's People mode, reading: the stored people filter counted free and searched a page at a time
 * over ContactOut, cache first. Filing what it finds is {@link PeopleSearchFiling}'s.
 */
@Slf4j
@Service
public class StrategyPeopleService {

    /** ContactOut states no page limit; forty pages is a thousand people, well past a mapping's reading. */
    static final int MAX_PAGE = 40;

    /** ContactOut caps a list parameter at fifty, so a count excludes the first fifty declined companies. */
    private static final int MAX_DECLINED_EXCLUDED = 50;

    /** Far past any mandate's declined stage: the whole list filters a page, where no vendor cap binds. */
    private static final int MAX_DECLINED_READ = 5_000;

    private final CachedContactOutPeopleQuery contactOut;
    private final StrategyService strategy;
    private final TriageCompanyReadService triage;
    private final CandidateService candidates;
    private final RateLimiter limiter;
    private final FairUseGuard fairUse;
    private final UsageRecorder usage;
    private final AuditService audit;
    private final ContactOutSettings config;
    private final ObjectMapper json;

    public StrategyPeopleService(CachedContactOutPeopleQuery contactOut, StrategyService strategy,
                                 TriageCompanyReadService triage, CandidateService candidates, RateLimiter limiter,
                                 FairUseGuard fairUse, UsageRecorder usage, AuditService audit,
                                 LightMoveProperties properties, ObjectMapper json) {
        this.contactOut = contactOut;
        this.strategy = strategy;
        this.triage = triage;
        this.candidates = candidates;
        this.limiter = limiter;
        this.fairUse = fairUse;
        this.usage = usage;
        this.audit = audit;
        this.config = properties.enrichment().contactout();
        this.json = json;
    }

    /** Only the count sends declined companies: it is free, and its cache is this process's own. */
    public PeopleCountResponse count(UUID workspaceId, UUID projectId) {
        PeopleFilter filter = strategy.peopleFilterOf(workspaceId, projectId);
        if (!contactOut.isOffered() || filter.isEmpty()) {
            return PeopleCountResponse.none(contactOut.isOffered());
        }
        List<String> excluded = declinedOf(workspaceId, projectId).names().stream()
                .limit(MAX_DECLINED_EXCLUDED)
                .toList();
        ContactOutCount count = ask(() -> contactOut.count(PeopleFilterBody.countBody(filter, excluded)));
        return new PeopleCountResponse(true, count.total(), orZero(count.estimatedPersonalEmails()),
                orZero(count.estimatedWorkEmails()), orZero(count.estimatedPhones()));
    }

    /**
     * A page already bought is answered free; only a page ContactOut must be asked for spends the budget.
     * The vendor is asked exactly the question the page is cached under — never this mandate's declined
     * companies, since the cache answers every workspace — and people at those companies are left out
     * here instead. A page counts towards fair use the first time this workspace reads it, whoever bought it.
     */
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
        Map<String, Object> question = PeopleFilterBody.searchBody(filter);
        Optional<PeoplePage> cached = contactOut.cachedPage(question, page);
        if (cached.isEmpty()) {
            requireBudget(userId);
        }
        String usageKey = "people-page:" + contactOut.queryKeyOf(question, page);
        boolean newHere = !usage.hasRecorded(workspaceId, usageKey);
        if (newHere) {
            fairUse.check(workspaceId, userId, UsageKind.PEOPLE_SEARCH_PAGE,
                    cached.map(known -> known.people().size()).orElse(ContactOutPeopleClient.MAX_PAGE_SIZE));
        }
        PeoplePage found = cached.orElseGet(() -> ask(() -> contactOut.buyPage(question, page)));
        if (newHere) {
            usage.record(new MeteredUse(workspaceId, userId, projectId, UsageKind.PEOPLE_SEARCH_PAGE,
                    found.people().size(), usageKey));
        }

        audit.projectEvent(ProjectEventType.PEOPLE_SEARCH_PAGE_FETCHED, userId, workspaceId, projectId, httpRequest)
                .detail("page", Integer.toString(page))
                .detail("billed", Integer.toString(found.billed()))
                .detail("cached", Integer.toString(found.cached()))
                .record();

        return pagesOf(List.of(found), page, workspaceId, projectId).getFirst();
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
        Map<String, Object> question = PeopleFilterBody.searchBody(filter);
        List<PeoplePage> bought = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGE; page++) {
            Optional<PeoplePage> found = contactOut.cachedPage(question, page);
            if (found.isEmpty()) {
                break;
            }
            bought.add(found.get());
        }
        return new PeopleSearchResultsResponse(bought.isEmpty() ? List.of()
                : pagesOf(bought, 1, workspaceId, projectId));
    }

    /**
     * Pages numbered from {@code firstPage}, read with one lookup each of the mandate's people, its declined
     * companies and the pages' provider records, however many pages there are.
     */
    private List<PeopleSearchPageResponse> pagesOf(List<PeoplePage> pages, int firstPage, UUID workspaceId,
                                                   UUID projectId) {
        Map<String, UUID> held = candidates.mappedProfileCandidatesOf(workspaceId, projectId);
        DeclinedEmployers declined = declinedOf(workspaceId, projectId);
        Map<String, String> sources = contactOut.sourceRecordsOf(pages.stream()
                .flatMap(page -> page.people().stream())
                .map(BrightDataPerson::linkedinId)
                .filter(Objects::nonNull)
                .toList());
        List<PeopleSearchPageResponse> read = new ArrayList<>(pages.size());
        for (int index = 0; index < pages.size(); index++) {
            PeoplePage page = pages.get(index);
            read.add(new PeopleSearchPageResponse(
                    page.people().stream()
                            .map(person -> resultOf(person, held, sources))
                            .filter(person -> person.held()
                                    || !declined.includes(person.companyName(), person.companyLinkedinUrl()))
                            .toList(),
                    firstPage + index, ContactOutPeopleClient.MAX_PAGE_SIZE, page.total(), page.billed(),
                    page.cached()));
        }
        return read;
    }

    private PersonResultDto resultOf(BrightDataPerson person, Map<String, UUID> held, Map<String, String> sources) {
        String slug = person.linkedinId() == null ? null : person.linkedinId().toLowerCase(Locale.ROOT);
        return PersonResultDto.of(person,
                slug == null ? null : ContactOutProfileDetailsReader.read(sources.get(slug), json).orElse(null),
                slug == null ? null : held.get(slug));
    }

    private DeclinedEmployers declinedOf(UUID workspaceId, UUID projectId) {
        return DeclinedEmployers.of(triage.listAllOfStage(workspaceId, projectId, TriageCompanyStatus.DECLINED,
                TriageCompanyFilters.none(), MAX_DECLINED_READ).companies());
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
