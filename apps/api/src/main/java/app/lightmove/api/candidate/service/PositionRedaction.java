package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.PersonNoteResponse;
import app.lightmove.api.candidate.dto.PersonTimelineEntryResponse;
import app.lightmove.api.candidate.dto.PersonTimelineResponse;
import app.lightmove.api.candidate.model.PositionVisibility;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Which of a person's positions a caller may see named. A client seat names nothing; an admin, everything; anyone else
 * the positions they may work. A line or note about another position is kept, without the position's name.
 */
@Service
@RequiredArgsConstructor
public class PositionRedaction {

    private final ProjectAccess projectAccess;

    public PositionVisibility visibleTo(UUID callerId, UUID workspaceId, Collection<UUID> projectIds,
                                        UUID currentProjectId) {
        return new PositionVisibility(currentProjectId, nameableBy(callerId, workspaceId, projectIds));
    }

    /** Of {@code projectIds}, the positions the caller may see named. */
    public Set<UUID> nameableBy(UUID callerId, UUID workspaceId, Collection<UUID> projectIds) {
        return projectIds.isEmpty() ? Set.of()
                : projectAccess.projectsWithAction(callerId, workspaceId, projectIds, ProjectAction.WORK_EXECUTE);
    }

    public boolean names(UUID callerId, UUID workspaceId, UUID projectId) {
        return projectAccess.allows(callerId, workspaceId, projectId, ProjectAction.WORK_EXECUTE);
    }

    /** A page of history read from the workspace: a line made on a position the caller is not on loses its name. */
    public PersonTimelineResponse timelineFor(UUID callerId, UUID workspaceId, PersonTimelineResponse page) {
        Set<UUID> named = nameableBy(callerId, workspaceId, projectIdsOf(page.entries()));
        return new PersonTimelineResponse(page.entries().stream().map(entry -> named(entry, named)).toList(),
                page.nextCursor());
    }

    public Map<UUID, PersonTimelineEntryResponse> latestFor(UUID callerId, UUID workspaceId,
                                                            Map<UUID, PersonTimelineEntryResponse> latest) {
        Set<UUID> named = nameableBy(callerId, workspaceId, projectIdsOf(latest.values()));
        return latest.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> named(entry.getValue(), named)));
    }

    /** Notes read from the workspace: one about a position the caller is not on is listed without its title. */
    public List<PersonNoteResponse> notesFor(UUID callerId, UUID workspaceId, List<PersonNoteResponse> notes) {
        Set<UUID> named = nameableBy(callerId, workspaceId,
                notes.stream().map(PersonNoteResponse::projectId).filter(Objects::nonNull).distinct().toList());
        return notes.stream()
                .map(note -> note.projectId() == null || named.contains(note.projectId()) ? note
                        : note.withoutPositionTitle())
                .toList();
    }

    private static PersonTimelineEntryResponse named(PersonTimelineEntryResponse entry, Set<UUID> named) {
        return entry.projectId() == null || named.contains(entry.projectId()) ? entry : entry.withoutPosition();
    }

    private static List<UUID> projectIdsOf(Collection<PersonTimelineEntryResponse> entries) {
        return entries.stream().map(PersonTimelineEntryResponse::projectId).filter(Objects::nonNull).distinct().toList();
    }
}
