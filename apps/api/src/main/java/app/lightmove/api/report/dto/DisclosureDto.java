package app.lightmove.api.report.dto;

import java.util.UUID;

/**
 * One executive's disclosed package, annual, in the report's currency. {@code fixed} is base plus
 * allowances; {@code totalPackage} adds bonus and long-term incentive.
 */
public record DisclosureDto(UUID id, String fullName, String company, String title, String country,
                            String nationality, String status, long fixed, long totalPackage, String note) {}
