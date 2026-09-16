package app.lightmove.api.report.dto;

import java.util.List;

/**
 * One country the mapped talent sits in. {@code depth} is the headcount per seniority level,
 * {@code employers} the companies with most executives here, and {@code interested} how many have
 * said yes.
 *
 * <p><b>A country, not a city.</b> Most researched rows carry a country and no city, so grouping
 * any finer reported those rows as unlocated and a real market as empty.
 *
 * <p>{@code gccNationals} and {@code female} are counts, not shares, and each is measured against a
 * different denominator — nationality and gender are recorded on different rows — so the screen
 * divides by what it is told rather than by {@code count}. {@code recordedGender} is that second
 * denominator.
 *
 * <p>{@code medianPackage} is the middle disclosed package of the executives here, in the brief's
 * currency, or null where none of them disclosed one. {@code point} is null until the geocoder has
 * placed the country.
 */
public record TalentHubDto(String country, int count, List<LevelCountDto> depth,
                           List<String> employers, int interested, int gccNationals,
                           int female, int recordedGender, Long medianPackage, MapPointDto point) {}
