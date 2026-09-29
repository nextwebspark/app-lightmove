package app.lightmove.api.enrichment.candidate.model;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * One record of Bright Data's LinkedIn people dataset, as the Search API returns it — the profile
 * lookup's answer and a people search's hit alike. Masked values ("******* ***") are carried as they
 * arrive; {@code BrightDataPersonProfiles.toEnrichedProfile} is what reads them as absent.
 *
 * <p>{@code linkedinId} is the profile slug, the dataset's own key and the one every profile URL is
 * rebuilt from. {@code currentCompany.companyId} is the employer's slug — not a number.
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
