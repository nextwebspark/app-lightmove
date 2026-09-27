package app.lightmove.api.candidate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The model's reading of a candidate against the mandate's competencies, stored whole on the row
 * (V79) and replaced whole by the next run. {@code assessedAt} is an ISO instant, as
 * {@link CandidateProfile#enrichedAt} is.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateAiAssessment(String summary, CompetencyPanelAssessment technical,
                                    CompetencyPanelAssessment behavioural, String assessedAt) {}
