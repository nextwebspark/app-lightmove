package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.NationalityConfidence;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The nationality classifier's last reading of a candidate (V83), stored whole and replaced by the next
 * run. {@code category} is a {@code NationalityGroup} label or {@link #UNKNOWN}; {@code confidence} is a
 * {@link NationalityConfidence} value. Staff-only: the evidence lines reason about a person's origin,
 * so this never rides on {@code CandidateResponse}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NationalityReading(String category, String confidence, List<String> evidenceFor,
                                 List<String> evidenceAgainst, String rule, String readAt) {

    public static final String UNKNOWN = "Unknown";

    /** A reading confident enough to fill an empty nationality on its own. */
    @JsonIgnore
    public boolean isDecisive() {
        return !UNKNOWN.equals(category) && NationalityConfidence.HIGH.value().equals(confidence);
    }
}
