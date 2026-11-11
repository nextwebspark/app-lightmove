package app.lightmove.api.core.validation;

import app.lightmove.api.common.location.service.Currencies;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;

/** {@link Currencies#isKnownCode}, asked after a trim and an upper-casing; a blank value passes. */
public class SuppliedCurrencyCodeValidator implements ConstraintValidator<SuppliedCurrencyCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return Currencies.isKnownCode(value.trim().toUpperCase(Locale.ROOT));
    }
}
