package app.lightmove.api.notification.service;

import app.lightmove.api.candidate.service.CandidateTaskService;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.stream.UserStreamPublisher;
import app.lightmove.api.notification.constant.NotificationFilter;
import app.lightmove.api.notification.dto.ChangeNotificationRequest;
import app.lightmove.api.notification.dto.MarkNotificationsReadRequest;
import app.lightmove.api.notification.dto.NotificationResponse;
import app.lightmove.api.notification.dto.NotificationsResponse;
import app.lightmove.api.notification.dto.UnreadCountResponse;
import app.lightmove.api.notification.model.Notification;
import app.lightmove.api.notification.repository.NotificationRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.task.service.TaskPeople;
import app.lightmove.api.task.service.TaskService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's own bell in the session's workspace: read a page, count what is unread, mark, snooze and archive.
 * Nobody reads or changes another person's rows — every query takes the caller as the recipient.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int PAGE_SIZE = 20;
    private static final UUID LAST_ID = new UUID(-1L, -1L);
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");

    private final NotificationRepository rows;
    private final TaskPeople taskPeople;
    private final TaskService tasks;
    private final CandidateTaskService people;
    private final ProjectRepository projects;
    private final UserStreamPublisher stream;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NotificationsResponse page(UUID userId, UUID workspaceId, String filter, String cursor) {
        NotificationFilter tab = ApiValueEnum.parse(NotificationFilter.class, filter, NotificationFilter.UNREAD,
                "notification filter");
        Instant now = clock.instant();
        Cursor after = Cursor.parse(cursor);
        List<Notification> page = rows.findInbox(userId, workspaceId, now, tab == NotificationFilter.UNREAD,
                after.latestAt(), after.id(), PAGE_SIZE + 1);
        boolean hasMore = page.size() > PAGE_SIZE;
        List<Notification> shown = hasMore ? page.subList(0, PAGE_SIZE) : page;
        String next = hasMore ? Cursor.of(shown.getLast()) : null;
        return new NotificationsResponse(toDtos(workspaceId, shown), next,
                rows.countUnread(userId, workspaceId, now));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unread(UUID userId, UUID workspaceId) {
        return new UnreadCountResponse(rows.countUnread(userId, workspaceId, clock.instant()));
    }

    @Transactional
    public UnreadCountResponse markRead(UUID userId, UUID workspaceId, MarkNotificationsReadRequest request) {
        Instant now = clock.instant();
        if (Boolean.TRUE.equals(request.all())) {
            rows.markAllRead(userId, workspaceId, now);
        } else if (request.ids() != null && !request.ids().isEmpty()) {
            rows.markRead(userId, workspaceId, request.ids(), now);
        }
        stream.publish(userId, workspaceId);
        return new UnreadCountResponse(rows.countUnread(userId, workspaceId, now));
    }

    @Transactional
    public NotificationResponse change(UUID userId, UUID workspaceId, UUID notificationId,
                                       ChangeNotificationRequest request) {
        Notification row = rows.requireOwn(notificationId, workspaceId, userId);
        Instant now = clock.instant();
        if (Boolean.TRUE.equals(request.read())) {
            row.markRead(now);
        } else if (Boolean.FALSE.equals(request.read())) {
            row.markUnread();
        }
        if (Boolean.TRUE.equals(request.clearSnooze())) {
            row.snoozeUntil(null);
        } else if (request.snoozedUntil() != null) {
            if (!request.snoozedUntil().isAfter(now)) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "snoozedUntil",
                        "Snooze until a time still to come");
            }
            row.snoozeUntil(request.snoozedUntil());
        }
        if (Boolean.TRUE.equals(request.archived())) {
            row.archive(now);
        }
        stream.publish(userId, workspaceId);
        return toDtos(workspaceId, List.of(row)).getFirst();
    }

    /** Every row in one pass: the people, executives, positions and tasks they name, read once each. */
    List<NotificationResponse> toDtos(UUID workspaceId, List<Notification> shown) {
        if (shown.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> actors = taskPeople.usersOf(shown.stream().map(Notification::getActorUserId).toList());
        Map<UUID, String> personNames = people.personNamesOf(workspaceId, shown.stream()
                .map(Notification::getPersonId).filter(Objects::nonNull).distinct().toList());
        Map<UUID, String> projectTitles = projects.findAllById(shown.stream().map(Notification::getProjectId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Project::getId, Project::getPositionTitle));
        Map<UUID, String> taskTitles = tasks.titlesOfTasks(workspaceId, shown.stream().map(Notification::getTaskId)
                .filter(Objects::nonNull).distinct().toList());
        return shown.stream().map(row -> new NotificationResponse(row.getId(), row.getKind().value(),
                row.getEventCount(), TaskPeople.personOf(row.getActorUserId(), actors), row.getProjectId(),
                valueOf(projectTitles, row.getProjectId()), row.getPersonId(), row.getCandidateId(),
                valueOf(personNames, row.getPersonId()), row.getTaskId(), valueOf(taskTitles, row.getTaskId()),
                row.getNoteId(), row.names(), dueOf(row), row.getLatestAt(), row.getReadAt() != null,
                row.getSnoozedUntil())).toList();
    }

    /** {@code Map.of()} answers a null key with a {@code NullPointerException}, not a null. */
    private static String valueOf(Map<UUID, String> values, UUID key) {
        return key == null ? null : values.get(key);
    }

    private static Instant dueOf(Notification row) {
        Object due = row.getDetails().get("dueAt");
        return due == null ? null : Instant.parse(due.toString());
    }

    /** {@code <epoch-micros>_<id>}: the last row of a page, which the next page starts after. */
    private record Cursor(Instant latestAt, UUID id) {

        static Cursor parse(String value) {
            if (value == null || value.isBlank()) {
                return new Cursor(FAR_FUTURE, LAST_ID);
            }
            try {
                String[] parts = value.split("_", 2);
                long micros = Long.parseLong(parts[0]);
                return new Cursor(Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                        Math.floorMod(micros, 1_000_000L) * 1_000L), UUID.fromString(parts[1]));
            } catch (RuntimeException malformed) {
                throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That page cursor is not valid",
                        "Malformed notification cursor");
            }
        }

        static String of(Notification row) {
            Instant at = row.getLatestAt();
            return (at.getEpochSecond() * 1_000_000L + at.getNano() / 1_000L) + "_" + row.getId();
        }
    }
}
