package app.uncava.api.workspace.dto;

import app.uncava.api.workspace.model.WorkspaceCompany;

/** The universe company a workspace was identified as, snapshotted at signup. */
public record WorkspaceCompanyResponse(
        String apolloAccountId,
        String industry,
        String city,
        String country,
        String website,
        String linkedinUrl,
        String logoUrl
) {

    public static WorkspaceCompanyResponse of(WorkspaceCompany company) {
        return company == null ? null : new WorkspaceCompanyResponse(company.apolloAccountId(),
                company.industry(), company.city(), company.country(), company.website(),
                company.linkedinUrl(), company.logoUrl());
    }
}
