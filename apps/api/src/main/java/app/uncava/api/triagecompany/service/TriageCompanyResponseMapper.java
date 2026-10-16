package app.uncava.api.triagecompany.service;

import app.uncava.api.triagecompany.dto.TriageCompanyResponse;
import app.uncava.api.triagecompany.model.TriageCompany;

/** A triaged company as the API answers it. */
final class TriageCompanyResponseMapper {

    private TriageCompanyResponseMapper() {}

    static TriageCompanyResponse toDto(TriageCompany company) {
        return new TriageCompanyResponse(company.getId(), company.getApolloAccountId(),
                company.getSource().value(), company.getStatus().value(), company.getNote(),
                company.isNoExecutiveFound(),
                company.getCompanyName(), company.getIndustry(), company.getCompanyCountry(),
                company.getCompanyCity(), company.getNumEmployees(), company.getAnnualRevenue(),
                company.getWebsite(), company.getCompanyLinkedinUrl(), company.getFoundedYear(),
                company.getShortDescription(), company.getSourceUrl(), company.getLogoUrl(),
                company.getCustomFields().asMap(), company.getCreatedAt());
    }
}
