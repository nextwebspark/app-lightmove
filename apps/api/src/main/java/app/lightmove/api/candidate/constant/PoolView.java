package app.lightmove.api.candidate.constant;

import app.lightmove.api.core.constant.ApiValueEnum;
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
    UNPLACED("unplaced"),

    /** Held over a month and never told the firm holds their data (#923, GDPR Art. 14). */
    NOT_NOTIFIED("not_notified"),

    /** Asked for consent, with the request still open and unanswered (#1267). */
    CONSENT_ASKED("consent_asked"),

    /** A consent given that ends within 30 days. */
    CONSENT_ENDING("consent_ending"),

    /** Declined, or withdrew, a consent. */
    CONSENT_REFUSED("consent_refused");

    private final String value;
}
