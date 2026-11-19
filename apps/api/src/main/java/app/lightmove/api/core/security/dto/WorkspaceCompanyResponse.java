package app.lightmove.api.core.security.dto;

/** The universe company a workspace was identified as, snapshotted at signup. */
public record WorkspaceCompanyResponse(
        String apolloAccountId,
        String industry,
        String city,
        String country,
        String website,
        String linkedinUrl,
        String logoUrl
) {}
