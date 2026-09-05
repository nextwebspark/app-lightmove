package app.lightmove.api.dataimport.dto;

/**
 * One option in the mapping step's dropdown. Sent with the preview rather than hardcoded in the SPA,
 * so a field added to the catalogue reaches the screen without a frontend release — and so the two
 * can never disagree about what a token means.
 */
public record ImportTargetFieldDto(
        String value,
        String label,
        String target
) {}
