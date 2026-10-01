package app.lightmove.api.enrichment.common.model;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** ContactOut's People Count: the matches, and how many of them it holds each channel for — all free. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ContactOutCount(Long totalResults, Long estimatedPersonalEmails, Long estimatedWorkEmails,
                              Long estimatedPhones) {

    public static final ContactOutCount NONE = new ContactOutCount(0L, 0L, 0L, 0L);

    public long total() {
        return totalResults == null ? 0 : totalResults;
    }
}
