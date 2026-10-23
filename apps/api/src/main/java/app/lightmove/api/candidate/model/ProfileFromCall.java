package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CallField;
import java.util.Set;

/**
 * The person's own fields a call changes: only those named in {@code fields} are written, so a package whose bonus
 * was left unticked keeps the bonus on file.
 */
public record ProfileFromCall(Set<CallField> fields, String noticePeriod, String currency, Long baseSalary,
                              Long bonus, Long allowances, String city, String country) {

    public static final Set<CallField> PACKAGE = Set.of(CallField.BASE_SALARY, CallField.BONUS, CallField.ALLOWANCES);

    public ProfileFromCall {
        fields = Set.copyOf(fields);
    }

    public boolean touchesPackage() {
        return fields.stream().anyMatch(PACKAGE::contains);
    }
}
