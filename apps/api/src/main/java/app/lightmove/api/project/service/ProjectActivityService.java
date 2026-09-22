package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.AuditOutcome;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.audit.repository.AuditEventRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.dto.ProjectActivityResponse;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What has happened on one mandate lately, read back out of the audit trail. The ledger already
 * records every act on a mandate against the mandate, so the feed is a read rather than a second
 * store nobody would keep in step.
 */
@Service
@RequiredArgsConstructor
public class ProjectActivityService {

    /** The drawer shows a recent history, not the archive. */
    private static final int MOST_RECENT = 20;

    /** The {@code target_type} every project-domain event is written under. */
    private static final String PROJECT_TARGET = "project";

    private final AuditEventRepository events;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final ProjectActivityNarrator narrator;

    @Transactional(readOnly = true)
    public List<ProjectActivityResponse> recent(UUID workspaceId, UUID projectId) {
        // Scoped (id, workspaceId) like every other load: a mandate of another firm is absent rather
        // than refused, and its trail is never reached by guessing an id.
        if (projects.findByIdAndWorkspaceId(projectId, workspaceId).isEmpty()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }

        List<AuditEvent> recorded = events
                .findByWorkspaceIdAndTargetTypeAndTargetIdAndOutcomeOrderByOccurredAtDesc(
                        workspaceId, PROJECT_TARGET, projectId.toString(), AuditOutcome.SUCCESS,
                        PageRequest.of(0, MOST_RECENT));

        Map<UUID, User> actorsById = users.findAllById(recorded.stream()
                        .map(AuditEvent::getActorUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return recorded.stream()
                .flatMap(event -> {
                    String summary = narrator.narrate(event.getEventType(), event.getMetadata());
                    if (summary == null) {
                        return Stream.<ProjectActivityResponse>empty();
                    }
                    User actor = event.getActorUserId() == null
                            ? null : actorsById.get(event.getActorUserId());
                    return Stream.of(new ProjectActivityResponse(
                            event.getEventType(), summary,
                            actor == null ? "A colleague" : actor.getFullName(),
                            actor == null ? null : actor.getAvatarUrl(),
                            event.getOccurredAt()));
                })
                .toList();
    }
}
