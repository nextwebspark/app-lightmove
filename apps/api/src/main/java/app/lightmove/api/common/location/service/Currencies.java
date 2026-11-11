package app.lightmove.api.common.location.service;

import app.lightmove.api.common.constant.DefaultCurrency;
import app.lightmove.api.common.location.model.OfferedCurrency;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The currencies a country uses today, read off the JDK's ISO 3166 → ISO 4217 table, and the one a country's
 * firm starts in. Historic codes are never offered but are still known, so a stored or imported one keeps working.
 */
public final class Currencies {

    private static final List<OfferedCurrency> OFFERED = Arrays.stream(Locale.getISOCountries())
            .map(Currencies::currencyOfCode)
            .flatMap(Optional::stream)
            .distinct()
            .map(currency -> new OfferedCurrency(currency.getCurrencyCode(), currency.getDisplayName(Locale.ENGLISH)))
            .sorted(Comparator.comparing(OfferedCurrency::code))
            .toList();

    // Every ISO 4217 code but metals, fund units, XXX and XTS, the codes ISO gives no minor unit.
    private static final Set<String> KNOWN_CODES = Currency.getAvailableCurrencies().stream()
            .filter(Currencies::isMoney)
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    private Currencies() {
    }

    public static List<OfferedCurrency> offered() {
        return OFFERED;
    }

    /** Whether ISO issues this exact code, a retired one included, so a stored or imported figure keeps it. */
    public static boolean isKnownCode(String code) {
        return code != null && KNOWN_CODES.contains(code);
    }

    /** The currency a firm in this country starts in; any spelling {@link Countries} resolves, else AED. */
    public static String startingCurrencyFor(String countrySpelling) {
        return Optional.ofNullable(Countries.codeOf(countrySpelling))
                .flatMap(Currencies::currencyOfCode)
                .map(Currency::getCurrencyCode)
                .orElse(DefaultCurrency.CODE);
    }

    // Antarctica has no currency: the JDK answers null rather than throwing.
    private static Optional<Currency> currencyOfCode(String alpha2) {
        try {
            return Optional.ofNullable(Currency.getInstance(Locale.of("", alpha2))).filter(Currencies::isMoney);
        } catch (IllegalArgumentException unknownCountry) {
            return Optional.empty();
        }
    }

    private static boolean isMoney(Currency currency) {
        return currency.getDefaultFractionDigits() >= 0;
    }
}
