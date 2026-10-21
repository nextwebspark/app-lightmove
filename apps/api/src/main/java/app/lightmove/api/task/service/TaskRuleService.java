package app.lightmove.api.task.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.service.CandidateTaskService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.outreach.model.OutreachBounced;
import app.lightmove.api.outreach.model.OutreachCallBooked;
import app.lightmove.api.outreach.model.OutreachReplied;
import app.lightmove.api.task.constant.OutreachFollowUpKind;
import app.lightmove.api.task.constant.TaskKind;
import app.lightmove.api.task.constant.TaskRule;
import app.lightmove.api.task.constant.TaskStatus;
import app.lightmove.api.task.dto.TaskRulesDto;
import app.lightmove.api.task.model.OutreachFollowUp;
import app.lightmove.api.task.model.Task;
import app.lightmove.api.task.model.TaskPlan;
import app.lightmove.api.task.repository.TaskRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Outreach that needs a person files the task for them: a reply ({@code Reply to …}), a bounce ({@code Find a
 * working email for …}), a booked call ({@code Debrief …}, due as it ends). Each is filed for whoever started
 * the sequence or holds the call — the position's lead where they no longer work it — once per event: the rule
 * key is unique, so a replayed webhook files nothing. A workspace may switch any rule off; the event is still
 * announced, with no task.
 *
 * <p>Run after the outreach write commits and in a transaction of its own, so a failure here never undoes the
 * reply, bounce or booking it reacts to.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskRuleService {

    private static final Duration REPLY_WITHIN = Duration.ofDays(1);
    private static final Duration BOUNCE_WITHIN = Duration.ofDays(2);
    private static final Duration CALL_LENGTH_UNKNOWN = Duration.ofMinutes(30);

    private final JdbcTemplate jdbc;
    private final TaskRepository tasks;
    private final CandidateTaskService people;
    private final ProjectAccess projectAccess;
    private final TaskTimeline timeline;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TaskRulesDto rulesOf(UUID workspaceId) {
        Map<TaskRule, Boolean> enabled = switchesOf(workspaceId);
        return new TaskRulesDto(enabled.get(TaskRule.REPLY), enabled.get(TaskRule.BOUNCE),
                enabled.get(TaskRule.BOOKED_CALL));
    }

    @Transactional
    public TaskRulesDto change(UUID userId, UUID workspaceId, TaskRulesDto rules, HttpServletRequest httpRequest) {
        Map<TaskRule, Boolean> wanted = Map.of(TaskRule.REPLY, rules.reply(), TaskRule.BOUNCE, rules.bounce(),
                TaskRule.BOOKED_CALL, rules.bookedCall());
        wanted.forEach((rule, enabled) -> jdbc.update("""
                INSERT INTO app_lm_workspace_task_rule (workspace_id, rule, enabled, updated_by)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (workspace_id, rule)
                DO UPDATE SET enabled = excluded.enabled, updated_by = excluded.updated_by, updated_at = now()""",
                workspaceId, rule.name(), enabled, userId));
        audit.event(ProjectEventType.TASK_RULES_CHANGED).actor(userId).workspace(workspaceId).from(httpRequest)
                .detail("reply", rules.reply())
                .detail("bounce", rules.bounce())
                .detail("bookedCall", rules.bookedCall())
                .record();
        return rulesOf(workspaceId);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReplied(OutreachReplied replied) {
        follow(new FollowUpCase(OutreachFollowUpKind.REPLIED, TaskRule.REPLY, "reply:" + replied.messageId(),
                replied.workspaceId(), replied.projectId(), replied.personId(), replied.candidateId(),
                replied.senderUserId(), TaskKind.EMAIL, "Reply to %s", clock.instant().plus(REPLY_WITHIN), true));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBounced(OutreachBounced bounced) {
        follow(new FollowUpCase(OutreachFollowUpKind.BOUNCED, TaskRule.BOUNCE, "bounce:" + bounced.enrollmentId(),
                bounced.workspaceId(), bounced.projectId(), bounced.personId(), bounced.candidateId(),
                bounced.senderUserId(), TaskKind.TODO, "Find a working email for %s",
                clock.instant().plus(BOUNCE_WITHIN), true));
    }

    /** The consultant who booked a call themselves needs no telling; one the executive booked, they do. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCallBooked(OutreachCallBooked booked) {
        Instant ends = booked.endsAt() != null ? booked.endsAt() : booked.startsAt().plus(CALL_LENGTH_UNKNOWN);
        follow(new FollowUpCase(OutreachFollowUpKind.CALL_BOOKED, TaskRule.BOOKED_CALL,
                "booked_call:" + booked.projectId() + ":" + booked.personId() + ":" + booked.startsAt().getEpochSecond(),
                booked.workspaceId(), booked.projectId(), booked.personId(), booked.candidateId(),
                booked.consultantUserId(), TaskKind.FOLLOW_UP, "Debrief %s", ends, booked.viaLink()));
    }

    private void follow(FollowUpCase followUp) {
        if (tasks.existsByWorkspaceIdAndRuleKey(followUp.workspaceId(), followUp.ruleKey())) {
            return;
        }
        UUID holder = holderOf(followUp);
        if (holder == null) {
            log.info("No one works project {} any more; {} files no task", followUp.projectId(), followUp.ruleKey());
            return;
        }
        Task filed = null;
        if (switchesOf(followUp.workspaceId()).get(followUp.rule())) {
            // Three quick replies are one reply to answer: an open one already stands for the rest.
            filed = followUp.rule() == TaskRule.REPLY
                    ? tasks.findFirstByWorkspaceIdAndProjectIdAndPersonIdAndRuleAndStatusOrderByDueAtAsc(
                            followUp.workspaceId(), followUp.projectId(), followUp.personId(), TaskRule.REPLY,
                            TaskStatus.OPEN).orElseGet(() -> file(followUp, holder))
                    : file(followUp, holder);
        }
        if (followUp.announce()) {
            events.publishEvent(new OutreachFollowUp(followUp.kind(), followUp.workspaceId(), followUp.projectId(),
                    followUp.personId(), followUp.candidateId(), holder, filed == null ? null : filed.getId(),
                    followUp.dueAt()));
        }
    }

    private Task file(FollowUpCase followUp, UUID holder) {
        UUID candidateId = people.findSubject(followUp.projectId(), followUp.candidateId())
                .map(subject -> subject.candidateId()).orElse(null);
        String name = people.personNamesOf(followUp.workspaceId(), List.of(followUp.personId()))
                .getOrDefault(followUp.personId(), "the executive");
        String title = followUp.titlePattern().formatted(name);
        Task task = tasks.saveAndFlush(Task.filedByRule(new TaskPlan(followUp.workspaceId(), followUp.projectId(),
                followUp.personId(), candidateId, null, null, title.length() > Task.MAX_TITLE
                        ? title.substring(0, Task.MAX_TITLE) : title, followUp.taskKind(), null, holder,
                followUp.dueAt()), followUp.rule(), followUp.ruleKey()));
        timeline.record(task, null, PersonActivityKind.TASK_CREATED, null);
        return task;
    }

    /** Whoever the event names, while they still work the position; else its first lead; else nobody. */
    private UUID holderOf(FollowUpCase followUp) {
        UUID named = followUp.namedUserId();
        if (named != null && projectAccess.allows(named, followUp.workspaceId(), followUp.projectId(),
                ProjectAction.WORK_EXECUTE)) {
            return named;
        }
        return projectAccess.leadsOf(followUp.projectId()).stream().findFirst().orElse(null);
    }

    private Map<TaskRule, Boolean> switchesOf(UUID workspaceId) {
        Map<TaskRule, Boolean> enabled = new EnumMap<>(TaskRule.class);
        for (TaskRule rule : TaskRule.values()) {
            enabled.put(rule, true);
        }
        jdbc.query("SELECT rule, enabled FROM app_lm_workspace_task_rule WHERE workspace_id = ?",
                row -> {
                    enabled.put(TaskRule.valueOf(row.getString("rule")), row.getBoolean("enabled"));
                }, workspaceId);
        return enabled;
    }

    private record FollowUpCase(OutreachFollowUpKind kind, TaskRule rule, String ruleKey, UUID workspaceId,
                                UUID projectId, UUID personId, UUID candidateId, UUID namedUserId, TaskKind taskKind,
                                String titlePattern, Instant dueAt, boolean announce) {}
}
