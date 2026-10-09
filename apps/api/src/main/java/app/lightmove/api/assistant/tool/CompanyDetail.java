package app.lightmove.api.assistant.tool;

import app.lightmove.api.common.industry.model.ResolvedIndustry;
import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.List;

/** One company as a ranking reads it: what it does, where, how big, and where the mandate already has it. */
public record CompanyDetail(String apolloAccountId, String linkedinSlug, String companyName, String industry,
                            String sectorGroup, String country, String city, Integer employees,
                            Integer foundedYear, String about, List<String> niche, String mandateStage) {

    static final int MAX_ABOUT = 160;
    static final int NICHE_SHOWN = 8;

    static CompanyDetail ofRow(CompanyRow row, List<String> niche) {
        return new CompanyDetail(row.apolloAccountId(), LinkedInUrls.companySlugOrNull(row.companyLinkedinUrl()),
                row.companyName(), row.industry(), sectorOf(row.industry()), row.companyCountry(),
                row.companyCity(), row.numEmployees(), row.foundedYear(), shortened(row.shortDescription()), niche,
                null);
    }

    /** The industry as a capture files it: the vendor's V2 leaf canonicalised to the universe's label. */
    static CompanyDetail ofPage(VendorCompanyRecord page) {
        String industry = page.asCapturedDetails().map(CapturedCompanyDetails::industry).orElse(page.industry());
        List<String> specialties = page.keywords() == null ? List.of()
                : page.keywords().stream().limit(NICHE_SHOWN).toList();
        return new CompanyDetail(null, page.linkedinSlug(), page.companyName(), industry, sectorOf(industry),
                page.companyCountry(), page.companyCity(), page.employeesInLinkedin(), page.foundedYear(),
                shortened(page.about()), specialties, null);
    }

    static CompanyDetail ofKept(String linkedinSlug, CapturedCompanyDetails kept) {
        return new CompanyDetail(null, linkedinSlug, kept.companyName(), kept.industry(), sectorOf(kept.industry()),
                kept.companyCountry(), kept.companyCity(), kept.numEmployees(), kept.foundedYear(),
                shortened(kept.shortDescription()), List.of(), null);
    }

    static CompanyDetail ofFiled(TriageCompanyResponse filed, List<String> niche, String stageToken) {
        return new CompanyDetail(filed.apolloAccountId(), LinkedInUrls.companySlugOrNull(filed.companyLinkedinUrl()),
                filed.companyName(), filed.industry(), sectorOf(filed.industry()), filed.companyCountry(),
                filed.companyCity(), filed.numEmployees(), filed.foundedYear(), shortened(filed.shortDescription()),
                niche, stageToken);
    }

    CompanyDetail inMandateAs(String stageToken) {
        return new CompanyDetail(apolloAccountId, linkedinSlug, companyName, industry, sectorGroup, country, city,
                employees, foundedYear, about, niche, stageToken);
    }

    private static String sectorOf(String industry) {
        ResolvedIndustry resolved = Industries.resolve(industry);
        return resolved == null ? null : resolved.sectorGroup();
    }

    private static String shortened(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String flattened = text.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_ABOUT ? flattened : flattened.substring(0, MAX_ABOUT) + "…";
    }
}
