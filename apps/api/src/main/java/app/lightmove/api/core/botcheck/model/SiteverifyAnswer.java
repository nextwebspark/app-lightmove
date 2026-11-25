package app.lightmove.api.core.botcheck.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Cloudflare's siteverify answer, the fields the check reads. */
public record SiteverifyAnswer(
        boolean success,
        String hostname,
        String action,
        @JsonProperty("error-codes") List<String> errorCodes
) {

    public List<String> errorCodesOrNone() {
        return errorCodes == null ? List.of() : errorCodes;
    }
}
