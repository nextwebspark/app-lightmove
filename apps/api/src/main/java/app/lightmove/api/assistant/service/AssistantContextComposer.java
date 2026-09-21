package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantContext;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.service.ProjectService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a turn's context from the turn's own stored facts.
 *
 * <p><b>Derived here, never taken from the client.</b> The SPA knows which screen it is on and is
 * welcome to say so, but a hint is a convenience and the guard re-checks every tool call against
 * its arguments regardless. What reaches the model is read from the workspace and the thread this
 * turn belongs to.
 *
 * <p>Composed on the request thread, where a read is one more query among several, rather than on
 * the worker — which owns a model call and should not also own a database round trip.
 */
@Service
@RequiredArgsConstructor
public class AssistantContextComposer {

    private final UserRepository users;
    private final ProjectService projects;

    /**
     * @param projectId the <b>thread's</b> mandate, not the request's. A thread is a conversation
     *                  about something, and {@code AssistantThread.projectId} is write-once
     */
    @Transactional(readOnly = true)
    public AssistantContext compose(UUID userId, UUID workspaceId, UUID projectId) {
        // filter, not just orElse: a user row with a blank full name maps to Optional.of("") and
        // would reach AssistantContext's constructor, which refuses it — turning a cosmetic gap in
        // somebody's profile into a failed turn.
        String consultant = users.findById(userId)
                .map(User::getFullName)
                .filter(fullName -> !fullName.isBlank())
                .orElse("a consultant");
        return new AssistantContext(consultant,
                projectId == null ? null : projects.factsOf(workspaceId, projectId).orElse(null));
    }
}
