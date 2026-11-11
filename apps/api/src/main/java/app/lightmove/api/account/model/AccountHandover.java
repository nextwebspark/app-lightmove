package app.lightmove.api.account.model;

import app.lightmove.api.account.dto.AccountWorkspace;
import app.lightmove.api.account.dto.HandoverPosition;
import java.util.List;
import java.util.Map;

/** What a person must hand over before their account can go: nobody else could run these. */
public record AccountHandover(List<AccountWorkspace> soleAdminOf, List<HandoverPosition> soleLeadOf) {

    public boolean isRequired() {
        return !soleAdminOf.isEmpty() || !soleLeadOf.isEmpty();
    }

    /** The refusal's properties, as the preview names them. */
    public Map<String, Object> asProperties() {
        return Map.of("soleAdminOf", soleAdminOf, "soleLeadOf", soleLeadOf);
    }
}
