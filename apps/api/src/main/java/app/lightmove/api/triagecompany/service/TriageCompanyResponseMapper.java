package app.lightmove.api.triagecompany.service;

import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.triagecompany.model.TriageCompany;

/** A triaged company as the API answers it. */
final class TriageCompanyResponseMapper {

    private TriageCompanyResponseMapper() {}

    static TriageCompanyResponse toDto(TriageCompany company) {
        return toDto(company, null, null);
    }

    /**
     * {@code noteCount} and {@code addedBy} only for a staff read: a company's notes are staff-only, their
     * number included, and who filed it is the firm's own attribution, as an executive's {@code added_by} is.
     */
    static TriageCompanyResponse toDto(TriageCompany company, Integer noteCount, User addedBy) {
        return new TriageCompanyResponse(company.getId(), company.getApolloAccountId(),
                company.getSource().value(), company.getStatus().value(), noteCount,
                company.isNoExecutiveFound(),
                company.getCompanyName(), company.getIndustry(), company.getCompanyCountry(),
                company.getCompanyCity(), company.getNumEmployees(), company.getAnnualRevenue(),
                company.getWebsite(), company.getCompanyLinkedinUrl(), company.getFoundedYear(),
                company.getShortDescription(), company.getSourceUrl(), company.getLogoUrl(),
                company.getCustomFields().asMap(), company.getCreatedAt(),
                addedBy == null ? null : addedBy.getId(), addedBy == null ? null : addedBy.getFullName(),
                addedBy == null ? null : addedBy.getAvatarUrl());
    }
}
