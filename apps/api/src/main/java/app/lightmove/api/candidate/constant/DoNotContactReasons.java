package app.lightmove.api.candidate.constant;

import java.util.Set;

/** The reasons a person's own objection writes on do not contact; any other reason is a consultant's free text. */
public final class DoNotContactReasons {

    public static final String OPTED_OUT_BY_EMAIL = "Opted out by email";
    public static final String OBJECTED_THROUGH_PRIVACY_PAGE = "Objected through their privacy page";
    public static final String OBJECTED_TO_THE_TEAM = "Objected when contacted, recorded by the team";

    private static final Set<String> OBJECTIONS = Set.of(OPTED_OUT_BY_EMAIL, OBJECTED_THROUGH_PRIVACY_PAGE,
            OBJECTED_TO_THE_TEAM);

    private DoNotContactReasons() {
    }

    public static boolean isObjection(String reason) {
        return reason != null && OBJECTIONS.contains(reason);
    }

    public static Set<String> objections() {
        return OBJECTIONS;
    }
}
