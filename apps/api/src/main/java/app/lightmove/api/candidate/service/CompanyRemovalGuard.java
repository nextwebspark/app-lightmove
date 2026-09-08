package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.triagecompany.model.TriageCompanyRemovalRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Refuses to let a mandate remove a company any executive is mapped at.
 *
 * <p>V36 made the schema survive it — {@code ON DELETE SET NULL} beside a snapshotted employer name,
 * so a removal could never silently delete somebody's research — but surviving is not the same as
 * being wanted. What it produced was a person on the Companies grid with no company line to sit on:
 * no logo, no stage, no action but delete, and no way back except retyping the employer. So the app
 * refuses ahead of the schema, and the constraint stays as the floor under a path that no longer
 * reaches it (a project deleted whole, a hand-run statement).
 *
 * <p>Lives in {@code candidate} rather than in {@code triagecompany} because the dependency runs one
 * way: this listens for a question published in primitives and answers by throwing, so the company
 * side never learns that people exist.
 */
@Component
@RequiredArgsConstructor
public class CompanyRemovalGuard {

    private final CandidateRepository candidates;

    /**
     * Synchronous and inside the removing transaction, which is what makes throwing here a refusal
     * rather than a half-done delete.
     */
    @EventListener
    public void refuseWhileExecutivesAreMapped(TriageCompanyRemovalRequested removal) {
        long mapped = candidates.countByProjectIdAndTriageCompanyId(
                removal.projectId(), removal.triageCompanyId());
        if (mapped > 0) {
            throw ApiException.of(ErrorCode.TRIAGE_COMPANY_HAS_EXECUTIVES);
        }
    }
}
