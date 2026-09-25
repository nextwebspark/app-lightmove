package app.lightmove.api.workspace.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/** Settings → General's firm persona. Lists arrive whole and replace what was there. */
public record UpdateWorkspacePersonaRequest(
        @Size(max = 2000, message = "Keep the summary under 2,000 characters")
        String summary,

        @Size(max = 20, message = "List at most 20 sectors")
        List<@Size(max = 96, message = "That sector is too long") String> sectors,

        @Size(max = 20, message = "List at most 20 competitors")
        List<@Size(max = 160, message = "That competitor name is too long") String> competitors,

        @Size(max = 20, message = "List at most 20 geographies")
        List<@Size(max = 64, message = "That geography is too long") String> geographies,

        @Size(max = 2000, message = "Keep the notes under 2,000 characters")
        String notes
) {}
