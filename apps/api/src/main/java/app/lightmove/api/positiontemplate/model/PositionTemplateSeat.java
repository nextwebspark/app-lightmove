package app.lightmove.api.positiontemplate.model;

/**
 * One seat of the org chart a template draws. The ids are the file's own strings rather than UUIDs, so a
 * template written by hand or by a model can say {@code "cfo"} and {@code "role"}; applying the template
 * mints fresh ids for the brief.
 *
 * <p>A seat is a title and nothing more: who sits in it and where the box was dragged are a mandate's,
 * and the mandate seat carries no title because it is the mandate's own role title.
 */
public record PositionTemplateSeat(String id, String parentId, String title, Boolean mandateSeat) {

    public static final String MANDATE_SEAT_ID = "role";

    /** Boxed so a hand-written seat may leave the flag out, as the schema allows; Jackson refuses a missing primitive. */
    public PositionTemplateSeat {
        mandateSeat = Boolean.TRUE.equals(mandateSeat);
    }

    public static PositionTemplateSeat ofMandate(String parentId) {
        return new PositionTemplateSeat(MANDATE_SEAT_ID, parentId, null, true);
    }
}
