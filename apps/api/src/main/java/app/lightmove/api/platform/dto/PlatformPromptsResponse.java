package app.lightmove.api.platform.dto;

import java.util.List;

public record PlatformPromptsResponse(List<PlatformPromptSummary> prompts, PlatformConfigVersionResponse config) {}
