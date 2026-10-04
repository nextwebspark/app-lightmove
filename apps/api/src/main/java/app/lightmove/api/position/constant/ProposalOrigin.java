package app.lightmove.api.position.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What a proposal's value was drawn from — distinct from {@link ExtractionSource}, which says how the
 * document was read. Every proposal answers {@link #DOCUMENT} today.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ProposalOrigin {

    DOCUMENT("document"),

    /** No longer produced by any proposer; kept on the wire. */
    TEMPLATE("template");

    private final String value;
}
