package app.lightmove.api.triagecompany.service;

import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.TriageCompany;

/** A triaged company as the API answers it. */
final class TriageCompanyResponseMapper {

    private TriageCompanyResponseMapper() {}

    static TriageCompanyResponse toDto(TriageCompany company) {
        return toDto(company, null);
    }

    /** {@code noteCount} only for a staff read: a company's notes are staff-only, their number included. */
    static TriageCompanyResponse toDto(TriageCompany company, Integer noteCount) {
        return new TriageCompanyResponse(company.getId(), company.getApolloAccountId(),
                company.getSource().value(), company.getStatus().value(), noteCount,
                company.isNoExecutiveFound(),
                company.getCompanyName(), company.getIndustry(), company.getCompanyCountry(),
                company.getCompanyCity(), company.getNumEmployees(), company.getAnnualRevenue(),
                company.getWebsite(), company.getCompanyLinkedinUrl(), company.getFoundedYear(),
                company.getShortDescription(), company.getSourceUrl(), company.getLogoUrl(),
                company.getCustomFields().asMap(), company.getCreatedAt());
    }
}
