package app.lightmove.api.report.dto;

import java.util.List;

/**
 * One city the mapped talent concentrates in; {@code country} is null where the rows name none.
 * {@code depth} is the headcount per seniority level, {@code employers} the companies with most
 * executives here, and {@code interested} how many of the hub's executives have said yes.
 *
 * <p>{@code gccNationals} and {@code female} are counts, not shares, and each is measured against a
 * different denominator — nationality and gender are recorded on different rows — so the screen
 * divides by what it is told rather than by {@code count}. {@code recordedGender} is that second
 * denominator.
 *
 * <p>{@code medianPackage} is the middle disclosed package of the executives here, in the brief's
 * currency, or null where too few of them disclosed one to say. {@code point} is null until the
 * geocoder has placed the city.
 */
public record TalentHubDto(String city, String country, int count, List<LevelCountDto> depth,
                           List<String> employers, int interested, int gccNationals,
                           int female, int recordedGender, Long medianPackage, MapPointDto point) {}
