package app.lightmove.api.workspace.model;

import app.lightmove.api.strategy.model.CompanyRow;

/** The universe company a workspace was picked as at signup, as a write-time snapshot. */
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
