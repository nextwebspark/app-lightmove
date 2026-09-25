package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One named allowance in a package — "Housing, 414,000" — annual, in the package's currency. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AllowanceLine(String label, Long amount) {

    public AllowanceLine {
        label = blankToNull(label);
    }

    /** The blank trailing row every repeatable list grows. */
    public boolean isEmpty() {
        return label == null && amount == null;
    }
}
