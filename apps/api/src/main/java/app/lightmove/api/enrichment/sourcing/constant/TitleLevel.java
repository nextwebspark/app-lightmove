package app.lightmove.api.enrichment.sourcing.constant;

import app.lightmove.api.common.constant.Seniority;

/** How senior a job title reads, lowest first, so a hit's level can be measured against the brief's seat. */
public enum TitleLevel {
    NONE,
    MANAGER,
    HEAD,
    SENIOR_VICE_PRESIDENT,
    TOP;

    /** The level a brief's seat is titled at; a brief stating none is read as the top seat. */
    public static TitleLevel ofSeat(Seniority seniority) {
        return seniority == null ? TOP : switch (seniority) {
            case BOARD, C_SUITE -> TOP;
            case N_MINUS_1 -> HEAD;
            case N_MINUS_2, N_MINUS_3 -> MANAGER;
        };
    }

    /** The level below; the lowest stays itself. */
    public TitleLevel oneDown() {
        return switch (this) {
            case NONE, MANAGER -> NONE;
            case HEAD -> MANAGER;
            case SENIOR_VICE_PRESIDENT -> HEAD;
            case TOP -> SENIOR_VICE_PRESIDENT;
        };
    }

    public int distanceTo(TitleLevel other) {
        return Math.abs(ordinal() - other.ordinal());
    }
}
