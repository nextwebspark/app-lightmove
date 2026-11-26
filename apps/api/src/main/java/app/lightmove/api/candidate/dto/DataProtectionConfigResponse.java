package app.lightmove.api.candidate.dto;

/**
 * Whether the screens offer the suppression list, erasure, restriction and the retention review; and Uncava's own notice
 * to executives (#1490) every candidate email links, null until it is published, so the email previews draw it too.
 */
public record DataProtectionConfigResponse(boolean enabled, String candidateNoticeUrl) {}
