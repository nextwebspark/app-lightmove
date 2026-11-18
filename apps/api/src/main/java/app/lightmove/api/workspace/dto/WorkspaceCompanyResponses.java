package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.security.dto.WorkspaceCompanyResponse;
import app.lightmove.api.workspace.model.WorkspaceCompany;

/** A workspace's universe company as the auth response and the settings screen draw it. */
public final class WorkspaceCompanyResponses {

    private WorkspaceCompanyResponses() {
    }

    public static WorkspaceCompanyResponse of(WorkspaceCompany company) {
        return company == null ? null : new WorkspaceCompanyResponse(company.apolloAccountId(),
                company.industry(), company.city(), company.country(), company.website(),
                company.linkedinUrl(), company.logoUrl());
    }
}
