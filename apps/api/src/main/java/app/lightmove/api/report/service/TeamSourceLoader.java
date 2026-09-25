package app.lightmove.api.report.service;

import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.dto.TeamMemberResponse;
import app.lightmove.api.project.service.ProjectService;
import app.lightmove.api.report.constant.ResearcherRole;
import app.lightmove.api.report.model.ResearcherIdentity;
import app.lightmove.api.report.model.TeamSources;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Names everyone the researcher breakdown counts: the mandate's staff seats, and anyone who filed an
 * executive without holding one any more. A pure client seat is left out — it files nothing and is
 * not on the team being measured.
 */
@Component
@RequiredArgsConstructor
class TeamSourceLoader {

    private final CandidateService candidates;
    private final ProjectService projects;
    private final UserRepository users;

    TeamSources load(UUID workspaceId, UUID projectId) {
        Map<UUID, UUID> addedBy = candidates.addedByOf(workspaceId, projectId);
        Map<UUID, ResearcherIdentity> researchers = new LinkedHashMap<>();
        for (TeamMemberResponse seat : projects.teamOf(workspaceId, projectId)) {
            ResearcherRole role = staffRoleOf(seat);
            if (role != null) {
                researchers.put(seat.userId(), new ResearcherIdentity(seat.userId(), seat.fullName(), seat.avatarUrl(), role));
            }
        }

        Set<UUID> former = new HashSet<>(addedBy.values());
        former.removeAll(researchers.keySet());
        users.findAllById(former).stream()
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .forEach(user -> researchers.put(user.getId(),
                        new ResearcherIdentity(user.getId(), user.getFullName(), user.getAvatarUrl(), ResearcherRole.FORMER)));
        return new TeamSources(addedBy, researchers);
    }

    private static ResearcherRole staffRoleOf(TeamMemberResponse seat) {
        if (seat.projectRoles().contains(ProjectRole.LEAD)) {
            return ResearcherRole.LEAD;
        }
        return seat.projectRoles().contains(ProjectRole.RESEARCHER) ? ResearcherRole.RESEARCHER : null;
    }
}
