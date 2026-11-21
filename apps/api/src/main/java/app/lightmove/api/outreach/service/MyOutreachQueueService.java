package app.lightmove.api.outreach.service;

import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.outreach.constant.ConversationScope;
import app.lightmove.api.outreach.constant.ConversationView;
import app.lightmove.api.outreach.dto.ConversationListResponse;
import app.lightmove.api.outreach.dto.ConversationRowResponse;
import app.lightmove.api.outreach.dto.MyConversationRowResponse;
import app.lightmove.api.outreach.dto.MyOutreachQueueResponse;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Home's Needs you, outreach half: each position's own queue ({@link ConversationService#list}, Needs) over the live,
 * active positions the caller holds a seat on. Mine reads the caller's part of every position they may work; Team
 * reads the whole queue of the positions they lead by their own seat, never by an admin's bypass. A position is read
 * under its own transaction.
 */
@Service
@RequiredArgsConstructor
public class MyOutreachQueueService {

    static final int MAX_POSITIONS = 25;
    static final int MAX_ROWS = 50;

    /** The order each position's own queue keeps: what is open first by kind, then soonest due, then latest. */
    static final Comparator<MyConversationRowResponse> MOST_URGENT_FIRST = Comparator
            .comparing((MyConversationRowResponse entry) -> entry.row().group(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(entry -> dueAtOf(entry.row()), Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparing(entry -> entry.row().lastActivityAt(), Comparator.nullsLast(Comparator.<Instant>reverseOrder()));

    private final WorkspaceAccess workspaceAccess;
    private final ProjectAccess projectAccess;
    private final ProjectMemberRepository seats;
    private final ProjectRepository projects;
    private final ConversationService conversations;

    public MyOutreachQueueResponse needsOf(UUID userId, UUID workspaceId, ConversationScope scope) {
        WorkspaceMember member = workspaceAccess.requireStaff(userId, workspaceId);
        Set<UUID> seated = seats.findByMemberId(member.getId()).stream()
                .map(ProjectMember::getProjectId).collect(Collectors.toSet());
        List<Project> live = projects.findByWorkspaceIdAndArchivedAtIsNullOrderByCreatedAtDesc(workspaceId).stream()
                .filter(project -> seated.contains(project.getId()) && !project.getStage().isDone())
                .toList();
        if (live.isEmpty()) {
            return new MyOutreachQueueResponse(List.of(), 0, false);
        }
        List<UUID> liveIds = live.stream().map(Project::getId).toList();
        Set<UUID> readable = scope == ConversationScope.TEAM
                ? seats.findProjectIdsWithAction(member.getId(), liveIds, ProjectAction.TEAM_MANAGE.name())
                : projectAccess.projectsWithAction(userId, workspaceId, liveIds, ProjectAction.WORK_EXECUTE);
        // The cap applies to the positions read, after deciding which may be read, so one never hides another.
        List<Project> read = live.stream().filter(project -> readable.contains(project.getId())).toList();
        boolean truncated = read.size() > MAX_POSITIONS;

        List<MyConversationRowResponse> rows = new ArrayList<>();
        long total = 0;
        for (Project project : read.stream().limit(MAX_POSITIONS).toList()) {
            ConversationListResponse queue = conversations.list(userId, workspaceId, project.getId(),
                    ConversationView.NEEDS, scope, null);
            total += queue.counts().needs();
            truncated |= queue.truncated();
            queue.rows().forEach(row -> rows.add(
                    new MyConversationRowResponse(project.getId(), project.getPositionTitle(), row)));
        }
        rows.sort(MOST_URGENT_FIRST);
        truncated |= rows.size() > MAX_ROWS;
        return new MyOutreachQueueResponse(rows.stream().limit(MAX_ROWS).toList(), total, truncated);
    }

    private static Instant dueAtOf(ConversationRowResponse row) {
        return row.action() == null ? null : row.action().dueAt();
    }
}
