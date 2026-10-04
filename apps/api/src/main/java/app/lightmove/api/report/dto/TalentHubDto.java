package app.lightmove.api.report.dto;

import java.util.List;

/**
 * One country the mapped talent sits in — a country, not a city, as most rows carry no city.
 * {@code gccNationals} and {@code female} are counts over different denominators, the second being
 * {@code recordedGender}. {@code medianPackage} is in the brief's currency, null when none disclosed.
 */
public record TalentHubDto(String country, int count, List<LevelCountDto> depth,
                           List<String> employers, int interested, int gccNationals,
                           int female, int recordedGender, Long medianPackage, MapPointDto point) {}
