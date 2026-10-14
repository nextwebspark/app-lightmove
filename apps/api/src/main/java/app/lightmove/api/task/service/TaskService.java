package app.lightmove.api.task.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.LinkedNote;
import app.lightmove.api.candidate.model.TaskSubject;
import app.lightmove.api.candidate.service.CandidateTaskService;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.task.constant.MyTasksView;
import app.lightmove.api.task.constant.TaskKind;
import app.lightmove.api.task.constant.TaskStatus;
import app.lightmove.api.task.dto.CreateTaskRequest;
import app.lightmove.api.task.dto.MyTaskCountsResponse;
import app.lightmove.api.task.dto.MyTasksResponse;
import app.lightmove.api.task.dto.TaskNoteDto;
import app.lightmove.api.task.dto.TaskResponse;
import app.lightmove.api.task.dto.TasksResponse;
import app.lightmove.api.task.dto.UpdateTaskRequest;
import app.lightmove.api.task.model.DueDigest;
import app.lightmove.api.task.model.DueDigestLine;
import app.lightmove.api.task.model.Task;
import app.lightmove.api.task.model.TaskAssigned;
import app.lightmove.api.task.model.TaskPlan;
import app.lightmove.api.task.model.TaskHolder;
import app.lightmove.api.task.model.TaskPreset;
import app.lightmove.api.task.repository.TaskPresetRepository;
import app.lightmove.api.task.repository.MyTaskTally;
import app.lightmove.api.task.repository.TaskRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tasks on a position's executives — the card's Next action is the earliest open one. Staff-only throughout.
 * Anyone who works the position may file or complete one; changing, reassigning or cancelling one is its
 * creator's, its assignee's, the position's lead's ({@code TEAM_MANAGE}) or an admin's. A task goes only to
 * someone who may work the position, read again on every save. Each change is a timeline line on the person.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    /** The done tasks a drawer shows until it asks for every one; and every one is never more than this. */
    private static final int DONE_SHOWN = 3;
    private static final int DONE_CAP = 200;
    private static final int MY_TASKS_PAGE_MAX = 50;

    private final TaskRepository tasks;
    private final TaskPresetRepository presets;
    private final CandidateTaskService people;
    private final ProjectRepository projects;
    private final ProjectAccess projectAccess;
    private final WorkspaceAccess workspaceAccess;
    private final TaskPeople taskPeople;
    private final TaskTimeline timeline;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TasksResponse listOf(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, boolean allDone) {
        projects.requireInWorkspace(projectId, workspaceId);
        people.requireSubject(projectId, candidateId);
        List<Task> open = tasks.findOfCandidates(projectId, List.of(candidateId), TaskStatus.OPEN).stream()
                .sorted(TaskOrder.SOONEST_FIRST).toList();
        List<Task> done = tasks.findDoneOfCandidate(projectId, candidateId, Limit.of(allDone ? DONE_CAP : DONE_SHOWN));
        boolean managesAll = managesAll(userId, workspaceId, projectId);
        Map<UUID, TaskResponse> shown = toDtos(workspaceId, Stream.concat(open.stream(), done.stream()).toList(),
                task -> managesAll || isOwnedBy(task, userId)).stream()
                .collect(Collectors.toMap(TaskResponse::id, Function.identity()));
        return new TasksResponse(open.stream().map(task -> shown.get(task.getId())).toList(),
                done.stream().map(task -> shown.get(task.getId())).toList(),
                tasks.countDoneOfCandidate(projectId, candidateId));
    }

    @Transactional
    public TaskResponse create(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                               CreateTaskRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        TaskSubject subject = people.requireSubject(projectId, candidateId);
        requireOnTeam(request.assigneeUserId(), workspaceId, projectId);
        if (request.noteId() != null) {
            people.requireNoteAbout(workspaceId, subject.personId(), request.noteId());
        }
        UUID presetId = request.presetId() == null ? null
                : presets.requireInWorkspace(request.presetId(), workspaceId).getId();
        Task task = tasks.saveAndFlush(Task.planned(new TaskPlan(workspaceId, projectId, subject.personId(),
                subject.candidateId(), request.noteId(), presetId, request.title().strip(), kindOf(request.kind()),
                blankToNull(request.detail()), request.assigneeUserId(), request.dueAt()), userId));
        line(task, userId, PersonActivityKind.TASK_CREATED, null);
        announceAssignment(task, userId);
        audited(ProjectEventType.TASK_CREATED, task, userId, httpRequest).record();
        return single(task, true);
    }

    @Transactional
    public TaskResponse update(UUID userId, UUID workspaceId, UUID taskId, UpdateTaskRequest request,
                               HttpServletRequest httpRequest) {
        Task task = tasks.requireInWorkspace(taskId, workspaceId);
        projectAccess.requireAction(userId, workspaceId, task.getProjectId(), ProjectAction.WORK_EXECUTE);
        boolean changesTheTask = request.title() != null || request.kind() != null || request.detail() != null
                || request.dueAt() != null || request.assigneeUserId() != null;
        if (changesTheTask) {
            requireMayChange(userId, task);
        }
        EditableTask before = EditableTask.of(task);
        if (request.title() != null) {
            retitle(task, request.title());
        }
        if (request.kind() != null) {
            task.rekind(kindOf(request.kind()));
        }
        if (request.detail() != null) {
            task.describe(blankToNull(request.detail()));
        }
        if (request.dueAt() != null) {
            task.reschedule(request.dueAt());
        }
        if (request.assigneeUserId() != null) {
            reassign(task, request.assigneeUserId(), userId);
        }
        if (request.status() != null) {
            changeStatus(task, request.status(), userId);
        }
        if (!before.equals(EditableTask.of(task))) {
            audited(ProjectEventType.TASK_UPDATED, task, userId, httpRequest)
                    .detailIfPresent("status", request.status())
                    .detailIfPresent("reassigned", request.assigneeUserId() == null ? null : "true")
                    .record();
        }
        return single(task, mayChange(userId, task));
    }

    private record EditableTask(String title, TaskKind kind, String detail, Instant dueAt, UUID assigneeUserId,
                                TaskStatus status) {

        static EditableTask of(Task task) {
            return new EditableTask(task.getTitle(), task.getKind(), task.getDetail(), task.getDueAt(),
                    task.getAssigneeUserId(), task.getStatus());
        }
    }

    @Transactional
    public void cancel(UUID userId, UUID workspaceId, UUID taskId, HttpServletRequest httpRequest) {
        Task task = tasks.requireInWorkspace(taskId, workspaceId);
        projectAccess.requireAction(userId, workspaceId, task.getProjectId(), ProjectAction.WORK_EXECUTE);
        requireMayChange(userId, task);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return;
        }
        task.cancel();
        line(task, userId, PersonActivityKind.TASK_CANCELLED, null);
        audited(ProjectEventType.TASK_CANCELLED, task, userId, httpRequest).record();
    }

    /**
     * Up to {@code size} of one tab of My tasks from {@code offset}, soonest first, and every tab's count. The
     * offset is the caller's, counting only the rows it holds still open: one struck done in place has left the tab.
     * Today and overdue are the viewer's own days ({@code zone}, an IANA name; UTC when it names none): due before
     * now is overdue, later today is today, anything after — or undated — upcoming.
     */
    @Transactional(readOnly = true)
    public MyTasksResponse mine(UUID userId, UUID workspaceId, String view, String zone, long offset, int size) {
        MyTasksView tab = ApiValueEnum.parse(MyTasksView.class, view, MyTasksView.TODAY, "task view");
        Instant now = clock.instant();
        ZoneId viewerZone = zoneOf(zone);
        Instant tomorrow = LocalDate.ofInstant(now, viewerZone).plusDays(1).atStartOfDay(viewerZone).toInstant();
        MyTaskTally tally = tasks.tallyOpenAssignedTo(workspaceId, userId, now, tomorrow);
        MyTaskCountsResponse counts = new MyTaskCountsResponse((int) tally.getToday(), (int) tally.getOverdue(),
                (int) tally.getUpcoming(), (int) tasks.countOpenAssignedBy(workspaceId, userId));
        long from = Math.max(offset, 0);
        int limit = Math.clamp(size, 1, MY_TASKS_PAGE_MAX);
        List<Task> shown = switch (tab) {
            case TODAY -> tasks.findOpenAssignedToDueBetween(workspaceId, userId, now, tomorrow, from, limit);
            case OVERDUE -> tasks.findOpenAssignedToDueBefore(workspaceId, userId, now, from, limit);
            case UPCOMING -> tasks.findOpenAssignedToDueFrom(workspaceId, userId, tomorrow, from, limit);
            case ASSIGNED_BY_ME -> tasks.findOpenAssignedBy(workspaceId, userId, from, limit);
        };
        int inTab = switch (tab) {
            case TODAY -> counts.today();
            case OVERDUE -> counts.overdue();
            case UPCOMING -> counts.upcoming();
            case ASSIGNED_BY_ME -> counts.assignedByMe();
        };
        return new MyTasksResponse(toDtos(workspaceId, shown, task -> isOwnedBy(task, userId)), counts,
                from + shown.size() < inTab);
    }

    /** The morning's digest for one person: their open tasks due by the end of their day, overdue first. */
    @Transactional(readOnly = true)
    public DueDigest dueDigestOf(UUID userId, UUID workspaceId, ZoneId zone, Instant now, int lineCap) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        List<Task> due = tasks.findOpenAssignedTo(workspaceId, userId).stream()
                .filter(task -> task.getDueAt() != null
                        && !LocalDate.ofInstant(task.getDueAt(), zone).isAfter(today))
                .sorted(TaskOrder.SOONEST_FIRST)
                .toList();
        int overdue = (int) due.stream().filter(task -> task.getDueAt().isBefore(now)).count();
        List<Task> named = due.stream().limit(lineCap).toList();
        Map<UUID, String> personNames = people.personNamesOf(workspaceId,
                named.stream().map(Task::getPersonId).distinct().toList());
        Map<UUID, String> projectTitles = titlesOf(named.stream().map(Task::getProjectId).distinct().toList());
        return new DueDigest(due.size() - overdue, overdue, named.stream()
                .map(task -> new DueDigestLine(task.getTitle(), personNames.get(task.getPersonId()),
                        projectTitles.get(task.getProjectId()), task.getDueAt(), task.getDueAt().isBefore(now)))
                .toList());
    }

    /** Who holds open tasks due by {@code until}, per workspace — the people a digest may have something for. */
    @Transactional(readOnly = true)
    public List<TaskHolder> holdersDueBy(Instant until) {
        return tasks.findHoldersDueBy(until).stream()
                .map(holder -> new TaskHolder(holder.getWorkspaceId(), holder.getUserId()))
                .toList();
    }

    /** Titles of tasks by id, for the bell's lines; a task of another workspace is absent. */
    @Transactional(readOnly = true)
    public Map<UUID, String> titlesOfTasks(UUID workspaceId, Collection<UUID> taskIds) {
        return taskIds.isEmpty() ? Map.of() : tasks.findAllById(taskIds).stream()
                .filter(task -> task.getWorkspaceId().equals(workspaceId))
                .collect(Collectors.toMap(Task::getId, Task::getTitle));
    }

    /** One task's position and executive, for a notification's link and line. */
    @Transactional(readOnly = true)
    public Optional<TaskResponse> find(UUID workspaceId, UUID taskId) {
        return tasks.findByIdAndWorkspaceId(taskId, workspaceId)
                .map(task -> toDtos(workspaceId, List.of(task), any -> false).getFirst());
    }

    private void retitle(Task task, String title) {
        String stripped = title.strip();
        if (stripped.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "title", "A task needs a title");
        }
        UUID keptPreset = task.getPresetId() == null ? null : presets.findById(task.getPresetId())
                .filter(preset -> preset.getLabel().equalsIgnoreCase(stripped))
                .map(TaskPreset::getId)
                .orElse(null);
        task.retitle(stripped, keptPreset);
    }

    private void reassign(Task task, UUID assigneeUserId, UUID actor) {
        requireOnTeam(assigneeUserId, task.getWorkspaceId(), task.getProjectId());
        UUID previous = task.getAssigneeUserId();
        if (task.reassign(assigneeUserId)) {
            line(task, actor, PersonActivityKind.TASK_REASSIGNED, previous);
            announceAssignment(task, actor);
        }
    }

    private void changeStatus(Task task, String status, UUID actor) {
        TaskStatus wanted = ApiValueEnum.require(TaskStatus.class, status, "task status");
        if (wanted == TaskStatus.DONE && task.isOpen()) {
            task.complete(actor, clock.instant());
            line(task, actor, PersonActivityKind.TASK_COMPLETED, null);
        } else if (wanted == TaskStatus.OPEN && task.getStatus() == TaskStatus.DONE) {
            task.reopen();
            line(task, actor, PersonActivityKind.TASK_REOPENED, null);
        } else if (wanted == TaskStatus.CANCELLED) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Delete a task to cancel it",
                    "Cancelling through a PATCH");
        }
    }

    /** Nobody is told of a task they put on themselves. */
    private void announceAssignment(Task task, UUID actor) {
        if (!task.getAssigneeUserId().equals(actor)) {
            events.publishEvent(new TaskAssigned(task.getWorkspaceId(), task.getProjectId(), task.getPersonId(),
                    task.getCandidateId(), task.getId(), task.getNoteId(), task.getAssigneeUserId(), actor));
        }
    }

    private void requireOnTeam(UUID assigneeUserId, UUID workspaceId, UUID projectId) {
        if (!projectAccess.allows(assigneeUserId, workspaceId, projectId, ProjectAction.WORK_EXECUTE)) {
            throw ApiException.of(ErrorCode.TASK_ASSIGNEE_NOT_ON_TEAM);
        }
    }

    private void requireMayChange(UUID userId, Task task) {
        if (!mayChange(userId, task)) {
            throw ApiException.of(ErrorCode.TASK_NOT_YOURS);
        }
    }

    private boolean mayChange(UUID userId, Task task) {
        return isOwnedBy(task, userId) || managesAll(userId, task.getWorkspaceId(), task.getProjectId());
    }

    private static boolean isOwnedBy(Task task, UUID userId) {
        return userId.equals(task.getCreatedBy()) || userId.equals(task.getAssigneeUserId());
    }

    private boolean managesAll(UUID userId, UUID workspaceId, UUID projectId) {
        return projectAccess.allows(userId, workspaceId, projectId, ProjectAction.TEAM_MANAGE)
                || workspaceAccess.holdsAction(userId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
    }

    private void line(Task task, UUID actor, PersonActivityKind kind, UUID previousAssignee) {
        timeline.record(task, actor, kind, previousAssignee);
    }

    private AuditService.Builder audited(ProjectEventType type, Task task, UUID actor,
                                         HttpServletRequest httpRequest) {
        return audit.projectEvent(type, actor, task.getWorkspaceId(), task.getProjectId(), httpRequest)
                .detail("taskId", task.getId().toString())
                .detailIfPresent("candidateId", task.getCandidateId() == null ? null
                        : task.getCandidateId().toString());
    }

    private TaskResponse single(Task task, boolean editable) {
        return toDtos(task.getWorkspaceId(), List.of(task), any -> editable).getFirst();
    }

    /** Every task in one pass: the people, the executives and the positions they name, read once each. */
    List<TaskResponse> toDtos(UUID workspaceId, List<Task> shown, Predicate<Task> editable) {
        if (shown.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> named = taskPeople.usersOf(shown.stream()
                .flatMap(task -> Stream.of(task.getAssigneeUserId(), task.getCreatedBy(), task.getCompletedBy()))
                .toList());
        Map<UUID, String> personNames = people.personNamesOf(workspaceId,
                shown.stream().map(Task::getPersonId).distinct().toList());
        Map<UUID, String> projectTitles = titlesOf(shown.stream().map(Task::getProjectId).distinct().toList());
        Map<UUID, LinkedNote> notes = people.linkedNotesOf(workspaceId,
                shown.stream().map(Task::getNoteId).filter(Objects::nonNull).distinct().toList());
        return shown.stream().map(task -> new TaskResponse(task.getId(), task.getProjectId(),
                projectTitles.get(task.getProjectId()), task.getCandidateId(), task.getPersonId(),
                personNames.get(task.getPersonId()), task.getTitle(), task.getKind().value(), task.getDetail(),
                task.getPresetId(), TaskNoteDto.of(task.getNoteId(), notes),
                TaskPeople.personOf(task.getAssigneeUserId(), named),
                TaskPeople.personOf(task.getCreatedBy(), named), task.getDueAt(), task.getStatus().value(),
                task.getSource().value(), task.getRule() == null ? null : task.getRule().value(),
                task.getCompletedAt(), TaskPeople.personOf(task.getCompletedBy(), named), task.getCreatedAt(),
                editable.test(task))).toList();
    }

    private Map<UUID, String> titlesOf(Collection<UUID> projectIds) {
        return projects.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getPositionTitle));
    }

    static TaskKind kindOf(String kind) {
        return ApiValueEnum.require(TaskKind.class, kind, "task type");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ZoneId zoneOf(String zone) {
        if (zone == null || zone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(zone.strip());
        } catch (DateTimeException unknown) {
            return ZoneOffset.UTC;
        }
    }
}
