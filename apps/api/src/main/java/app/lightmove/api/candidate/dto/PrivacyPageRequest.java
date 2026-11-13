package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;

/** What a person asks for from their privacy page: {@code copy} or {@code deletion}. */
public record PrivacyPageRequest(@NotBlank String kind) {}
