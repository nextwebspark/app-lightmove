package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.constant.MandateReason;
import java.util.List;
import java.util.Map;

/** Step two as the brief returns it — internal throughout, never shown to a candidate. */
public record MandateContextDto(
        MandateReason mandateReason,
        String businessDriver,
        List<StrategicPriorityDto> strategicPriorities,
        boolean confidential,
        String internalContext,

        /** Provenance of mandateReason and businessDriver. */
        Map<String, FieldSource> fieldSources
) {

    /**
     * What a client seat may read of it: the firm's own reading of the mandate — its business driver, priorities
     * and internal notes — goes, and so does the provenance, whose keys would say a business driver exists.
     */
    public MandateContextDto withoutInternalContext() {
        return new MandateContextDto(mandateReason, null, List.of(), confidential, null, Map.of());
    }
}
