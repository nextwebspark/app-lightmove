package app.lightmove.api.report.dto;

import java.util.UUID;

/** The universe companies one researcher brought into coverage by filing their first executive. */
public record CoverageShareDto(UUID userId, String name, int companies) {}
