package app.lightmove.api.promptregistry.dto;

import java.util.List;

/** What one version did over the days asked for: its calls by model, and what people did with its answers. */
public record PromptVersionUsage(
        int version,
        boolean live,
        long accepted,
        long changed,
        List<PromptModelUsageResponse> models
) {
}
