package app.uncava.api.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The two competency-weighting panels a brief scores a candidate against. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CompetencyPanel implements ApiValueEnum {
    TECHNICAL("technical"),
    BEHAVIOURAL("behavioural");

    private final String value;
}
