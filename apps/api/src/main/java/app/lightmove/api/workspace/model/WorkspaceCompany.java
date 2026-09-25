package app.lightmove.api.workspace.model;

import app.lightmove.api.strategy.model.CompanyRow;

/**
 * The universe company a workspace was identified as at signup — copied once from the resolved row and
 * owned by the workspace afterwards, like a client's snapshot (V48), because the pipeline reloads the
 * universe wholesale.
 */
public record WorkspaceCompany(
        String apolloAccountId,
        String industry,
        String city,
        String country,
        String website,
        String linkedinUrl,
        String logoUrl
) {

    public static WorkspaceCompany of(CompanyRow row) {
        return new WorkspaceCompany(row.apolloAccountId(), row.industry(), row.companyCity(),
                row.companyCountry(), row.website(), row.companyLinkedinUrl(), row.logoUrl());
    }
}
