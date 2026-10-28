package app.lightmove.api.task.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.service.CandidateTaskService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.task.model.Task;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a task's change onto the person's timeline in the change's own transaction. The line names the task as
 * it stood — title, kind, and who held it, by name as then spelled — so a later rename never rewrites it.
 */
@Component
@RequiredArgsConstructor
class TaskTimeline {

    private final CandidateTaskService people;
    private final TaskPeople taskPeople;

    /** {@code actor} null: a rule did it. {@code previousAssignee} only on a reassignment. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Task task, UUID actor, PersonActivityKind kind, UUID previousAssignee) {
        Map<UUID, User> named = taskPeople.usersOf(Stream.of(task.getAssigneeUserId(), previousAssignee)
                .filter(Objects::nonNull).toList());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("taskId", task.getId().toString());
        details.put("title", task.getTitle());
        details.put("taskKind", task.getKind().value());
        details.put("assignee", nameOf(named, task.getAssigneeUserId()));
        details.put("dueAt", task.getDueAt() == null ? null : task.getDueAt().toString());
        if (previousAssignee != null) {
            details.put("previousAssignee", nameOf(named, previousAssignee));
        }
        if (task.getRule() != null) {
            details.put("rule", task.getRule().value());
        }
        details.values().removeIf(Objects::isNull);
        people.recordTaskLine(task.getWorkspaceId(), task.getProjectId(), task.getPersonId(), actor, kind, details);
    }

    private static String nameOf(Map<UUID, User> named, UUID userId) {
        User user = named.get(userId);
        return user == null ? null : user.getFullName();
    }
}
