package app.lightmove.api.workspace.model;

import app.lightmove.api.common.persona.model.PersonaSeed;
import app.lightmove.api.core.security.dto.WorkspaceCompanyResponse;
import app.lightmove.api.universe.model.CompanyRow;

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

    public static WorkspaceCompanyResponse responseOf(WorkspaceCompany company) {
        return company == null ? null : new WorkspaceCompanyResponse(company.apolloAccountId(),
                company.industry(), company.city(), company.country(), company.website(),
                company.linkedinUrl(), company.logoUrl());
    }

    public PersonaSeed personaSeed() {
        return new PersonaSeed(apolloAccountId, industry, country);
    }
}
