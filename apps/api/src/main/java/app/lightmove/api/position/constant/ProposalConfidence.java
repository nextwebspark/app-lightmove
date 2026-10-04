package app.lightmove.api.position.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** How far a proposed field is worth trusting before it is accepted into the brief. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ProposalConfidence {

    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    private final String value;
}
