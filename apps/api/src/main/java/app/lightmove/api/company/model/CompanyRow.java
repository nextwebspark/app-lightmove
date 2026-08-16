package app.lightmove.api.company.model;

import java.util.List;

/**
 * One row of the company universe, as read back for a filtered list. Wider than any single screen
 * shows: the Sourcing table lets a user choose their columns, and a row is a few hundred bytes, so
 * every offerable field travels rather than the query taking a field list and the response shape
 * depending on client UI state.
 */
public record CompanyRow(long id, String name, String domain, String website, String linkedinUrl,
                          String logo, String slogan, String description, String primaryIndustry,
                          List<String> industryTags, List<String> specialties, String hqCountry,
                          String hqCity, String employeeRange, String revenueRange, Integer founded,
                          String ownership, String ipoStatus, String orgType, String matchTier) {}
