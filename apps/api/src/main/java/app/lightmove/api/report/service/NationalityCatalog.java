package app.lightmove.api.report.service;

import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.service.Countries;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The group a nationality is counted under. The drawer records one of nine — the Gulf six by name and
 * everyone else as Western expat, South Asian or Arab expat, non-GCC — but a spreadsheet states
 * whatever it states, so "Egyptian", "Egypt" and "Arab expat" are folded here, at read time, and the
 * stored value is never rewritten.
 *
 * <p>Two routes to a group: a spelling the table knows, or a country the catalog resolves. A spelling
 * neither places keeps its own, title-cased, rather than being pushed into a group it may not belong
 * to. The Gulf six are named because localisation rules turn on exactly that line.
 */
final class NationalityCatalog {

    private static final String WESTERN_EXPAT = "Western expat";
    private static final String SOUTH_ASIAN = "South Asian";
    private static final String ARAB_EXPAT = "Arab expat, non-GCC";

    private static final Set<String> GCC_GROUPS =
            Set.of("Saudi", "Emirati", "Kuwaiti", "Qatari", "Omani", "Bahraini");

    private static final Map<String, String> GROUP_BY_COUNTRY_CODE = new HashMap<>();
    private static final Map<String, String> GROUP_BY_SPELLING = new HashMap<>();

    static {
        countries("Saudi", "SA");
        countries("Emirati", "AE");
        countries("Kuwaiti", "KW");
        countries("Qatari", "QA");
        countries("Omani", "OM");
        countries("Bahraini", "BH");
        countries("Turkish", "TR");
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
        spellings("Turkish", "turkish");
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
