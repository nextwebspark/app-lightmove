package app.lightmove.api.common.constant;

import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * A notice period, one of five, on both halves of a search: the brief stores {@link #months()} with
 * {@link NoticeUnit#MONTHS}, a profile {@link #value()}. Neither column is narrowed to these, so an
 * unlisted value somebody entered stays offered as recorded. {@link #NONE} is a claim, not a blank.
 */
@RequiredArgsConstructor
public enum NoticePeriod {

    NONE("None", 0),

    ONE_MONTH("1 month", 1),

    TWO_MONTHS("2 months", 2),

    THREE_MONTHS("3 months", 3),

    SIX_MONTHS("6 months", 6);

    private final String label;

    private final int months;

    public String value() {
        return label;
    }

    /** The option a whole number of months names, or null where the screens offer no such option. */
    private static NoticePeriod ofMonths(Integer months) {
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
