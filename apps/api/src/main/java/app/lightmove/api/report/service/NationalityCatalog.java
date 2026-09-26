package app.lightmove.api.report.service;

import app.lightmove.api.common.constant.NationalityGroup;
import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.service.Countries;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@link NationalityGroup} a nationality is counted under, folded at read time — "Egyptian" joins
 * Arab expat — without rewriting the stored value. A spelling neither table nor country catalog places
 * keeps its own, title-cased.
 */
final class NationalityCatalog {

    private static final String WESTERN_EXPAT = NationalityGroup.WESTERN_EXPAT.value();
    private static final String SOUTH_ASIAN = NationalityGroup.SOUTH_ASIAN.value();
    private static final String ARAB_EXPAT = NationalityGroup.ARAB_EXPAT_NON_GCC.value();

    private static final Set<String> GCC_GROUPS = Arrays.stream(NationalityGroup.values())
            .filter(NationalityGroup::isGcc)
            .map(NationalityGroup::value)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> EXPAT_GROUPS = Set.of(WESTERN_EXPAT, SOUTH_ASIAN, ARAB_EXPAT);

    private static final Map<String, String> GROUP_BY_COUNTRY_CODE = new HashMap<>();
    private static final Map<String, String> GROUP_BY_SPELLING = new HashMap<>();

    static {
        countries("Saudi", "SA");
        countries("Emirati", "AE");
        countries("Kuwaiti", "KW");
        countries("Qatari", "QA");
        countries("Omani", "OM");
        countries("Bahraini", "BH");
        countries(ARAB_EXPAT, "EG", "LB", "JO", "SY", "IQ", "PS", "MA", "TN", "DZ", "SD", "YE", "LY");
        countries(SOUTH_ASIAN, "IN", "PK", "BD", "LK", "NP");
        countries(WESTERN_EXPAT, "GB", "IE", "US", "CA", "AU", "NZ", "FR", "DE", "NL", "IT", "ES", "CH", "BE",
                "SE", "DK", "NO", "PT", "AT");

        spellings("Saudi", "saudi", "saudi arabian", "saudi national");
        spellings("Emirati", "emirati", "uae national");
        spellings("Kuwaiti", "kuwaiti");
        spellings("Qatari", "qatari");
        spellings("Omani", "omani");
        spellings("Bahraini", "bahraini");
        spellings(ARAB_EXPAT, "arab expat, non-gcc", "arab expat non-gcc", "arab expat", "non-gcc arab",
                "egyptian", "lebanese", "jordanian", "syrian", "iraqi", "palestinian", "moroccan", "tunisian",
                "algerian", "sudanese", "yemeni", "libyan");
        spellings(SOUTH_ASIAN, "south asian", "indian", "pakistani", "bangladeshi", "sri lankan", "nepali",
                "nepalese");
        spellings(WESTERN_EXPAT, "western expat", "western", "british", "english", "scottish", "welsh", "irish",
                "american", "canadian", "australian", "new zealander", "french", "german", "dutch", "italian",
                "spanish", "swiss", "belgian", "swedish", "danish", "norwegian", "portuguese", "austrian");
    }

    private NationalityCatalog() {
    }

    /** The one heading to count under, or null for a blank. */
    static String groupOf(String spelling) {
        if (spelling == null || spelling.isBlank()) {
            return null;
        }
        String normalised = spelling.trim().toLowerCase(Locale.ROOT);
        String known = GROUP_BY_SPELLING.get(normalised);
        if (known != null) {
            return known;
        }
        return Countries.resolve(normalised)
                .map(Country::code)
                .map(GROUP_BY_COUNTRY_CODE::get)
                .orElseGet(() -> titleCase(spelling.trim()));
    }

    static boolean isGcc(String group) {
        return group != null && GCC_GROUPS.contains(group);
    }

    /** One of the nine, as opposed to a spelling the catalog could not place and kept as written. */
    static boolean isGroup(String label) {
        return isGcc(label) || EXPAT_GROUPS.contains(label);
    }

    private static void countries(String group, String... codes) {
        for (String code : codes) {
            GROUP_BY_COUNTRY_CODE.put(code, group);
        }
    }

    private static void spellings(String group, String... known) {
        for (String spelling : known) {
            GROUP_BY_SPELLING.put(spelling, group);
        }
    }

    private static String titleCase(String words) {
        StringBuilder out = new StringBuilder(words.length());
        boolean startOfWord = true;
        for (char letter : words.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(letter) : letter);
            startOfWord = letter == ' ' || letter == '-';
        }
        return out.toString();
    }
}
