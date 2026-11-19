package app.lightmove.api.universe.model;

import java.time.LocalDate;
import java.util.List;
import org.springframework.modulith.NamedInterface;

/**
 * One universe row for a filtered list. Revenue is null on about nine rows in ten — that is the
 * data. The array fields are never null.
 */
@NamedInterface(name = "api", propagate = false)
public record CompanyRow(String apolloAccountId, String companyName, String industry,
                          String companyCountry, String companyCity, Integer numEmployees,
                          Long annualRevenue, String website, String logoUrl,
                          String shortDescription, Integer foundedYear,
                          String companyLinkedinUrl, String facebookUrl, String twitterUrl,
                          String companyPhone, String companyState, String companyAddress,
                          String parentCompany, Long totalFunding, String latestFunding,
                          Long latestFundingAmount, LocalDate lastRaisedAt,
                          Integer numberOfRetailLocations, List<String> keywords,
                          List<String> technologies, List<String> sicCodes,
                          List<String> naicsCodes) {}
