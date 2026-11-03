package app.lightmove.api.enrichment.sourcing.constant;

import app.lightmove.api.common.constant.Seniority;
import java.util.List;

/**
 * A band of job titles a search may take, highest first: what a researcher means by "C-level down to
 * Director". Each carries the title words that mark it, so nobody types "Chief OR Head", and the
 * {@link TitleLevel} the role check reads a title at, which is why VP, Head and Director are one band.
 */
public enum SearchLevel {
    C_LEVEL(TitleLevel.TOP, List.of("Chief", "President", "CEO", "Managing Director", "General Manager")),
    SENIOR_VP(TitleLevel.SENIOR_VICE_PRESIDENT,
            List.of("SVP", "EVP", "Senior Vice President", "Executive Vice President")),
    VP_HEAD_DIRECTOR(TitleLevel.HEAD, List.of("VP", "Vice President", "Head", "Director")),
    MANAGER(TitleLevel.MANAGER, List.of("Manager", "Lead", "Principal")),
    SPECIALIST(TitleLevel.NONE, List.of("Specialist", "Officer", "Supervisor", "Generalist", "Business Partner"));

    private final TitleLevel titleLevel;
    private final List<String> words;

    SearchLevel(TitleLevel titleLevel, List<String> words) {
        this.titleLevel = titleLevel;
        this.words = words;
    }

    public TitleLevel titleLevel() {
        return titleLevel;
    }

    public List<String> words() {
        return words;
    }

    /**
     * Who a search firm maps for a role: its level and the bench below it. A C-suite role takes the top
     * three bands, an N-1 role adds managers, an N-2 or N-3 role runs from Director to specialist, and a
     * board seat is the top band alone. A brief stating no level is read as C-suite.
     */
    public static List<SearchLevel> rangeFor(Seniority seniority) {
        return seniority == null ? List.of(C_LEVEL, SENIOR_VP, VP_HEAD_DIRECTOR) : switch (seniority) {
            case BOARD -> List.of(C_LEVEL);
            case C_SUITE -> List.of(C_LEVEL, SENIOR_VP, VP_HEAD_DIRECTOR);
            case N_MINUS_1 -> List.of(C_LEVEL, SENIOR_VP, VP_HEAD_DIRECTOR, MANAGER);
            case N_MINUS_2, N_MINUS_3 -> List.of(VP_HEAD_DIRECTOR, MANAGER, SPECIALIST);
        };
    }

    /** The band the role itself is titled at. */
    public static SearchLevel ofSeat(Seniority seniority) {
        return seniority == null ? C_LEVEL : switch (seniority) {
            case BOARD, C_SUITE -> C_LEVEL;
            case N_MINUS_1 -> VP_HEAD_DIRECTOR;
            case N_MINUS_2, N_MINUS_3 -> MANAGER;
        };
    }

    /**
     * The lowest band a search buys first: the role's own, but never above Head and Director, the titles
     * most of any function's bench carries.
     */
    public static SearchLevel firstTierFloor(Seniority seniority) {
        return seniority == Seniority.N_MINUS_2 || seniority == Seniority.N_MINUS_3 ? MANAGER : VP_HEAD_DIRECTOR;
    }

    public boolean isAtLeast(SearchLevel other) {
        return ordinal() <= other.ordinal();
    }
}
