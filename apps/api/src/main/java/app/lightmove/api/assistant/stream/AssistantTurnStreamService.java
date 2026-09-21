package app.lightmove.api.assistant.stream;

import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Opens a stream on one of the caller's own turns.
 *
 * <p>The guard is one query and no join: V65 puts {@code workspace_id} and {@code actor_user_id} on
 * the turn row itself, precisely so a background worker — and a read like this one — can authorise
 * without walking back to the thread. A turn that is not the caller's answers <b>404, never 403</b>,
 * for the reason {@code AssistantTurnStore.requireOwnThread} already gives: on an unconditionally
 * private tier, confirming that a colleague's row exists is the disclosure.
 *
 * <p>Deliberately not {@code @Transactional}. The ownership read is a single row and needs no
 * explicit transaction, and one opened here would stay open while the emitter is handed back to the
 * container — holding a connection for the life of a 55s stream, five of which would exhaust the
 * pool.
 */
@Service
@RequiredArgsConstructor
public class AssistantTurnStreamService {

    private final AssistantTurnRepository turns;
    private final AssistantTurnStreamRegistry registry;

    public SseEmitter stream(UUID turnId, UUID userId, UUID workspaceId, int afterSeq) {
        if (!turns.existsByIdAndWorkspaceIdAndActorUserId(turnId, workspaceId, userId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return registry.subscribe(turnId, afterSeq);
    }
}
