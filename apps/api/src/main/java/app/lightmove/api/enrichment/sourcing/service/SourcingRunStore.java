package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.enrichment.sourcing.constant.SourcingStage;
import app.lightmove.api.enrichment.sourcing.model.CompanyOutcome;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.repository.ExecutiveSourcingRunRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The worker's writes to its run, each its own short transaction: the worker holds no connection
 * while it waits on the vendor or the model, and every write announces itself on the project stream
 * inside the transaction that made it. {@code REQUIRES_NEW} for the reason
 * {@code CandidateService.applyResearch} gives — the worker runs after a commit whose resources are
 * still bound to the thread.
 */
@Component
@RequiredArgsConstructor
public class SourcingRunStore {

    private final ExecutiveSourcingRunRepository runs;
    private final ProjectStreamPublisher stream;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ExecutiveSourcingRun> start(UUID runId) {
        return runs.findById(runId).map(run -> {
            run.start();
            stream.publish(run.getProjectId(), ProjectStreamKind.EXECUTIVE_SOURCING);
            return run;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOutcome(UUID runId, CompanyOutcome outcome) {
        runs.findById(runId).ifPresent(run -> {
            run.recordOutcome(outcome);
            stream.publish(run.getProjectId(), ProjectStreamKind.EXECUTIVE_SOURCING);
        });
    }

    /** Apart from the outcome, so a progress write that fails never takes a company's result with it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProgressFinished(UUID runId, UUID triageCompanyId) {
        runs.markProgressFinished(runId, triageCompanyId);
    }

    /** One company's step, announced as progress alone so the panel rereads the run and not what was found. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reportProgress(UUID runId, UUID projectId, UUID triageCompanyId, SourcingStage stage, int considered,
                               int done, int total) {
        if (runs.reportProgress(runId, triageCompanyId, stage.name(), considered, done, total) > 0) {
            stream.publish(projectId, ProjectStreamKind.EXECUTIVE_SOURCING_PROGRESS);
        }
    }

    /** Read fresh before each company, so a stop asked for while the run searches is honoured at the next. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isStopRequested(UUID runId) {
        return runs.findById(runId).map(ExecutiveSourcingRun::isStopRequested).orElse(true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ExecutiveSourcingRun> finish(UUID runId) {
        return runs.findById(runId).map(run -> {
            run.finish();
            stream.publish(run.getProjectId(), ProjectStreamKind.EXECUTIVE_SOURCING);
            return run;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ExecutiveSourcingRun> fail(UUID runId, String reason) {
        return runs.findById(runId).map(run -> {
            run.fail(reason);
            stream.publish(run.getProjectId(), ProjectStreamKind.EXECUTIVE_SOURCING);
            return run;
        });
    }
}
