package app.lightmove.api.notification.service;

import app.lightmove.api.candidate.service.CandidateTaskService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.notification.constant.NotificationKind;
import app.lightmove.api.notification.model.Notification;
import app.lightmove.api.notification.repository.NotificationRecipientRow;
import app.lightmove.api.notification.repository.NotificationRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.task.dto.TaskResponse;
import app.lightmove.api.task.model.DueDigest;
import app.lightmove.api.task.model.DueDigestLine;
import app.lightmove.api.task.model.TaskHolder;
import app.lightmove.api.task.service.TaskService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The bell's email. An assignment or a mention still unread when its email time comes is emailed once — claimed
 * in one statement ({@code SKIP LOCKED}) and committed before anything is sent, so two instances never both send
 * it. A digest goes on a weekday morning, at {@code digest-hour} in the person's own zone, to anyone with tasks due
 * or mentions unread, claimed once per person per day. Every recipient is read again before anything goes:
 * someone who has left the position, or the workspace, is sent nothing about it. An email names a note and never
 * quotes it. Both run a workspace at a time, the notes and positions they read being behind row-level security.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEmails {

    private static final int BATCH = 200;
    private static final int DIGEST_LINES = 8;
    private static final Duration DIGEST_HORIZON = Duration.ofHours(36);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private static final String CLAIM_EMAILS = """
            UPDATE app_lm_notification SET emailed_at = ?, version = version + 1
             WHERE id IN (SELECT id FROM app_lm_notification
                           WHERE workspace_id = ? AND email_due_at <= ? AND emailed_at IS NULL AND read_at IS NULL
                             AND archived_at IS NULL
                             AND (snoozed_until IS NULL OR snoozed_until <= ?)
                             AND NOT EXISTS (SELECT 1 FROM app_lm_person_note n
                                              WHERE n.id = app_lm_notification.note_id AND n.deleted_at IS NOT NULL)
                           ORDER BY email_due_at LIMIT ? FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private final JdbcTemplate jdbc;
    private final EveryWorkspace workspaces;
    private final TransactionTemplate transactions;
    private final NotificationRepository rows;
    private final NotificationPreferences preferences;
    private final TaskService tasks;
    private final CandidateTaskService people;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final WorkspaceAccess workspaceAccess;
    private final ProjectAccess projectAccess;
    private final EmailTemplates templates;
    private final EmailSender emailSender;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${lightmove.notifications.email-scan}", zone = "UTC")
    public void scheduledEmails() {
        emailUnreadAt(clock.instant());
    }

    /** One pass; a row that fails is logged and the rows after it still sent. */
    public int emailUnreadAt(Instant now) {
        return workspaces.sum(workspaceId -> emailUnreadIn(workspaceId, now));
    }

    private int emailUnreadIn(UUID workspaceId, Instant now) {
        Timestamp at = Timestamp.from(now);
        List<UUID> claimed = transactions.execute(status -> jdbc.queryForList(CLAIM_EMAILS, UUID.class, at,
                workspaceId, at, at, BATCH));
        int sent = 0;
        for (UUID id : claimed == null ? List.<UUID>of() : claimed) {
            try {
                sent += emailOne(id) ? 1 : 0;
            } catch (RuntimeException failure) {
                log.error("The email for notification {} could not be sent", id, failure);
            }
        }
        return sent;
    }

    private boolean emailOne(UUID notificationId) {
        Optional<EmailMessage> email = transactions.execute(status -> rows.findById(notificationId)
                .filter(this::stillTheirs)
                .flatMap(this::emailOf));
        email.ifPresent(emailSender::send);
        return email != null && email.isPresent();
    }

    /** A row about an archived position is claimed and never sent, rather than sent late after a restore. */
    private boolean stillTheirs(Notification row) {
        return preferences.of(row.getRecipientUserId(), row.getWorkspaceId(), row.getKind().preference()).email()
                && workspaceAccess.isStaff(row.getRecipientUserId(), row.getWorkspaceId())
                && (row.getProjectId() == null || (!projects.existsByIdAndArchivedAtIsNotNull(row.getProjectId())
                        && projectAccess.allows(row.getRecipientUserId(), row.getWorkspaceId(),
                                row.getProjectId(), ProjectAction.WORK_EXECUTE)));
    }

    private Optional<EmailMessage> emailOf(Notification row) {
        Optional<User> recipient = users.findById(row.getRecipientUserId());
        if (recipient.isEmpty()) {
            return Optional.empty();
        }
        String actorName = Optional.ofNullable(row.getActorUserId()).flatMap(users::findById)
                .map(User::getFullName).orElse("A colleague");
        String executive = row.getPersonId() == null ? "an executive"
                : people.personNamesOf(row.getWorkspaceId(), List.of(row.getPersonId()))
                        .getOrDefault(row.getPersonId(), "an executive");
        String position = row.getProjectId() == null ? "a" : projects.findById(row.getProjectId())
                .map(Project::getPositionTitle).orElse("a");
        // A deleted note keeps its row until the purge, so whether it is still there is asked, not read off note_id.
        UUID liveNoteId = row.getNoteId() != null
                && people.linkedNotesOf(row.getWorkspaceId(), List.of(row.getNoteId())).containsKey(row.getNoteId())
                ? row.getNoteId() : null;
        String link = linkOf(row.getProjectId(), row.getCandidateId(), row.getPersonId(), liveNoteId);
        User to = recipient.get();
        if (row.getKind() == NotificationKind.MENTIONED && liveNoteId != null) {
            return Optional.of(templates.buildMentionedEmail(to.getEmail(), to.getFullName(), actorName, executive,
                    position, link));
        }
        if (row.getKind() == NotificationKind.TASK_ASSIGNED && row.getTaskId() != null) {
            ZoneId zone = to.zone();
            return tasks.find(row.getWorkspaceId(), row.getTaskId())
                    .filter(task -> "open".equals(task.status())
                            && row.getRecipientUserId().equals(task.assignee().userId()))
                    .map(task -> templates.buildTaskAssignedEmail(to.getEmail(), to.getFullName(), actorName,
                            task.title(), executive, position, dueOf(task, zone), link));
        }
        return Optional.empty();
    }

    @Scheduled(cron = "${lightmove.notifications.digest-scan}", zone = "UTC")
    public void scheduledDigests() {
        digestAt(clock.instant());
    }

    /**
     * One pass over everyone with something due or unread; each is sent at most one digest per local day. Who that is
     * is read a workspace at a time: the tasks and the bell are under row-level security.
     */
    public int digestAt(Instant now) {
        Set<TaskHolder> recipients = new LinkedHashSet<>();
        workspaces.forEach(workspaceId -> {
            recipients.addAll(tasks.holdersDueBy(now.plus(DIGEST_HORIZON)));
            for (NotificationRecipientRow mention : rows.findUnreadMentionRecipients()) {
                recipients.add(new TaskHolder(mention.getWorkspaceId(), mention.getUserId()));
            }
        });
        Map<UUID, User> people = users.findAllById(recipients.stream().map(TaskHolder::userId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        int sent = 0;
        for (TaskHolder recipient : recipients) {
            User user = people.get(recipient.userId());
            if (user == null) {
                continue;
            }
            try {
                sent += WorkspaceScope.call(recipient.workspaceId(), () -> digestOne(recipient, user, now)) ? 1 : 0;
            } catch (RuntimeException failure) {
                log.error("The digest for user {} in workspace {} could not be sent", recipient.userId(),
                        recipient.workspaceId(), failure);
            }
        }
        return sent;
    }

    private boolean digestOne(TaskHolder recipient, User user, Instant now) {
        UUID userId = recipient.userId();
        UUID workspaceId = recipient.workspaceId();
        ZoneId zone = user.zone();
        ZonedDateTime local = now.atZone(zone);
        if (local.getHour() != properties.notifications().digestHour() || isWeekend(local.getDayOfWeek())
                || !workspaceAccess.isStaff(userId, workspaceId) || !preferences.digest(userId, workspaceId)) {
            return false;
        }
        DueDigest due = tasks.dueDigestOf(userId, workspaceId, zone, now, DIGEST_LINES);
        long mentions = rows.countUnreadOfKind(userId, workspaceId, NotificationKind.MENTIONED.name());
        if ((due.isEmpty() && mentions == 0) || !claimDigest(userId, workspaceId, local.toLocalDate())) {
            return false;
        }
        List<String> lines = due.lines().stream().map(line -> lineOf(line, zone)).toList();
        emailSender.send(templates.buildNotificationDigestEmail(user.getEmail(), user.getFullName(),
                DATE.format(local), due.dueToday(), due.overdue(), (int) mentions, lines,
                properties.web().baseUrl() + "/"));
        return true;
    }

    private boolean claimDigest(UUID userId, UUID workspaceId, LocalDate day) {
        Integer claimed = transactions.execute(status -> jdbc.update("""
                INSERT INTO app_lm_notification_digest (user_id, workspace_id, digest_date) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING""", userId, workspaceId, java.sql.Date.valueOf(day)));
        return claimed != null && claimed == 1;
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private static String lineOf(DueDigestLine line, ZoneId zone) {
        String due = DATE_TIME.format(line.dueAt().atZone(zone));
        String who = line.executiveName() == null ? "" : " — " + line.executiveName();
        String where = line.positionTitle() == null ? "" : ", " + line.positionTitle();
        return (line.overdue() ? "Overdue · " : "") + line.title() + who + where + " · due " + due;
    }

    private static String dueOf(TaskResponse task, ZoneId zone) {
        return task.dueAt() == null ? "with no date" : DATE_TIME.format(task.dueAt().atZone(zone));
    }

    /** The bell's own destination (the SPA's `notificationHref`), so an email and its bell row open the same place. */
    private String linkOf(UUID projectId, UUID candidateId, UUID personId, UUID noteId) {
        String base = properties.web().baseUrl();
        if (projectId == null) {
            return base + "/";
        }
        if (candidateId != null) {
            String candidate = "%s/projects/%s/pipeline?candidate=%s".formatted(base, projectId, candidateId);
            return noteId == null ? candidate : candidate + "&note=" + noteId;
        }
        if (personId != null) {
            String person = "%s/candidates?person=%s".formatted(base, personId);
            return noteId == null ? person : person + "&tab=notes";
        }
        return "%s/projects/%s/pipeline".formatted(base, projectId);
    }
}
