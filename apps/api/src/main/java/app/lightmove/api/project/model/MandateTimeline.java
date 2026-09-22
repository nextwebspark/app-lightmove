package app.lightmove.api.project.model;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ProjectType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * When a mandate started and what it is due to deliver by when. The dates health is measured against,
 * kept apart from the brief's own target start date, which says when the hire should begin and is no
 * milestone at all.
 *
 * <p>A mapping mandate has one milestone, which its client knows as the map delivery date. A search
 * has two, and the second is the one the hiring manager is waiting for.
 */
public record MandateTimeline(ProjectType type, LocalDate startDate,
                              LocalDate mappingTarget, LocalDate shortlistTarget) {

    /**
     * How much of the window to the shortlist the mapping half is given. Mirrored by the SPA, which
     * previews the same date live while the modal is open — see {@code features/projects/lib/timeline.ts}.
     * Both round half-up, so the previewed date and the saved one are the same day.
     */
    private static final double MAPPING_SHARE_OF_WINDOW = 0.6;

    /**
     * The milestone a mandate is currently judged against: the mapping target until the map is done,
     * and then — for a search that stated one — the shortlist it now owes.
     */
    public LocalDate governingMilestone(boolean mappingComplete) {
        if (type == ProjectType.EXECUTIVE_SEARCH && mappingComplete && shortlistTarget != null) {
            return shortlistTarget;
        }
        return mappingTarget;
    }

    /** Where the mapping target lands when a search states only the date its shortlist is due. */
    public static LocalDate autoMappingTarget(LocalDate startDate, LocalDate shortlistTarget) {
        long window = ChronoUnit.DAYS.between(startDate, shortlistTarget);
        return startDate.plusDays(Math.round(window * MAPPING_SHARE_OF_WINDOW));
    }

    /**
     * One requested timeline, defaulted and checked. Both the create and the patch come through here —
     * the patch having first merged what it supplied over what is stored — so a mandate cannot reach a
     * shape through editing that it could not have been created in.
     *
     * <p>Not Bean Validation: a PATCH carrying one date has to be judged against the four the row
     * already holds, and an annotation on the request record cannot see them.
     */
    public static MandateTimeline requested(ProjectType type, LocalDate startDate,
                                            LocalDate mappingTarget, LocalDate shortlistTarget,
                                            LocalDate today) {
        LocalDate start = startDate == null ? today : startDate;

        // Switching a search to mapping-only drops the shortlist rather than refusing: the type is the
        // statement the user just made, and a date for a deliverable they no longer owe is not an error
        // they can act on.
        LocalDate shortlist = type == ProjectType.MAPPING ? null : shortlistTarget;

        if (type == ProjectType.EXECUTIVE_SEARCH && shortlist == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "shortlistTargetDate",
                    "Enter the date the shortlist is due");
        }

        LocalDate mapping = mappingTarget != null || shortlist == null
                ? mappingTarget
                : autoMappingTarget(start, shortlist);

        if (mapping != null && mapping.isBefore(start)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "mappingTargetDate",
                    "The mapping date cannot fall before the project starts");
        }
        if (shortlist != null && shortlist.isBefore(start)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "shortlistTargetDate",
                    "The shortlist date cannot fall before the project starts");
        }
        if (mapping != null && shortlist != null && shortlist.isBefore(mapping)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "shortlistTargetDate",
                    "The shortlist is due before the mapping it draws on");
        }

        return new MandateTimeline(type, start, mapping, shortlist);
    }
}
