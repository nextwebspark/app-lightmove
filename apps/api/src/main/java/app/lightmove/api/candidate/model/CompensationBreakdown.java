package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.LongTermIncentiveType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;

/**
 * The itemised half of a package, stored as the {@code compensation_breakdown} jsonb column: the
 * allowances line by line and the instruments the long-term incentive is paid in.
 *
 * <p>{@code NONE} is only ever alone — beside a real instrument it contradicts it, and the
 * instrument is the more specific statement.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompensationBreakdown(List<AllowanceLine> allowanceLines,
                                    List<LongTermIncentiveType> longTermIncentiveTypes) {

    public CompensationBreakdown {
        allowanceLines = allowanceLines == null ? List.of()
                : allowanceLines.stream().filter(Objects::nonNull).filter(line -> !line.isEmpty()).toList();
        List<LongTermIncentiveType> named = longTermIncentiveTypes == null ? List.of()
                : longTermIncentiveTypes.stream().filter(Objects::nonNull).distinct().toList();
        longTermIncentiveTypes = named.size() > 1
                ? named.stream().filter(type -> type != LongTermIncentiveType.NONE).toList()
                : named;
    }

    public static CompensationBreakdown empty() {
        return new CompensationBreakdown(List.of(), List.of());
    }

    /** The lines summed, or null when there are none to sum. */
    public Long allowanceTotal() {
        if (allowanceLines.isEmpty()) {
            return null;
        }
        return allowanceLines.stream().mapToLong(line -> line.amount() == null ? 0L : line.amount()).sum();
    }

    public CompensationBreakdown withoutAllowanceLines() {
        return new CompensationBreakdown(List.of(), longTermIncentiveTypes);
    }
}
