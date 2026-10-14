package app.lightmove.api.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

/** Checks {@link PlausibleDate}. */
public class PlausibleDateValidator implements ConstraintValidator<PlausibleDate, LocalDate> {

    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2100, 12, 31);

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        return value == null || (!value.isBefore(EARLIEST) && !value.isAfter(LATEST));
    }
}
