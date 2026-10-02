package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.ResearchedFiling;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.logging.service.MdcPropagation;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataPersonProfiles;
import app.lightmove.api.enrichment.common.service.SearchHitFiling;
import app.lightmove.api.enrichment.candidate.service.ProfilePhotoDownloader;
import app.lightmove.api.enrichment.sourcing.constant.SourcingOutcome;
import app.lightmove.api.enrichment.sourcing.model.CompanyOutcome;
import app.lightmove.api.enrichment.sourcing.model.ExecutivePick;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRequested;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import app.lightmove.api.position.service.PositionService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs a Find executives request after it commits, off its thread: one model call for the search
 * words, then the companies a few at a time — a vendor search, reworded while it finds nobody, and a
 * filing per hit — recording each company as it finishes so the strip moves. Not {@code @Transactional}: every vendor
 * and model call is made with no connection held, and the writes go through {@link SourcingRunStore}
 * and {@code CandidateService.addResearched}, each its own transaction.
 */
@Component
@Slf4j
class ExecutiveSourcingWorker {

    private final SourcingRunStore store;
    private final ChainedPeopleSearch peopleSearch;
    private final SourcingSpecProposer specs;
    private final SourcingSpecRefiner refiner;
    private final CandidateService candidates;
    private final PositionService positions;
    private final ProfilePhotoDownloader photos;
    private final AuditService audit;
    private final ExecutiveSourcingSettings settings;

    ExecutiveSourcingWorker(SourcingRunStore store, ChainedPeopleSearch peopleSearch, SourcingSpecProposer specs,
                            SourcingSpecRefiner refiner, CandidateService candidates, PositionService positions,
                            ProfilePhotoDownloader photos, AuditService audit, LightMoveProperties properties) {
        this.store = store;
        this.peopleSearch = peopleSearch;
        this.specs = specs;
        this.refiner = refiner;
        this.candidates = candidates;
        this.positions = positions;
        this.photos = photos;
        this.audit = audit;
        this.settings = properties.enrichment().sourcing();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void run(ExecutiveSourcingRequested request) {
        Optional<ExecutiveSourcingRun> started = store.start(request.runId());
        if (started.isEmpty()) {
            log.info("Sourcing run {} was removed before it started", request.runId());
            return;
        }
        try {
            execute(request, started.get().getCompanies());
        } catch (RuntimeException failed) {
            log.error("Sourcing run {} failed", request.runId(), failed);
            store.fail(request.runId(), "The run stopped unexpectedly");
        }
    }

    /**
     * Closing the pool waits for every company already started; one not started by the deadline is
     * reported as not reached rather than cancelled mid-filing.
     */
    private void execute(ExecutiveSourcingRequested request, List<SourcingCompany> companies) {
        SourcingBrief brief = SourcingBrief.of(positions.briefOf(request.workspaceId(), request.projectId()));
        SourcingSpec spec = specs.propose(brief)
                .orElseGet(() -> SourcingSpec.defaultFor(brief.roleTitle(), brief.seniority()));
        store.recordSpec(request.runId(), spec);
        Set<String> heldSlugs = ConcurrentHashMap.newKeySet();
        heldSlugs.addAll(candidates.mappedProfileSlugsOf(request.workspaceId(), request.projectId()));
        Run run = new Run(request, brief, spec, searchedCountries(brief), heldSlugs,
                Instant.now().plus(settings.runDeadline()));

        try (ExecutorService pool = Executors.newFixedThreadPool(settings.parallelism(),
                Thread.ofVirtual().factory())) {
            companies.forEach(company -> pool.execute(MdcPropagation.wrap(() -> run.record(company, run.sourceOne(company)))));
        }

        store.finish(request.runId()).ifPresent(finished -> audit
                .event(ProjectEventType.EXECUTIVE_SOURCING_COMPLETED)
                .actor(request.requestedBy()).workspace(request.workspaceId())
                .target(AuditService.PROJECT_TARGET, request.projectId())
                .detail("runId", finished.getId().toString())
                .detail("companies", finished.getCompanies().size())
                .detail("filed", finished.getExecutivesFiled())
                .detail("vendorHits", finished.getVendorHits())
                .detail("cachedHits", finished.getCachedHits())
                .detail("modelCalls", finished.getModelCalls())
                .record());
    }

    /** The position's country and the configured neighbours; nothing — anywhere — when the brief names no country. */
    private List<String> searchedCountries(SourcingBrief brief) {
        if (brief.countryCode() == null) {
            return List.of();
        }
        return Stream.concat(Stream.of(brief.countryCode()), settings.neighbourCountryCodes().stream())
                .distinct()
                .toList();
    }

    /** One run's fixed inputs, and the people it must not file twice — including those it files itself. */
    @RequiredArgsConstructor
    private final class Run {

        private final ExecutiveSourcingRequested request;
        private final SourcingBrief brief;
        private final SourcingSpec spec;
        private final List<String> searchedCountries;
        private final Set<String> heldSlugs;
        private final Instant deadline;
        private final ReentrantLock recording = new ReentrantLock();

        /**
         * Serialised: the run row is one aggregate under {@code @Version}, and the companies finish on
         * their own threads. An outcome that will not save is recorded as a failure instead, so the run
         * never finishes a company short.
         */
        void record(SourcingCompany company, CompanyOutcome outcome) {
            recording.lock();
            try {
                store.recordOutcome(request.runId(), outcome);
            } catch (RuntimeException unsaved) {
                log.error("Sourcing outcome at {} was not saved", company.companyName(), unsaved);
                store.recordOutcome(request.runId(), CompanyOutcome.of(company, SourcingOutcome.FAILED));
            } finally {
                recording.unlock();
            }
        }

        CompanyOutcome sourceOne(SourcingCompany company) {
            if (Instant.now().isAfter(deadline)) {
                return CompanyOutcome.of(company, SourcingOutcome.NOT_REACHED);
            }
            try {
                return search(company);
            } catch (RuntimeException failed) {
                log.warn("Sourcing at {} failed: {}", company.companyName(), failed.toString());
                return CompanyOutcome.of(company, SourcingOutcome.FAILED);
            }
        }

        /**
         * A search finding nobody is reworded by the model and run again, up to {@code max-search-rounds};
         * the first that finds anybody is the last, and its hits not already mapped are filed best fit
         * first ({@link SourcedHitRanking}).
         */
        private CompanyOutcome search(SourcingCompany company) {
            if (company.linkedinSlug() == null) {
                return CompanyOutcome.of(company, SourcingOutcome.NO_LINKEDIN_PAGE);
            }
            Map<String, Integer> triedWords = new HashMap<>();
            RefineConversation conversation = null;
            SourcingSpec searching = spec;
            int billed = 0;
            int cached = 0;
            int modelCalls = 0;
            for (int round = 1; ; round++) {
                triedWords.putIfAbsent(searching.wordKey(), round);
                PeopleFound found = peopleSearch.currentEmployeesTitled(company.employer(), searching,
                        brief.seniority(), searchedCountries, settings.hitsPerCompany());
                billed += found.billed();
                cached += found.cached();
                PeopleFound total = found.spending(billed, cached);
                if (!found.people().isEmpty()) {
                    return file(company, total, modelCalls, searching);
                }
                if (round >= settings.maxSearchRounds() || Instant.now().isAfter(deadline)) {
                    return outcome(company, SourcingOutcome.NO_HITS, total, modelCalls, List.of());
                }
                if (conversation == null) {
                    conversation = refiner.open(brief, company.companyName(), company.employeeCount());
                }
                refiner.reportNobodyFound(conversation, round, searching);
                Optional<SourcingSpec> next = refiner.refine(conversation, round + 1, spec.roleSummary());
                modelCalls += 1;
                Integer repeated = next.map(words -> triedWords.get(words.wordKey())).orElse(null);
                if (repeated != null) {
                    refiner.reportRepeat(conversation, repeated);
                    next = refiner.refine(conversation, round + 1, spec.roleSummary());
                    modelCalls += 1;
                }
                if (next.isEmpty() || triedWords.containsKey(next.get().wordKey())) {
                    return outcome(company, SourcingOutcome.NO_HITS, total, modelCalls, List.of());
                }
                searching = next.get();
            }
        }

        private CompanyOutcome file(SourcingCompany company, PeopleFound found, int modelCalls, SourcingSpec searched) {
            List<BrightDataPerson> usable = SourcedHitRanking.ranked(usable(found.people()), searched,
                    brief.seniority());
            if (usable.isEmpty()) {
                return outcome(company, SourcingOutcome.ALL_ALREADY_MAPPED, found, modelCalls, List.of());
            }
            List<ExecutivePick> picks = usable.stream()
                    .limit(settings.picksPerCompany())
                    .map(person -> file(company, person, found.researchedBy()))
                    .toList();
            boolean anyFiled = picks.stream().anyMatch(pick -> pick.candidateId() != null);
            return outcome(company, anyFiled ? SourcingOutcome.FILED : SourcingOutcome.NOTHING_FIT, found, modelCalls,
                    picks);
        }

        /** A hit refused as already mapped, or losing a race to the same name, is counted as skipped. */
        private ExecutivePick file(SourcingCompany company, BrightDataPerson person, EnrichmentVendor researchedBy) {
            EnrichedProfile research = BrightDataPersonProfiles.toEnrichedProfile(person, researchedBy)
                    .withPhoto(photos.fetchOrNull(person.usableAvatarUrl()));
            String name = SearchHitFiling.nameOf(person);
            SaveCandidateRequest filing = SaveCandidateRequest.ofFoundExecutive(company.triageCompanyId(), name,
                    research.title(), person.profileUrl(), research.locationCountry(), research.locationCity());
            try {
                UUID candidateId = candidates.addResearched(request.requestedBy(), request.workspaceId(),
                        request.projectId(), filing, research, ResearchedFiling.ofSourcingRun(request.runId()))
                        .candidate().id();
                heldSlugs.add(person.linkedinId());
                return ExecutivePick.of(name, candidateId);
            } catch (ApiException refused) {
                if (refused.getCode() != ErrorCode.CANDIDATE_ALREADY_MAPPED) {
                    throw refused;
                }
                return ExecutivePick.of(name, null);
            } catch (DataIntegrityViolationException raced) {
                if (!SearchHitFiling.isFilingRace(raced)) {
                    throw raced;
                }
                return ExecutivePick.of(name, null);
            }
        }

        /** Hits with a slug to key on, not already mapped in the mandate — including by this run. */
        private List<BrightDataPerson> usable(List<BrightDataPerson> hits) {
            return hits.stream()
                    .filter(person -> person.linkedinId() != null && !person.linkedinId().isBlank())
                    .filter(person -> !heldSlugs.contains(person.linkedinId()))
                    .toList();
        }

        private static CompanyOutcome outcome(SourcingCompany company, SourcingOutcome outcome, PeopleFound found,
                                              int modelCalls, List<ExecutivePick> picks) {
            int filed = (int) picks.stream().filter(pick -> pick.candidateId() != null).count();
            return new CompanyOutcome(company.triageCompanyId(), company.companyName(), outcome, found.billed(),
                    found.cached(), found.matched(), modelCalls, filed, picks.size() - filed, picks, found.source());
        }
    }
}
