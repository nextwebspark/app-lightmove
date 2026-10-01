package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The Candidates page's quick views: which slice of the pool, before any filter. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PoolView implements ApiValueEnum {

    ALL("all"),

    /** Owned by the caller. */
    MINE("mine"),

    /** Mapped on at least one position still being worked — not delivered, not closed. */
    ACTIVE("active"),

    /** Mapped on no position at all. */
    UNPLACED("unplaced");

    private final String value;
}
