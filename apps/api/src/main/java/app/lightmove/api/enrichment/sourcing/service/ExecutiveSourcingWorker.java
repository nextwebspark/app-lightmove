package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.ResearchedFiling;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.logging.service.MdcPropagation;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataPersonProfiles;
import app.lightmove.api.enrichment.candidate.service.ProfilePhotoDownloader;
import app.lightmove.api.enrichment.common.service.SearchHitFiling;
import app.lightmove.api.enrichment.sourcing.constant.SourcingOutcome;
import app.lightmove.api.enrichment.sourcing.constant.TitleCheck;
import app.lightmove.api.enrichment.sourcing.model.CompanyOutcome;
import app.lightmove.api.enrichment.sourcing.model.ExecutivePick;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRequested;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.FoundPeople;
import app.lightmove.api.enrichment.sourcing.model.FoundPerson;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import app.lightmove.api.position.service.PositionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs a Find executives request after it commits, off its thread: one model call for the search words,
 * then the companies a few at a time — one {@link CompanySearch} each, judged by {@link SeatFit}, the best
 * filed — recording each company as it finishes so the strip moves. Not {@code @Transactional}: every
 * vendor and model call is made with no connection held, and the writes go through {@link SourcingRunStore}
 * and {@code CandidateService.addResearched}, each its own transaction.
 */
@Component
@Slf4j
class ExecutiveSourcingWorker {

    private final SourcingRunStore store;
    private final CompanySearch companySearch;
    private final LiveProfiles live;
    private final PeopleCache cache;
    private final SearchWordsDrafter drafter;
    private final CandidateService candidates;
    private final PositionService positions;
    private final ProfilePhotoDownloader photos;
    private final AuditService audit;
    private final UsageRecorder usage;
    private final ExecutiveSourcingSettings settings;

    ExecutiveSourcingWorker(SourcingRunStore store, CompanySearch companySearch, LiveProfiles live, PeopleCache cache,
                            SearchWordsDrafter drafter, CandidateService candidates, PositionService positions,
                            ProfilePhotoDownloader photos, AuditService audit, UsageRecorder usage,
                            LightMoveProperties properties) {
        this.store = store;
        this.companySearch = companySearch;
        this.live = live;
        this.cache = cache;
        this.drafter = drafter;
        this.candidates = candidates;
        this.positions = positions;
        this.photos = photos;
        this.audit = audit;
        this.usage = usage;
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
            store.fail(request.runId(), "The run stopped unexpectedly")
                    .ifPresent(stopped -> recordUsage(request, stopped));
        }
    }

    /**
     * Closing the pool waits for every company already started; one not started by the deadline is
     * reported as not reached rather than cancelled mid-filing.
     */
    private void execute(ExecutiveSourcingRequested request, List<SourcingCompany> companies) {
        SourcingBrief brief = SourcingBrief.of(positions.briefOf(request.workspaceId(), request.projectId()));
        SearchWords words = drafter.draft(brief, countryNamesOf(companies))
                .orElseGet(() -> SearchWords.defaultFor(brief.roleTitle(), brief.seniority()));
        store.recordWords(request.runId(), words);
        Set<String> heldSlugs = ConcurrentHashMap.newKeySet();
        heldSlugs.addAll(candidates.mappedProfileSlugsOf(request.workspaceId(), request.projectId()));
        Run run = new Run(request, brief, words, SeatFit.of(brief, words), heldSlugs,
                Instant.now().plus(settings.runDeadline()));

        try (ExecutorService pool = Executors.newFixedThreadPool(settings.parallelism(),
                Thread.ofVirtual().factory())) {
            companies.forEach(company -> pool.execute(MdcPropagation.wrap(() -> run.record(company, run.sourceOne(company)))));
        }

        store.finish(request.runId()).ifPresent(finished -> {
            audit.event(ProjectEventType.EXECUTIVE_SOURCING_COMPLETED)
                    .actor(request.requestedBy()).workspace(request.workspaceId())
                    .target(AuditService.PROJECT_TARGET, request.projectId())
                    .detail("runId", finished.getId().toString())
                    .detail("companies", finished.getCompanies().size())
                    .detail("filed", finished.getExecutivesFiled())
                    .detail("vendorHits", finished.getVendorHits())
                    .detail("cachedHits", finished.getCachedHits())
                    .detail("modelCalls", finished.getModelCalls())
                    .record();
            recordUsage(request, finished);
        });
    }

    /** Whatever a run filed before it ended, once per run; a run that filed nobody used nothing. */
    private void recordUsage(ExecutiveSourcingRequested request, ExecutiveSourcingRun ended) {
        if (ended.getExecutivesFiled() == 0) {
            return;
        }
        usage.record(new MeteredUse(request.workspaceId(), request.requestedBy(), request.projectId(),
                UsageKind.SOURCING_RUN, ended.getExecutivesFiled(), "sourcing-run:" + ended.getId()));
    }

    /** The countries the companies are in, by name, so the words come in the languages their titles use. */
    private static List<String> countryNamesOf(List<SourcingCompany> companies) {
        return companies.stream()
                .map(SourcingCompany::countryCode)
                .filter(Objects::nonNull)
                .distinct()
                .map(Countries::nameOfCode)
                .flatMap(Optional::stream)
                .toList();
    }

    /** One run's fixed inputs, and the people it must not file twice — including those it files itself. */
    @RequiredArgsConstructor
    private final class Run {

        private final ExecutiveSourcingRequested request;
        private final SourcingBrief brief;
        private final SearchWords words;
        private final SeatFit seat;
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
         * The people found who hold the role are filed best fit first ({@link SourcedHitRanking}): those whose
         * title is on record and passes {@link SeatFit}, then those a free check confirmed, whose live profile
         * is bought as they are filed while the company's budget lasts.
         */
        private CompanyOutcome search(SourcingCompany company) {
            if (company.linkedinSlug() == null) {
                return CompanyOutcome.of(company, SourcingOutcome.NO_LINKEDIN_PAGE);
            }
            FoundPeople found = companySearch.search(company.employer(), words, brief.seniority(),
                    searchedCountries(company), Set.copyOf(heldSlugs));
            if (found.people().isEmpty()) {
                return outcome(company, found.held() > 0 ? SourcingOutcome.ALL_ALREADY_MAPPED : SourcingOutcome.NO_HITS,
                        found, null, 0);
            }
            List<FoundPerson> titled = found.people().stream().filter(FoundPerson::hasTitle).toList();
            Map<BrightDataPerson, FoundPerson> holding = titled.stream()
                    .filter(person -> seat.holds(person.person()))
                    .collect(Collectors.toMap(FoundPerson::person, Function.identity(), (first, second) -> first));
            List<FoundPerson> confirmed = found.people().stream()
                    .filter(person -> person.check() == TitleCheck.CONFIRMED)
                    .toList();
            int notFit = titled.size() - holding.size();
            List<FoundPerson> ranked = Stream.concat(
                    SourcedHitRanking.ranked(List.copyOf(holding.keySet()), words, brief.seniority()).stream()
                            .map(holding::get),
                    confirmed.stream()).toList();
            if (ranked.isEmpty()) {
                return outcome(company, SourcingOutcome.NOTHING_FIT, found, null, notFit);
            }
            Filing filing = new Filing(company, settings.profilesPerCompany() - found.credits());
            ranked.stream().takeWhile(person -> filing.picks.size() < settings.picksPerCompany())
                    .forEach(filing::fileOrSkip);
            boolean anyFiled = filing.picks.stream().anyMatch(pick -> pick.candidateId() != null);
            return outcome(company, anyFiled ? SourcingOutcome.FILED : SourcingOutcome.NOTHING_FIT, found,
                    filing, notFit);
        }

        /** The position's country, the configured neighbours and the company's own; anywhere when the brief names none. */
        private List<String> searchedCountries(SourcingCompany company) {
            if (brief.countryCode() == null) {
                return List.of();
            }
            return Stream.of(Stream.of(brief.countryCode()), settings.neighbourCountryCodes().stream(),
                            Stream.ofNullable(company.countryCode()))
                    .flatMap(Function.identity())
                    .distinct()
                    .toList();
        }

        /** {@code filing} is null where nobody was filed. */
        private CompanyOutcome outcome(SourcingCompany company, SourcingOutcome outcome, FoundPeople found,
                                       Filing filing, int notFit) {
            List<ExecutivePick> picks = filing == null ? List.of() : filing.picks;
            int filed = (int) picks.stream().filter(pick -> pick.candidateId() != null).count();
            String sources = found.people().stream().map(FoundPerson::provider).distinct()
                    .collect(Collectors.joining("+"));
            return new CompanyOutcome(company.triageCompanyId(), company.companyName(), outcome,
                    found.credits() + (filing == null ? 0 : filing.bought),
                    found.cached() + (filing == null ? 0 : filing.cached), found.matched(), 0, filed,
                    picks.size() - filed, picks, sources.isEmpty() ? null : sources, notFit);
        }

        /** One company's filings: the picks made, and the live profiles bought or read back to make them. */
        private final class Filing {

            private final SourcingCompany company;
            private final List<ExecutivePick> picks = new ArrayList<>();
            private int budget;
            private int bought;
            private int cached;

            private Filing(SourcingCompany company, int budget) {
                this.company = company;
                this.budget = budget;
            }

            /**
             * A confirmed person is filed from their live profile — on file, or bought now while the company's
             * budget lasts; with it spent they are skipped.
             */
            private void fileOrSkip(FoundPerson person) {
                if (person.hasTitle()) {
                    picks.add(file(person.person(), person.researchedBy()));
                    return;
                }
                PeopleFound profile = cache.liveProfileOf(live, company.employer(), person.person(), words, budget > 0);
                budget -= profile.bought();
                bought += profile.bought();
                cached += profile.cached();
                profile.people().stream().findFirst().ifPresent(found -> picks.add(file(found, live.researchedBy())));
            }

            /** A hit refused as already mapped, or losing a race to the same name, is counted as skipped. */
            private ExecutivePick file(BrightDataPerson person, EnrichmentVendor researchedBy) {
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
        }
    }
}
