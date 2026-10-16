package app.uncava.api.task.service;

import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.project.repository.ProjectRepository;
import app.uncava.api.task.model.Task;
import app.uncava.api.task.model.TaskFallingDue;
import app.uncava.api.task.repository.TaskRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tells an assignee when an open task falls due within the hour and again once it is overdue — each once per due
 * date, claimed in one statement ({@code SKIP LOCKED}) so two instances never both announce a task. A task long
 * past its date is not news: only one that went overdue within the lookback is announced. An archived position's
 * tasks are not claimed, so one restored within the lookback is still announced.
 */
@Component
@RequiredArgsConstructor
public class TaskDueScanner {

    private static final Duration OVERDUE_LOOKBACK = Duration.ofDays(2);
    private static final int BATCH = 500;

    private static final String ON_LIVE_POSITION =
            "  AND project_id NOT IN (" + ProjectRepository.ARCHIVED_IDS_SQL + ")\n";

    private static final String CLAIM_DUE_SOON = """
            UPDATE app_lm_task SET due_soon_notified_at = ?, version = version + 1
             WHERE id IN (SELECT id FROM app_lm_task
                           WHERE status = 'OPEN' AND due_soon_notified_at IS NULL AND due_at > ? AND due_at <= ?
            """ + ON_LIVE_POSITION + """
                           ORDER BY due_at LIMIT ? FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private static final String CLAIM_OVERDUE = """
            UPDATE app_lm_task SET overdue_notified_at = ?, due_soon_notified_at = coalesce(due_soon_notified_at, ?),
                   version = version + 1
             WHERE id IN (SELECT id FROM app_lm_task
                           WHERE status = 'OPEN' AND overdue_notified_at IS NULL AND due_at <= ? AND due_at > ?
            """ + ON_LIVE_POSITION + """
                           ORDER BY due_at LIMIT ? FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TaskRepository tasks;
    private final ApplicationEventPublisher events;
    private final UncavaProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${uncava.notifications.due-scan}", zone = "UTC")
    public void scheduledScan() {
        scanAt(clock.instant());
    }

    /**
     * One pass; the announcements go once the claims commit. The transaction is the template's, not an annotation's:
     * the scheduled call is a self-invocation, and without one the after-commit listeners never hear a claim.
     */
    public int scanAt(Instant now) {
        Integer announced = transactions.execute(status -> claimAndAnnounce(now));
        return announced == null ? 0 : announced;
    }

    private int claimAndAnnounce(Instant now) {
        Timestamp at = Timestamp.from(now);
        List<UUID> dueSoon = jdbc.queryForList(CLAIM_DUE_SOON, UUID.class, at, at,
                Timestamp.from(now.plus(properties.notifications().dueSoonWindow())), BATCH);
        List<UUID> overdue = jdbc.queryForList(CLAIM_OVERDUE, UUID.class, at, at, at,
                Timestamp.from(now.minus(OVERDUE_LOOKBACK)), BATCH);
        tasks.findAllById(dueSoon).forEach(task -> announce(task, false));
        tasks.findAllById(overdue).forEach(task -> announce(task, true));
        return dueSoon.size() + overdue.size();
    }

    private void announce(Task task, boolean isOverdue) {
        events.publishEvent(new TaskFallingDue(task.getWorkspaceId(), task.getProjectId(), task.getPersonId(),
                task.getCandidateId(), task.getId(), task.getAssigneeUserId(), task.getDueAt(), isOverdue));
    }
}
