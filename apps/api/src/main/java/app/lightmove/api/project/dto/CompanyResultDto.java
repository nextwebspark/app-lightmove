package app.lightmove.api.project.dto;

import java.util.List;

/**
 * One company matching a project's saved Strategy scope. Carries every field the Sourcing table can
 * show, not only the ones a given user has switched on — the visible set is a client-side
 * preference, and making the response shape depend on it would put UI state in the query key for
 * the sake of a few hundred bytes a row.
 */
public record CompanyResultDto(long id, String name, String domain, String website, String linkedinUrl,
                                String logo, String slogan, String description, String sector,
                                List<String> industryTags, List<String> specialties, String country,
                                String location, String employeeRange, String revenueRange,
                                Integer founded, String ownership, String ipoStatus, String orgType,
                                String matchTier) {}
