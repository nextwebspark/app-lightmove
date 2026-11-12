package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.service.SuppressionGate;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.concurrent.service.ThreadContextPropagation;
import app.lightmove.api.enrichment.sourcing.constant.SourcingOutcome;
import app.lightmove.api.enrichment.sourcing.constant.SourcingStage;
import app.lightmove.api.enrichment.sourcing.constant.TitleCheck;
import app.lightmove.api.enrichment.sourcing.model.CompanyOutcome;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRequested;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.FoundPeople;
import app.lightmove.api.enrichment.sourcing.model.FoundPerson;
import app.lightmove.api.enrichment.sourcing.model.SearchCriteria;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import app.lightmove.api.enrichment.sourcing.model.Suggestion;
import app.lightmove.api.position.service.PositionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs a Find executives search after it commits, off its thread: the companies a few at a time, each one
 * {@link CompanySearch}, its confirmed people's live profiles bought while the company's budget lasts, everyone
 * judged and ranked ({@link SuggestionJudge}) and stored for the panel, the suppression list left out — nobody is
 * filed. Each company is recorded as it finishes, and a stop asked for is honoured before the next. No model is
 * called. Not {@code @Transactional}: every vendor call is made with no connection held, and the writes are each
 * their own short transaction.
 */
@Component
@Slf4j
class ExecutiveSourcingWorker {

    private static final Duration TICK_EVERY = Duration.ofSeconds(2);

    private final SourcingRunStore store;
    private final SuggestionStore suggestions;
    private final CompanySearch companySearch;
    private final LiveProfiles live;
    private final PeopleCache cache;
    private final SourcingCriteriaService criteria;
    private final SuppressionGate suppression;
    private final PositionService positions;
    private final AuditService audit;
    private final UsageRecorder usage;
    private final Clock clock;
    private final ExecutiveSourcingSettings settings;
    private final Duration peopleCacheTtl;

    ExecutiveSourcingWorker(SourcingRunStore store, SuggestionStore suggestions, CompanySearch companySearch,
                            LiveProfiles live, PeopleCache cache, SourcingCriteriaService criteria,
                            SuppressionGate suppression, PositionService positions, AuditService audit, UsageRecorder usage, Clock clock,
                            LightMoveProperties properties) {
        this.store = store;
        this.suggestions = suggestions;
        this.companySearch = companySearch;
        this.live = live;
        this.cache = cache;
        this.criteria = criteria;
        this.suppression = suppression;
        this.positions = positions;
        this.audit = audit;
        this.usage = usage;
        this.clock = clock;
        this.settings = properties.enrichment().sourcing();
        this.peopleCacheTtl = properties.enrichment().peopleCacheTtl();
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
            execute(request, started.get());
        } catch (RuntimeException failed) {
            log.error("Sourcing run {} failed", request.runId(), failed);
            store.fail(request.runId(), "The run stopped unexpectedly")
                    .ifPresent(stopped -> recordUsage(request, stopped));
        }
    }

    /**
     * Closing the pool waits for every company already started; one not started by the deadline is reported as
     * not reached, and one not started once a stop was asked for as stopped, rather than cancelled mid-search.
     */
    private void execute(ExecutiveSourcingRequested request, ExecutiveSourcingRun started) {
        SourcingBrief brief = SourcingBrief.of(positions.briefOf(request.workspaceId(), request.projectId()));
        SearchCriteria searched = started.getCriteria() != null ? started.getCriteria()
                : criteria.current(request.workspaceId(), request.projectId());
        suggestions.purgeCreatedBefore(Instant.now(clock).minus(peopleCacheTtl));
        Run run = new Run(request, brief, searched, Instant.now(clock).plus(settings.runDeadline()));

        try (ExecutorService pool = Executors.newFixedThreadPool(settings.parallelism(),
                Thread.ofVirtual().factory())) {
            started.getCompanies().forEach(company ->
                    pool.execute(ThreadContextPropagation.wrap(() -> run.record(company, run.searchOne(company)))));
        }

        store.finish(request.runId()).ifPresent(finished -> {
            audit.event(ProjectEventType.EXECUTIVE_SOURCING_COMPLETED)
                    .actor(request.requestedBy()).workspace(request.workspaceId())
                    .target(AuditService.PROJECT_TARGET, request.projectId())
                    .detail("runId", finished.getId().toString())
                    .detail("companies", finished.getCompanies().size())
                    .detail("suggested", finished.getSuggested())
                    .detail("runStatus", finished.getStatus().name())
                    .detail("vendorHits", finished.getVendorHits())
                    .detail("cachedHits", finished.getCachedHits())
                    .record();
            recordUsage(request, finished);
        });
    }

    /** The live profiles a run bought, once per run; a run that bought none used nothing. */
    private void recordUsage(ExecutiveSourcingRequested request, ExecutiveSourcingRun ended) {
        if (ended.getVendorHits() == 0) {
            return;
        }
        usage.record(new MeteredUse(request.workspaceId(), request.requestedBy(), request.projectId(),
                UsageKind.SOURCING_RUN, ended.getVendorHits(), "sourcing-run:" + ended.getId()));
    }

    /** One run's fixed inputs. */
    @RequiredArgsConstructor
    private final class Run {

        private final ExecutiveSourcingRequested request;
        private final SourcingBrief brief;
        private final SearchCriteria searched;
        private final Instant deadline;
        private final ReentrantLock recording = new ReentrantLock();

        /**
         * Serialised: the run row is one aggregate under {@code @Version}, and the companies finish on their own
         * threads. An outcome that will not save is recorded as a failure instead, so the run never finishes a
         * company short.
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
            try {
                store.markProgressFinished(request.runId(), company.triageCompanyId());
            } catch (RuntimeException unsaved) {
                log.warn("Sourcing progress at {} was not marked finished: {}", company.companyName(),
                        unsaved.toString());
            }
        }

        CompanyOutcome searchOne(SourcingCompany company) {
            if (Instant.now(clock).isAfter(deadline)) {
                return CompanyOutcome.of(company, SourcingOutcome.NOT_REACHED);
            }
            if (store.isStopRequested(request.runId())) {
                return CompanyOutcome.of(company, SourcingOutcome.STOPPED);
            }
            if (company.linkedinSlug() == null) {
                return CompanyOutcome.of(company, SourcingOutcome.NO_LINKEDIN_PAGE);
            }
            try {
                return search(company);
            } catch (RuntimeException failed) {
                log.warn("Sourcing at {} failed: {}", company.companyName(), failed.toString());
                return CompanyOutcome.of(company, SourcingOutcome.FAILED);
            }
        }

        private CompanyOutcome search(SourcingCompany company) {
            List<String> countries = searched.countriesFor(company);
            StepReporter steps = new StepReporter(company);
            FoundPeople found = withConfirmedProfiles(company, withoutSuppressed(companySearch.search(
                    company.employer(), searched.words(), brief.seniority(), countries, searched.perCompany(),
                    steps)), steps);
            steps.reached(SourcingStage.RANKING, found.considered(), 0, 0);
            List<Suggestion> judged = SuggestionJudge.of(brief, searched.words(), countries, YearMonth.now(clock))
                    .judge(found.people());
            suggestions.save(request.workspaceId(), request.projectId(), request.runId(), company.triageCompanyId(),
                    judged);
            return CompanyOutcome.searched(company, found, judged);
        }

        /** The workspace's suppression list is asked before a profile is bought for anyone or anyone is stored. */
        private FoundPeople withoutSuppressed(FoundPeople found) {
            Set<String> suppressed = suppression.suppressedProfileSlugsOf(request.workspaceId(),
                    found.people().stream().map(FoundPerson::slug).filter(Objects::nonNull).toList());
            if (suppressed.isEmpty()) {
                return found;
            }
            return found.withPeople(found.people().stream()
                    .filter(person -> !suppressed.contains(person.slug()))
                    .toList(), 0, 0);
        }

        /**
         * Everyone a free check confirmed, read from the live index — on file, or bought while the company's budget
         * lasts — so the panel shows their title and Add files them from a whole profile.
         */
        private FoundPeople withConfirmedProfiles(SourcingCompany company, FoundPeople found,
                                                  CompanySearchProgress steps) {
            int budget = searched.perCompany() - found.credits();
            int bought = 0;
            int cached = 0;
            int toConfirm = (int) found.people().stream().filter(person -> person.check() == TitleCheck.CONFIRMED)
                    .count();
            int confirmed = 0;
            List<FoundPerson> people = new ArrayList<>(found.people().size());
            for (FoundPerson person : found.people()) {
                if (person.check() != TitleCheck.CONFIRMED) {
                    people.add(person);
                    continue;
                }
                steps.reached(SourcingStage.CONFIRMING, found.considered(), confirmed++, toConfirm);
                PeopleFound profile = cache.liveProfileOf(live, company.employer(), person.person(), searched.words(),
                        budget - bought > 0);
                bought += profile.bought();
                cached += profile.cached();
                people.add(profile.people().stream().findFirst()
                        .map(read -> person.asLive(read, live.provider(), live.researchedBy()))
                        .orElse(person));
            }
            return found.withPeople(people, bought, cached);
        }

        /**
         * Writes one company's steps as they come: every change of step, and a counted step's ticks no oftener
         * than {@link #TICK_EVERY}, so a hundred checks by name are a handful of writes. A write that fails is
         * logged and the search goes on — progress is never worth a company's result.
         */
        @RequiredArgsConstructor
        private final class StepReporter implements CompanySearchProgress {

            private final SourcingCompany company;
            private SourcingStage lastStage;
            private Instant lastWrite = Instant.MIN;

            @Override
            public void reached(SourcingStage stage, int considered, int done, int total) {
                Instant now = Instant.now(clock);
                if (stage == lastStage && now.isBefore(lastWrite.plus(TICK_EVERY))) {
                    return;
                }
                lastStage = stage;
                lastWrite = now;
                try {
                    store.reportProgress(request.runId(), request.projectId(), company.triageCompanyId(), stage,
                            considered, done, total);
                } catch (RuntimeException unsaved) {
                    log.warn("Sourcing progress at {} was not saved: {}", company.companyName(),
                            unsaved.toString());
                }
            }
        }
    }
}
