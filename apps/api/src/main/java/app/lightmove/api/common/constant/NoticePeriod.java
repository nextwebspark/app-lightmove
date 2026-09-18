package app.lightmove.api.common.constant;

import java.util.Map;

/**
 * How long a mandate plans to wait for somebody — one of five, on both halves of a search.
 *
 * <p>The brief states it as {@link #months()} paired with {@link NoticeUnit#MONTHS}, and an
 * executive's profile stores {@link #value()} in a free-text column. Neither column is narrowed to
 * these five: a brief written before the picker existed holds ninety days, an externally authored
 * template may carry six weeks, and a spreadsheet states whatever it states. Those are facts
 * somebody entered, and a write that refused them would clear a field nobody touched — so the
 * pickers keep an unlisted value offered as recorded, and only the choices on offer are these.
 *
 * <p>{@link #NONE} is a claim — this person can start now — and is not the blank a profile carries
 * before anybody asked. Both screens offer that blank above these five, the way every sibling
 * picker does.
 */
public enum NoticePeriod {

    NONE("None", 0),

    ONE_MONTH("1 month", 1),

    TWO_MONTHS("2 months", 2),

    THREE_MONTHS("3 months", 3),

    SIX_MONTHS("6 months", 6);

    private final String label;

    private final int months;

    NoticePeriod(String label, int months) {
        this.label = label;
        this.months = months;
    }

    public String value() {
        return label;
    }

    public int months() {
        return months;
    }

    public static NoticePeriod fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (NoticePeriod period : values()) {
            if (period.label.equalsIgnoreCase(value.trim())) {
                return period;
            }
        }
        return null;
    }

    /** The option a whole number of months names, or null where the screens offer no such option. */
    public static NoticePeriod ofMonths(Integer months) {
        if (months == null) {
            return null;
        }
        for (NoticePeriod period : values()) {
            if (period.months == months) {
                return period;
            }
        }
        return null;
    }

    /**
     * The option a count and a unit name — the brief's own pair, or a period a document or a
     * spreadsheet stated in some other unit.
     *
     * <p>Only an exact equivalent folds: ninety days and three months are one period, six weeks is
     * none of these five and answers null. Nothing is rounded to the nearest option, because a
     * rounded notice period is this code restating somebody else's figure.
     */
    public static NoticePeriod ofPair(Integer count, NoticeUnit unit) {
        if (count == null || unit == null) {
            return null;
        }
        if (count == 0) {
            return NONE;
        }
        return switch (unit) {
            case MONTHS -> ofMonths(count);
            case WEEKS -> ofMonths(MONTHS_BY_WEEKS.get(count));
            case DAYS -> ofMonths(MONTHS_BY_DAYS.get(count));
        };
    }

    private static final Map<Integer, Integer> MONTHS_BY_WEEKS = Map.of(4, 1, 8, 2, 12, 3, 26, 6);

    private static final Map<Integer, Integer> MONTHS_BY_DAYS = Map.of(30, 1, 60, 2, 90, 3, 180, 6);
}
