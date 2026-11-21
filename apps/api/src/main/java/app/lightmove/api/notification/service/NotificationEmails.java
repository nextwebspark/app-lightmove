package app.lightmove.api.notification.service;

import app.lightmove.api.candidate.service.CandidateTaskService;
import app.lightmove.api.common.time.model.DateTimePreferences;
import app.lightmove.api.common.time.service.DateTimeFormats;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.constant.PrivacyRequestEmailKind;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.notification.constant.NotificationKind;
import app.lightmove.api.notification.model.DigestBuckets;
import app.lightmove.api.notification.model.DigestMention;
import app.lightmove.api.notification.model.Notification;
import app.lightmove.api.notification.repository.NotificationRecipientRow;
import app.lightmove.api.notification.repository.NotificationRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.task.dto.TaskResponse;
import app.lightmove.api.task.model.TaskHolder;
import app.lightmove.api.task.service.TaskService;
import app.lightmove.api.workspace.service.DateTimePreferenceService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * The bell's email: an unread assignment or mention emailed once (claimed with {@code SKIP LOCKED}, committed before
 * sending), and a weekday digest per person per day. Recipients are re-read first; a note is named, never quoted.
 * Runs a workspace at a time, behind row-level security.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEmails {

    private static final int BATCH = 200;
    private static final int DIGEST_MENTION_ROWS = 3;
    private static final Duration DIGEST_HORIZON = Duration.ofHours(36);

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
    private final TenantResolvers resolvers;
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
    private final DateTimePreferenceService dateTimePreferences;
    private final TaskDigestComposer digestComposer;
    private final NotificationLinks links;

    @Scheduled(cron = "${lightmove.notifications.email-scan}", zone = "UTC")
    public void scheduledEmails() {
        emailUnreadAt(clock.instant());
    }

    /** One pass; a row that fails is logged and the rows after it still sent. */
    public int emailUnreadAt(Instant now) {
        return workspaces.sum(resolvers.workspacesWithNotificationEmailsDue(now),
                workspaceId -> emailUnreadIn(workspaceId, now));
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
        String link = links.of(row.getProjectId(), row.getCandidateId(), row.getPersonId(), liveNoteId);
        User to = recipient.get();
        PrivacyRequestEmailKind privacyRequest = switch (row.getKind()) {
            case COPY_REQUESTED -> PrivacyRequestEmailKind.COPY;
            case DELETION_REQUESTED -> PrivacyRequestEmailKind.DELETION;
            case PERSON_OBJECTED -> PrivacyRequestEmailKind.OBJECTION;
            default -> null;
        };
        if (privacyRequest != null && row.getPersonId() != null) {
            String record = "%s/candidates?person=%s".formatted(properties.web().baseUrl(), row.getPersonId());
            return Optional.of(templates.buildPrivacyRequestEmail(to.getEmail(), to.getFullName(), executive,
                    privacyRequest, record));
        }
        if (row.getKind() == NotificationKind.MENTIONED && liveNoteId != null) {
            return Optional.of(templates.buildMentionedEmail(to.getEmail(), to.getFullName(), actorName, executive,
                    position, link));
        }
        if (row.getKind() == NotificationKind.TASK_ASSIGNED && row.getTaskId() != null) {
            DateTimeFormats formats = DateTimeFormats.of(dateTimePreferences.forPerson(to,
                    dateTimePreferences.workspaceDefaultsOf(row.getWorkspaceId())));
            return tasks.find(row.getWorkspaceId(), row.getTaskId())
                    .filter(task -> "open".equals(task.status())
                            && row.getRecipientUserId().equals(task.assignee().userId()))
                    .map(task -> templates.buildTaskAssignedEmail(to.getEmail(), to.getFullName(), actorName,
                            task.title(), executive, position, dueOf(task, formats), link));
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
        Map<UUID, DateTimePreferences> workspaceDefaults = new HashMap<>();
        int sent = 0;
        for (TaskHolder recipient : recipients) {
            User user = people.get(recipient.userId());
            if (user == null) {
                continue;
            }
            try {
                sent += WorkspaceScope.call(recipient.workspaceId(), () -> {
                    DateTimePreferences defaults = workspaceDefaults.computeIfAbsent(recipient.workspaceId(),
                            dateTimePreferences::workspaceDefaultsOf);
                    return digestOne(recipient, user, dateTimePreferences.forPerson(user, defaults), now);
                }) ? 1 : 0;
            } catch (RuntimeException failure) {
                log.error("The digest for user {} in workspace {} could not be sent", recipient.userId(),
                        recipient.workspaceId(), failure);
            }
        }
        return sent;
    }

    private boolean digestOne(TaskHolder recipient, User user, DateTimePreferences personal, Instant now) {
        UUID userId = recipient.userId();
        UUID workspaceId = recipient.workspaceId();
        DateTimeFormats formats = DateTimeFormats.of(personal);
        ZoneId zone = personal.zone();
        ZonedDateTime local = now.atZone(zone);
        if (local.getHour() != properties.notifications().digestHour() || isWeekend(local.getDayOfWeek())
                || !workspaceAccess.isStaff(userId, workspaceId) || !preferences.digest(userId, workspaceId)) {
            return false;
        }
        DigestBuckets buckets = DigestBuckets.of(
                tasks.dueDigestOf(userId, workspaceId, DigestBuckets.horizonOf(now, zone)), zone, now);
        List<Notification> mentions = digestMentionsStillTheirs(userId, workspaceId, now);
        if (!buckets.hasNews(mentions.size()) || !claimDigest(userId, workspaceId, local.toLocalDate())) {
            return false;
        }
        List<DigestMention> named = digestMentionsOf(workspaceId,
                mentions.subList(0, Math.min(DIGEST_MENTION_ROWS, mentions.size())));
        emailSender.send(templates.buildNotificationDigestEmail(user.getEmail(), formats.date(now),
                digestComposer.compose(buckets, formats, named, mentions.size())));
        return true;
    }

    /** The same rule {@link #stillTheirs} puts to a single mention email, asked once for every position named. */
    private List<Notification> digestMentionsStillTheirs(UUID userId, UUID workspaceId, Instant now) {
        List<Notification> unread = rows.findDigestMentions(userId, workspaceId, now);
        Set<UUID> worked = projectAccess.projectsWithAction(userId, workspaceId,
                idsOf(unread, Notification::getProjectId), ProjectAction.WORK_EXECUTE);
        return unread.stream().filter(row -> row.getProjectId() == null || worked.contains(row.getProjectId()))
                .toList();
    }

    /** Names read in three batched reads, not one per row: this runs for every holder in every workspace. */
    private List<DigestMention> digestMentionsOf(UUID workspaceId, List<Notification> unread) {
        if (unread.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> actors = new HashMap<>();
        users.findAllById(idsOf(unread, Notification::getActorUserId))
                .forEach(actor -> actors.put(actor.getId(), actor.getFullName()));
        Map<UUID, String> positions = new HashMap<>();
        projects.findAllById(idsOf(unread, Notification::getProjectId))
                .forEach(project -> positions.put(project.getId(), project.getPositionTitle()));
        Map<UUID, String> executives = people.personNamesOf(workspaceId, idsOf(unread, Notification::getPersonId));
        return unread.stream().map(row -> new DigestMention(
                nameOrElse(actors, row.getActorUserId(), "A colleague"),
                nameOrElse(executives, row.getPersonId(), "an executive"),
                nameOrElse(positions, row.getProjectId(), "a position"),
                links.of(row.getProjectId(), row.getCandidateId(), row.getPersonId(), row.getNoteId()))).toList();
    }

    private static List<UUID> idsOf(List<Notification> rows, Function<Notification, UUID> id) {
        return rows.stream().map(id).filter(Objects::nonNull).distinct().toList();
    }

    /** A null key is answered here: an immutable map throws on a null lookup rather than missing. */
    private static String nameOrElse(Map<UUID, String> names, UUID id, String fallback) {
        String name = id == null ? null : names.get(id);
        return name == null ? fallback : name;
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

    private static String dueOf(TaskResponse task, DateTimeFormats formats) {
        return task.dueAt() == null ? "with no date" : formats.dateTime(task.dueAt());
    }
}
