package app.lightmove.api.core.stream;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live half of a project screen: anyone who may read the mandate may hold a stream telling them
 * when to read it again. Events carry a kind and nothing else — the content always comes back through
 * the guarded reads.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/stream")
@RequiredArgsConstructor
public class ProjectStreamController {

    private final ProjectStreamRegistry registry;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public SseEmitter stream(@PathVariable UUID projectId) {
        return registry.subscribe(projectId);
    }
}
