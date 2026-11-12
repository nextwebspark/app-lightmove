package app.lightmove.api.enrichment.candidate.model;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * One Bright Data LinkedIn people record (profile lookup or search hit); masked values arrive as-is and
 * {@code BrightDataPersonProfiles.toEnrichedProfile} reads them as absent. {@code linkedinId} and
 * {@code currentCompany.companyId} are slugs, not numbers.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BrightDataPerson(String id, String linkedinId, String name, String url, String about,
                               String position, String location, String city, String countryCode,
                               String currentCompanyName, BrightDataCurrentCompany currentCompany,
                               String avatar, Boolean defaultAvatar,
                               List<BrightDataExperience> experience,
                               List<BrightDataEducation> education, List<Object> skills,
                               List<Object> languages) {

    /** The profile page this record was read from, rebuilt from the slug rather than trusted from {@code url}. */
    public String profileUrl() {
        return linkedinId == null || linkedinId.isBlank() ? null
                : "https://www.linkedin.com/in/" + linkedinId + "/";
    }

    /** The photo worth fetching — LinkedIn's placeholder silhouette is not one. */
    public String usableAvatarUrl() {
        return Boolean.TRUE.equals(defaultAvatar) ? null : avatar;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BrightDataCurrentCompany(String name, String companyId, String link) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BrightDataExperience(String company, String title, String subtitle, String duration,
                                       String companyLogoUrl, String startDate, String endDate,
                                       String location, List<BrightDataPosition> positions) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BrightDataPosition(String title, String startDate, String endDate) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BrightDataEducation(String title, String degree, String field,
                                      String startYear, String endYear) {}
}
