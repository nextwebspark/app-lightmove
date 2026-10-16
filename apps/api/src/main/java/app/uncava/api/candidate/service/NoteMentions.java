package app.uncava.api.candidate.service;

import app.uncava.api.candidate.model.NoteMentionTokens;
import app.uncava.api.candidate.model.PersonNote;
import app.uncava.api.candidate.model.PersonNoteMentioned;
import app.uncava.api.candidate.repository.CandidateRepository;
import app.uncava.api.core.security.rbac.ProjectAccess;
import app.uncava.api.core.security.rbac.ProjectAction;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the mentions a note's body names, as far as the server allows them: only people who may work the
 * note's position, read now rather than trusted from the composer's picker, and never the writer. A note about
 * no position mentions nobody. Only someone mentioned for the first time is announced.
 */
@Component
@RequiredArgsConstructor
class NoteMentions {

    private final ProjectAccess projectAccess;
    private final CandidateRepository candidates;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.MANDATORY)
    public void keep(PersonNote note, UUID authorUserId) {
        UUID projectId = note.getProjectId();
        NoteMentionTokens tokens = NoteMentionTokens.of(note.getBody());
        if (projectId == null || tokens.isEmpty()) {
            return;
        }
        Set<UUID> named = new LinkedHashSet<>(tokens.userIds());
        if (tokens.team()) {
            named.addAll(projectAccess.seatedUsersWithAction(projectId, ProjectAction.WORK_EXECUTE));
        }
        named.remove(authorUserId);
        Set<UUID> fresh = named.stream()
                .filter(userId -> projectAccess.allows(userId, note.getWorkspaceId(), projectId,
                        ProjectAction.WORK_EXECUTE))
                .filter(userId -> jdbc.update("""
                        INSERT INTO app_lm_note_mention (note_id, user_id) VALUES (?, ?)
                        ON CONFLICT DO NOTHING""", note.getId(), userId) == 1)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (fresh.isEmpty()) {
            return;
        }
        UUID candidateId = candidates.findIdByProjectIdAndPersonId(projectId, note.getPersonId()).orElse(null);
        events.publishEvent(new PersonNoteMentioned(note.getWorkspaceId(), projectId, note.getPersonId(),
                candidateId, note.getId(), authorUserId, fresh));
    }
}
