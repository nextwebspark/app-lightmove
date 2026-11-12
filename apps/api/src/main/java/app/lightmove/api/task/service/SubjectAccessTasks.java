package app.lightmove.api.task.service;

import app.lightmove.api.candidate.model.SubjectAccessContribution;
import app.lightmove.api.candidate.model.SubjectAccessSection;
import app.lightmove.api.candidate.model.SubjectAccessSubject;
import app.lightmove.api.candidate.service.SubjectAccessContributor;
import app.lightmove.api.core.export.service.RowSnapshotReader;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A subject access export's tasks (#919): every next action the team set on the person, open or done. */
@Component
@RequiredArgsConstructor
class SubjectAccessTasks implements SubjectAccessContributor {

    private final RowSnapshotReader rows;

    @Override
    public Set<String> tables() {
        return Set.of("app_lm_task");
    }

    @Override
    public SubjectAccessContribution contributionOf(SubjectAccessSubject subject) {
        return new SubjectAccessContribution(List.of(new SubjectAccessSection("tasks", "Tasks", rows.rowsOf("""
                SELECT * FROM app_lm_task WHERE workspace_id = :workspaceId AND person_id = :personId
                ORDER BY created_at, id""", subject.parameters(),
                Set.of("workspace_id", "version", "rule_key", "due_soon_notified_at", "overdue_notified_at")))),
                Set.of());
    }
}
