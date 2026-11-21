package app.lightmove.api.dashboard.service;

import app.lightmove.api.project.constant.ProjectStage;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The words both halves of the library put on screen, kept in one place so they read the same everywhere. */
final class ChartLabels {

    static final String NOT_RECORDED = "Not recorded";
    static final DateTimeFormatter WEEK_ENDING = DateTimeFormatter.ofPattern("d MMM", Locale.UK);
    static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.UK);

    private ChartLabels() {
    }

    static String orNotRecorded(String value) {
        return value == null || value.isBlank() ? NOT_RECORDED : value;
    }

    static long percent(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 100.0 / whole);
    }

    /** The stage as the positions list draws it (the SPA's StagePill), never the enum's name. */
    static String stage(ProjectStage stage) {
        return switch (stage) {
            case BRIEF -> "Brief";
            case UNIVERSE -> "Universe";
            case LOCKED -> "Universe locked";
            case MAPPING -> "Mapping";
            case OUTREACH -> "Outreach live";
            case DELIVERED -> "Shortlist delivered";
            case CLOSED -> "Closed";
        };
    }
}
