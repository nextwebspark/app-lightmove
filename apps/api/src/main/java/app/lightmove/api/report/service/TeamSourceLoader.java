package app.lightmove.api.report.service;

import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.dto.TeamMemberResponse;
import app.lightmove.api.project.service.ProjectService;
import app.lightmove.api.report.constant.ResearcherRole;
import app.lightmove.api.report.model.ResearcherIdentity;
import app.lightmove.api.report.model.TeamSources;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Names everyone the researcher breakdown counts: the mandate's staff seats, and anyone who filed an
 * executive without holding one. A pure client seat is left out — it files nothing and is not on the
 * team being measured.
 *
 * <p>No seat is not the same as gone: a workspace admin is a lead on every mandate without being
 * seated on it, so an active admin who filed is named {@code LEAD}, and only someone who is no longer
 * an active admin of the workspace reads as {@code FORMER}.
 */
@Component
@RequiredArgsConstructor
class TeamSourceLoader {

    private final CandidateService candidates;
    private final ProjectService projects;
    private final UserRepository users;
    private final WorkspaceAccess workspaces;

    TeamSources load(UUID workspaceId, UUID projectId) {
        Map<UUID, UUID> addedBy = candidates.addedByOf(workspaceId, projectId);
        Map<UUID, ResearcherIdentity> researchers = new LinkedHashMap<>();
        for (TeamMemberResponse seat : projects.teamOf(workspaceId, projectId)) {
            ResearcherRole role = staffRoleOf(seat);
            if (role != null) {
                researchers.put(seat.userId(), new ResearcherIdentity(seat.userId(), seat.fullName(), seat.avatarUrl(), role));
            }
        }

        Set<UUID> unseated = new HashSet<>(addedBy.values());
        unseated.removeAll(researchers.keySet());
        if (unseated.isEmpty()) {
            return new TeamSources(addedBy, researchers);
        }
        Set<UUID> activeAdmins = workspaces.activeMembers(workspaceId).stream()
                .filter(member -> unseated.contains(member.getUserId()))
                .filter(workspaces::isAdmin)
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toSet());
        users.findAllById(unseated).stream()
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .forEach(user -> researchers.put(user.getId(), new ResearcherIdentity(user.getId(), user.getFullName(),
                        user.getAvatarUrl(), activeAdmins.contains(user.getId()) ? ResearcherRole.LEAD : ResearcherRole.FORMER)));
        return new TeamSources(addedBy, researchers);
    }

    private static ResearcherRole staffRoleOf(TeamMemberResponse seat) {
        if (seat.projectRoles().contains(ProjectRole.LEAD)) {
            return ResearcherRole.LEAD;
        }
        return seat.projectRoles().contains(ProjectRole.RESEARCHER) ? ResearcherRole.RESEARCHER : null;
    }
}
