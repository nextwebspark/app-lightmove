package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.TextUtils.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One named allowance in a package — "Housing, 414,000" — annual, in the package's currency. A figure
 * typed with no name is still an allowance, so it is filed under a generic one rather than read back
 * as a blank heading.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AllowanceLine(String label, Long amount) {

    public AllowanceLine {
        label = blankToNull(label);
        if (label == null && amount != null) {
            label = "Allowance";
        }
    }

    /** The blank trailing row every repeatable list grows. */
    public boolean isEmpty() {
        return label == null && amount == null;
    }
}
