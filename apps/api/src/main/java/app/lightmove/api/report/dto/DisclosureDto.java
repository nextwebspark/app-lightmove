package app.lightmove.api.report.dto;

import java.util.UUID;

/**
 * One executive's disclosed package, annual and in the report's currency. {@code fixed} is base plus
 * allowances; {@code totalPackage} adds bonus and long-term incentive. {@code status} is where the
 * mandate has got to with them, which is the nearest thing to an outcome the pipeline records today.
 */
public record DisclosureDto(UUID id, String fullName, String company, String title, String country,
                            String nationality, String status, long fixed, long totalPackage, String note) {}
