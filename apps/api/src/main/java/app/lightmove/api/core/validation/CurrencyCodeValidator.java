package app.lightmove.api.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/** Checks {@link CurrencyCode} against the JDK's ISO 4217 table. */
public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    private static final Set<String> ISO_CODES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isCurrencyCode(String value) {
        return value != null && ISO_CODES.contains(value);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || isCurrencyCode(value);
    }
}
