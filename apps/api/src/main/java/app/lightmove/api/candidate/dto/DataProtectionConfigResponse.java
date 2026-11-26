package app.lightmove.api.candidate.dto;

/** Whether the screens offer data protection; {@code candidateNoticeUrl} is null until Uncava's notice is published. */
public record DataProtectionConfigResponse(boolean enabled, String candidateNoticeUrl) {}
