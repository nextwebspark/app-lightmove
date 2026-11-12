package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataCurrentCompany;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A Business Search answer read into the people dataset's record shape, so the cache, the judging and the
 * filing read one shape whichever index answered. The index keeps no dated career: the current title, when
 * it still has one, becomes an open role that began {@code current_tenure_months} ago. Each document is
 * kept whole beside its person, as the provider's own record.
 */
final class BusinessSearchRecords {

    private BusinessSearchRecords() {
    }

    static BrightDataPeopleHits toHits(JsonNode answer, YearMonth today, ObjectMapper json) {
        if (answer == null) {
            return BrightDataPeopleHits.of(List.of(), null);
        }
        List<BrightDataPerson> people = new ArrayList<>();
        List<String> records = new ArrayList<>();
        for (JsonNode document : answer.path("documents")) {
            JsonNode data = document.path("data");
            BrightDataPerson person = toPerson(data, today);
            if (person != null) {
                people.add(person);
                records.add(data.toString());
            }
        }
        JsonNode matched = answer.path("meta").path("matched");
        return BrightDataPeopleHits.mappedFrom(people, records, matched.isNumber() ? matched.asLong() : null);
    }

    /** Null for a record with no profile slug — nothing to key, check or file it on. */
    static BrightDataPerson toPerson(JsonNode data, YearMonth today) {
        String slug = text(data, "linkedin_person_id");
        if (slug == null) {
            return null;
        }
        String title = text(data, "current_title");
        String companyName = text(data, "current_company_name");
        String location = text(data, "location");
        Integer tenureMonths = number(data, "current_tenure_months");
        List<BrightDataExperience> career = title == null ? List.of()
                : List.of(new BrightDataExperience(companyName, title, null, null, null,
                        tenureMonths == null ? null : monthOf(today.minusMonths(tenureMonths)), "Present", location,
                        null));
        String avatar = text(data, "avatar");
        return new BrightDataPerson(text(data, "linkedin_num_id"), slug, text(data, "name"), text(data, "url"),
                text(data, "about"), title, location, text(data, "city"), text(data, "country_code"), companyName,
                new BrightDataCurrentCompany(companyName, text(data, "current_company_id"), null),
                avatar, avatar == null, career, List.of(), null, languagesOf(data));
    }

    /** "Mar 2022", the spelling the dataset's own dates arrive in. */
    private static String monthOf(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear();
    }

    private static List<Object> languagesOf(JsonNode data) {
        List<Object> languages = new ArrayList<>();
        data.path("languages").forEach(language -> {
            if (language.isString() && !language.asString().isBlank()) {
                languages.add(language.asString());
            }
        });
        return languages;
    }

    private static String text(JsonNode data, String field) {
        JsonNode value = data.path(field);
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }

    /** The index sends a number as a number in one record and a string in the next. */
    private static Integer number(JsonNode data, String field) {
        JsonNode value = data.path(field);
        if (value.isNumber()) {
            return value.asInt();
        }
        String text = text(data, field);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
