package app.lightmove.api.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks {@link CurrencyCode} against the JDK's ISO 4217 table, less what is no currency at all — metals, fund
 * units, {@code XXX} and {@code XTS}, the codes ISO gives no minor unit.
 */
public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    private static final Set<String> ISO_CODES = Currency.getAvailableCurrencies().stream()
            .filter(currency -> currency.getDefaultFractionDigits() >= 0)
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ISO_CODES.contains(value);
    }
}
