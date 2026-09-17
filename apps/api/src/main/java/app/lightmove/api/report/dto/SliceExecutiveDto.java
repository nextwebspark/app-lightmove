package app.lightmove.api.report.dto;

import java.util.UUID;

/** One executive as a slice lists them — enough to recognise and to open, no more. */
public record SliceExecutiveDto(UUID id, String fullName, String company, String status) {}
