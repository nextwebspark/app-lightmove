package app.lightmove.api.report.dto;

import java.util.List;

/**
 * One place the mapped talent concentrates in. {@code city} is null for a hub known only by its
 * country. {@code depth} is the headcount per seniority level, {@code employers} the companies with
 * most executives here, and {@code interested} how many of the hub's executives have said yes.
 */
public record TalentHubDto(String city, String country, int count, List<LevelCountDto> depth,
                           List<String> employers, int interested) {}
