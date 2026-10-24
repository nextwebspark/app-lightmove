package app.lightmove.api.notetaker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The model's answer to {@code call-changes}, bound by Spring AI's structured output; nothing in it is trusted. */
@JsonIgnoreProperties(ignoreUnknown = true)
record CallChangesAnswer(String guard, Notice notice, Pay currentPackage, Place location, Relocation relocation,
                         Words motivation, Words expectedPackage, List<Step> nextSteps) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Notice(Integer count, String unit, String said, String at) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pay(String currency, Figure base, Figure bonus, Figure allowances, Double bonusMonthsOfBase, String said,
               String at) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Figure(Double amount, String per) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(String city, String country, String said, String at) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Relocation(Boolean open, String said, String at) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Words(String text, String said, String at) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Step(String title, String owner, String said, String at) {}
}
