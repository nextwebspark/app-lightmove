package app.lightmove.api.mcp.dto;

import app.lightmove.api.publicapi.dto.PublicCandidate;
import app.lightmove.api.publicapi.dto.PublicCareerEntry;
import app.lightmove.api.publicapi.dto.PublicCompany;
import org.jspecify.annotations.Nullable;

/**
 * Free text written by people or providers, held to a length before it reaches a model: it is data in a field, never
 * an instruction, and a field that cannot run on cannot carry a long one. Applied to what is sent, never to what is
 * stored.
 */
final class McpFreeText {

    static final int MAX_PROSE = 2000;
    static final int MAX_LINE = 200;

    private McpFreeText() {
    }

    static @Nullable String capped(@Nullable String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    static PublicCompany capped(PublicCompany company) {
        return new PublicCompany(company.id(), company.stage(), company.name(), company.industry(), company.country(),
                company.city(), company.employees(), company.annualRevenue(), company.website(), company.linkedinUrl(),
                company.foundedYear(), capped(company.description(), MAX_PROSE), company.logoUrl(),
                company.noExecutiveFound(), company.addedAt());
    }

    static PublicCandidate capped(PublicCandidate candidate) {
        return new PublicCandidate(candidate.id(), candidate.personId(), candidate.companyId(),
                candidate.companyName(), candidate.fullName(), capped(candidate.title(), MAX_LINE),
                candidate.seniority(), candidate.status(), candidate.linkedinUrl(), candidate.city(),
                candidate.country(), candidate.nationality(), candidate.gender(), candidate.yearsExperience(),
                capped(candidate.summary(), MAX_PROSE),
                candidate.career().stream()
                        .map(entry -> new PublicCareerEntry(entry.company(), capped(entry.title(), MAX_LINE),
                                entry.period(), entry.location()))
                        .toList(),
                candidate.education(), candidate.languages(), candidate.skills(), candidate.addedAt(),
                candidate.contacts(), candidate.compensation());
    }
}
