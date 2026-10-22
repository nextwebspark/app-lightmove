package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What a canary bot found: whether it got in and recorded, and if not, the failure it saw. */
public record NotetakerCanaryReportRequest(@NotNull NotetakerPlatform platform, boolean healthy,
                                           @Size(max = 200) String reason) {}
