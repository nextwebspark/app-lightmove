package app.lightmove.api.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;

/** {@link CurrencyCodeValidator}'s table, read after a trim and an upper-casing; a blank value passes. */
public class SuppliedCurrencyCodeValidator implements ConstraintValidator<SuppliedCurrencyCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return CurrencyCodeValidator.isCurrencyCode(value.trim().toUpperCase(Locale.ROOT));
    }
}
