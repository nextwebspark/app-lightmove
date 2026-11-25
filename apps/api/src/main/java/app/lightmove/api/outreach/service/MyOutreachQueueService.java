package app.lightmove.api.outreach.service;

import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.outreach.constant.ConversationGroup;
import app.lightmove.api.outreach.constant.ConversationScope;
import app.lightmove.api.outreach.constant.ConversationView;
import app.lightmove.api.outreach.dto.ConversationListResponse;
import app.lightmove.api.outreach.dto.ConversationRowResponse;
import app.lightmove.api.outreach.dto.MyConversationRowResponse;
import app.lightmove.api.outreach.dto.MyOutreachQueueResponse;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

/**
 * Home's Needs your attention, outreach half: each position's own queue ({@link ConversationService#list}, Needs) over the live,
 * active positions the caller holds a seat on, merged in the queue's own order ({@link ConversationService#NEEDS_ORDER}).
 * Mine reads the caller's part of every position they may work; Team reads the whole queue of the positions they lead
 * by their own seat, never by an admin's bypass. One read-only transaction for the whole read. The merged rows are cut
 * to {@link #MAX_ROWS} most pressing first, as Home ranks them, so a reply, a delivery problem or an overdue action is
 * never the row the cap drops.
 */
@Service
@RequiredArgsConstructor
public class MyOutreachQueueService {

    static final int MAX_POSITIONS = 25;
    static final int MAX_ROWS = 50;

    private final WorkspaceAccess workspaceAccess;
    private final ProjectAccess projectAccess;
    private final ProjectMemberRepository seats;
    private final MySeatedPositions seatedPositions;
    private final ConversationService conversations;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MyOutreachQueueResponse needsOf(UUID userId, UUID workspaceId, ConversationScope scope) {
        WorkspaceMember member = workspaceAccess.requireStaff(userId, workspaceId);
        List<Project> live = seatedPositions.liveOf(member);
        if (live.isEmpty()) {
            return new MyOutreachQueueResponse(List.of(), false);
        }
        List<UUID> liveIds = live.stream().map(Project::getId).toList();
        Set<UUID> readable = scope == ConversationScope.TEAM
                ? seats.findProjectIdsWithAction(member.getId(), liveIds, ProjectAction.TEAM_MANAGE.name())
                : projectAccess.projectsWithAction(userId, workspaceId, liveIds, ProjectAction.WORK_EXECUTE);
        // The cap applies to the positions read, after deciding which may be read, so one never hides another.
        List<Project> read = live.stream().filter(project -> readable.contains(project.getId())).toList();
        boolean truncated = read.size() > MAX_POSITIONS;

        List<MyConversationRowResponse> rows = new ArrayList<>();
        for (Project project : read.stream().limit(MAX_POSITIONS).toList()) {
            ConversationListResponse queue = conversations.list(userId, workspaceId, project.getId(),
                    ConversationView.NEEDS, scope, null);
            truncated |= queue.truncated();
            queue.rows().forEach(row -> rows.add(
                    new MyConversationRowResponse(project.getId(), project.getPositionTitle(), row)));
        }
        // The Outreach page's order puts Delivery problems last; cut in that order, a long queue dropped them first.
        rows.sort(mostPressingFirst(clock.instant()));
        truncated |= rows.size() > MAX_ROWS;
        return new MyOutreachQueueResponse(rows.stream().limit(MAX_ROWS).toList(), truncated);
    }

    static Comparator<MyConversationRowResponse> mostPressingFirst(Instant now) {
        return Comparator.comparing((MyConversationRowResponse entry) -> !isPressing(entry.row(), now))
                .thenComparing(entry -> dueAtOf(entry.row()), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MyConversationRowResponse::row, ConversationService.NEEDS_ORDER);
    }

    private static boolean isPressing(ConversationRowResponse row, Instant now) {
        if (row.group() == ConversationGroup.NEEDS_REPLY || row.group() == ConversationGroup.ATTENTION) {
            return true;
        }
        Instant due = dueAtOf(row);
        return due != null && due.isBefore(now);
    }

    private static Instant dueAtOf(ConversationRowResponse row) {
        return row.action() == null ? null : row.action().dueAt();
    }
}
