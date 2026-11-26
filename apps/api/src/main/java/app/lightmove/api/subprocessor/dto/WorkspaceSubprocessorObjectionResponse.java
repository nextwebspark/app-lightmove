package app.lightmove.api.subprocessor.dto;

import app.lightmove.api.subprocessor.model.SubprocessorObjection;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** This workspace's objection to a notice, as last written. */
public record WorkspaceSubprocessorObjectionResponse(String reason, Instant objectedAt, @Nullable String objectedBy) {

    public static WorkspaceSubprocessorObjectionResponse of(SubprocessorObjection objection) {
        return new WorkspaceSubprocessorObjectionResponse(objection.reason(), objection.objectedAt(),
                objection.objectedByName());
    }
}
