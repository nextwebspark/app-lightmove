package app.lightmove.api.enrichment.candidate.dto;

import app.lightmove.api.candidate.model.NationalityReading;
import java.util.List;

/** The nationality classifier's last reading, as the Background section offers it. Staff-only. */
public record NationalityReadingResponse(String category, String confidence, List<String> evidenceFor,
                                         List<String> evidenceAgainst, String rule, String readAt) {

    public static NationalityReadingResponse of(NationalityReading reading) {
        return new NationalityReadingResponse(reading.category(), reading.confidence(), reading.evidenceFor(),
                reading.evidenceAgainst(), reading.rule(), reading.readAt());
    }
}
