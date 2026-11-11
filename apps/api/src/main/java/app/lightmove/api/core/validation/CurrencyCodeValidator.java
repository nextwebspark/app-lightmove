package app.lightmove.api.core.validation;

import app.lightmove.api.common.location.service.Currencies;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link CurrencyCode} against {@link Currencies#isKnownCode}, the one place the currency rules live. */
public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || Currencies.isKnownCode(value);
    }
}
