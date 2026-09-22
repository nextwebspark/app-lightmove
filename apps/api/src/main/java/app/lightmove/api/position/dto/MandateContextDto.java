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
) {}
