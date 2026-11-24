package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.PersonPlacement;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.outreach.dto.MyMeetingResponse;
import app.lightmove.api.outreach.dto.MyMeetingsResponse;
import app.lightmove.api.outreach.model.PersonMeeting;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Home's Today: the meetings on the caller's own calendar between their local midnights, each placed on the live
 * position the caller works where that executive is mapped — the newest such position when there are several.
 */
@Service
@RequiredArgsConstructor
public class MyMeetingsService {

    private final WorkspaceAccess workspaceAccess;
    private final ProjectAccess projectAccess;
    private final MySeatedPositions seatedPositions;
    private final ClientRepository clients;
    private final PersonMeetingRepository meetings;
    private final CandidateOutreachService candidates;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MyMeetingsResponse todayOf(UUID userId, UUID workspaceId, String zone) {
        WorkspaceMember member = workspaceAccess.requireStaff(userId, workspaceId);
        ZoneId viewerZone = zoneOf(zone);
        LocalDate today = LocalDate.now(clock.withZone(viewerZone));
        Instant from = today.atStartOfDay(viewerZone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(viewerZone).toInstant();
        List<PersonMeeting> rows = meetings
                .findByWorkspaceIdAndUserIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                        workspaceId, userId, from, to);
        if (rows.isEmpty()) {
            return new MyMeetingsResponse(List.of());
        }

        List<Project> live = workablePositionsOf(member, userId, workspaceId);
        Set<UUID> personIds = rows.stream().map(PersonMeeting::getPersonId).collect(Collectors.toSet());
        Map<UUID, Project> byId = live.stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<UUID, Integer> newestFirst = new HashMap<>();
        for (int index = 0; index < live.size(); index++) {
            newestFirst.put(live.get(index).getId(), index);
        }
        Map<UUID, PersonPlacement> placementOf = new HashMap<>();
        candidates.placementsOf(byId.keySet(), personIds).stream()
                .sorted(Comparator.comparing(placement -> newestFirst.get(placement.projectId())))
                .forEach(placement -> placementOf.putIfAbsent(placement.personId(), placement));
        Map<UUID, String> clientNames = clients.findAllById(
                        live.stream().map(Project::getClientId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Client::getId, Client::getName));
        Map<UUID, String> names = candidates.fullNamesOf(workspaceId, personIds);
        Instant now = clock.instant();

        return new MyMeetingsResponse(rows.stream().map(row -> {
            PersonPlacement placement = placementOf.get(row.getPersonId());
            Project project = placement == null ? null : byId.get(placement.projectId());
            return new MyMeetingResponse(row.getId(), row.getTitle(), row.getStartsAt(), row.getEndsAt(),
                    row.getEndsAt().isAfter(now) ? safeLinkOf(row.getJoinUrl()) : null, row.getConferencingProvider(),
                    row.getPersonId(), names.get(row.getPersonId()),
                    project == null ? null : project.getId(), placement == null ? null : placement.candidateId(),
                    project == null ? null : project.getPositionTitle(),
                    project == null ? null : clientNames.get(project.getClientId()));
        }).toList());
    }

    private List<Project> workablePositionsOf(WorkspaceMember member, UUID userId, UUID workspaceId) {
        List<Project> live = seatedPositions.liveOf(member);
        Set<UUID> workable = projectAccess.projectsWithAction(userId, workspaceId,
                live.stream().map(Project::getId).toList(), ProjectAction.WORK_EXECUTE);
        return live.stream().filter(project -> workable.contains(project.getId())).toList();
    }

    /** A calendar's link is drawn as a link: only an https one is handed on. */
    private static String safeLinkOf(String joinUrl) {
        return joinUrl != null && joinUrl.regionMatches(true, 0, "https://", 0, 8) ? joinUrl : null;
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
