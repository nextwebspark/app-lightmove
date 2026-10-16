package app.uncava.api.enrichment.candidate.dto;

import app.uncava.api.candidate.model.NationalityReading;
import java.util.List;

/** The nationality classifier's last reading, as the Background section offers it. Staff-only. */
public record NationalityReadingResponse(String category, String confidence, List<String> evidenceFor,
                                         List<String> evidenceAgainst, String rule, String readAt) {

    public static NationalityReadingResponse of(NationalityReading reading) {
        return new NationalityReadingResponse(reading.category(), reading.confidence(), reading.evidenceFor(),
                reading.evidenceAgainst(), reading.rule(), reading.readAt());
    }
}
