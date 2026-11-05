package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.enrichment.common.service.PeopleQueryKeys;
import app.lightmove.api.enrichment.sourcing.constant.CriteriaSource;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCriteriaDto;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCriteriaResponse;
import app.lightmove.api.enrichment.sourcing.model.SearchCriteria;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import app.lightmove.api.enrichment.sourcing.model.SourcingCriteria;
import app.lightmove.api.enrichment.sourcing.repository.SourcingCriteriaRepository;
import app.lightmove.api.position.service.PositionService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A position's Find executives search: drafted by the AI from the brief once, then saved as the consultant edits
 * it. Where nothing is saved yet, a search runs on the role title's own words. The draft's model call is made with
 * no transaction open.
 */
@Service
public class SourcingCriteriaService {

    private final SourcingCriteriaRepository saved;
    private final PositionService positions;
    private final SearchWordsDrafter drafter;
    private final SourcingCompanies companies;
    private final LlmBudgetGuard llmBudget;
    private final TransactionTemplate transaction;
    private final ExecutiveSourcingSettings settings;

    public SourcingCriteriaService(SourcingCriteriaRepository saved, PositionService positions,
                                   SearchWordsDrafter drafter, SourcingCompanies companies, LlmBudgetGuard llmBudget,
                                   PlatformTransactionManager transactions, LightMoveProperties properties) {
        this.saved = saved;
        this.positions = positions;
        this.drafter = drafter;
        this.companies = companies;
        this.llmBudget = llmBudget;
        this.transaction = new TransactionTemplate(transactions);
        this.settings = properties.enrichment().sourcing();
    }

    @Transactional(readOnly = true)
    public Optional<SourcingCriteriaResponse> get(UUID workspaceId, UUID projectId) {
        return saved.findByWorkspaceIdAndProjectId(workspaceId, projectId)
                .map(criteria -> responseOf(criteria, briefOf(workspaceId, projectId)));
    }

    /** Drafts the search from the brief and the companies' countries, keeping the saved profiles per company. */
    public SourcingCriteriaResponse suggest(UUID userId, UUID workspaceId, UUID projectId, List<UUID> companyIds) {
        llmBudget.require(LlmBudget.EXECUTIVE_SOURCING, userId);
        SourcingBrief brief = briefOf(workspaceId, projectId);
        List<SourcingCompany> chosen = companies.chosen(workspaceId, projectId, companyIds,
                settings.maxCompaniesPerRun());
        Optional<SearchWords> drafted = drafter.draft(brief, SourcingCompanies.countryNamesOf(chosen));
        SearchWords words = drafted.orElseGet(() -> SearchWords.defaultFor(brief.roleTitle(), brief.seniority()));
        CriteriaSource source = drafted.isPresent() ? CriteriaSource.AI : CriteriaSource.DEFAULT;
        return Objects.requireNonNull(transaction.execute(status ->
                saveDraft(userId, workspaceId, projectId, words, source, brief)));
    }

    @Transactional
    public SourcingCriteriaResponse save(UUID userId, UUID workspaceId, UUID projectId, SearchCriteria edited) {
        SourcingCriteria criteria = saved.findByWorkspaceIdAndProjectId(workspaceId, projectId)
                .map(existing -> {
                    existing.edit(edited, userId);
                    return existing;
                })
                .orElseGet(() -> saved.saveAndFlush(SourcingCriteria.drafted(workspaceId, projectId, edited,
                        CriteriaSource.EDITED, null, userId)));
        return responseOf(criteria, briefOf(workspaceId, projectId));
    }

    /** What a run searches: the saved search, or the role title's own words where none is saved yet. */
    @Transactional(readOnly = true)
    public SearchCriteria current(UUID workspaceId, UUID projectId) {
        return saved.findByWorkspaceIdAndProjectId(workspaceId, projectId)
                .map(SourcingCriteria::getCriteria)
                .orElseGet(() -> defaultOf(briefOf(workspaceId, projectId)));
    }

    private SourcingCriteriaResponse saveDraft(UUID userId, UUID workspaceId, UUID projectId, SearchWords words,
                                               CriteriaSource source, SourcingBrief brief) {
        String fingerprint = fingerprintOf(brief);
        Optional<SourcingCriteria> existing = saved.findByWorkspaceIdAndProjectId(workspaceId, projectId);
        SearchCriteria drafted = new SearchCriteria(words, regionOf(brief),
                existing.map(criteria -> criteria.getCriteria().perCompany()).orElse(SearchCriteria.DEFAULT_PER_COMPANY));
        SourcingCriteria criteria = existing.map(found -> {
            found.redraft(drafted, source, fingerprint, userId);
            return found;
        }).orElseGet(() -> insertRacing(SourcingCriteria.drafted(workspaceId, projectId, drafted, source,
                fingerprint, userId)));
        return responseOf(criteria, brief);
    }

    /** Two drafts racing for a position's first save: the later one is refused by V184's unique key, and loses. */
    private SourcingCriteria insertRacing(SourcingCriteria first) {
        try {
            return saved.saveAndFlush(first);
        } catch (DataIntegrityViolationException raced) {
            return saved.findByWorkspaceIdAndProjectId(first.getWorkspaceId(), first.getProjectId()).orElseThrow();
        }
    }

    private SearchCriteria defaultOf(SourcingBrief brief) {
        return new SearchCriteria(SearchWords.defaultFor(brief.roleTitle(), brief.seniority()), regionOf(brief),
                SearchCriteria.DEFAULT_PER_COMPANY);
    }

    private List<String> regionOf(SourcingBrief brief) {
        return SearchCriteria.regionOf(brief.countryCode(), settings.neighbourCountryCodes());
    }

    private SourcingBrief briefOf(UUID workspaceId, UUID projectId) {
        return SourcingBrief.of(positions.briefOf(workspaceId, projectId));
    }

    private static SourcingCriteriaResponse responseOf(SourcingCriteria criteria, SourcingBrief brief) {
        boolean briefChanged = criteria.getBriefFingerprint() != null
                && !criteria.getBriefFingerprint().equals(fingerprintOf(brief));
        return new SourcingCriteriaResponse(SourcingCriteriaDto.of(criteria.getCriteria()), criteria.getSource(),
                briefChanged, criteria.getUpdatedAt());
    }

    /** What the draft read of the brief, hashed: the role, its level and place, what it does and is measured on. */
    static String fingerprintOf(SourcingBrief brief) {
        return PeopleQueryKeys.of(Stream.of(brief.roleTitle(), String.valueOf(brief.seniority()), brief.department(),
                        brief.locationLine(), String.join("\n", brief.responsibilities()), brief.narrative(),
                        String.join(",", brief.technicalCompetencies()))
                .map(part -> part == null ? "" : part)
                .collect(Collectors.joining("|")));
    }
}
