package app.lightmove.api.enrichment.common.service;

import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails;
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails.CompanyFacts;
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails.ContactAvailability;
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails.ProfileItem;
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails.ProfileLink;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads {@link ContactOutProfileDetails} off a stored ContactOut profile. The reference names these
 * fields but not every item's shape, so each is read by the first of several plausible keys, a scalar
 * of any type is taken as text, and anything unreadable is simply absent — never a failed page.
 */
public final class ContactOutProfileDetailsReader {

    private static final Pattern YEAR = Pattern.compile("\\b(1[89]|20)\\d{2}\\b");

    private ContactOutProfileDetailsReader() {
    }

    public static Optional<ContactOutProfileDetails> read(String sourceRecord, ObjectMapper json) {
        if (sourceRecord == null || sourceRecord.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(read(json.readTree(sourceRecord)));
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    static ContactOutProfileDetails read(JsonNode profile) {
        return new ContactOutProfileDetails(
                text(profile, "headline"), text(profile, "industry"), text(profile, "job_function"),
                text(profile, "seniority"), text(profile, "work_status"), number(profile.path("followers")),
                text(profile, "updated_at"), linksOf(profile),
                itemsOf(profile.path("certifications"), new String[] {"name", "title"},
                        new String[] {"authority", "issuer", "organization", "company_name"}),
                itemsOf(profile.path("publications"), new String[] {"title", "name"},
                        new String[] {"publisher", "publication", "organization"}),
                itemsOf(profile.path("projects"), new String[] {"title", "name"},
                        new String[] {"company_name", "organization", "role"}),
                itemsOf(profile.path("volunteering_experiences"), new String[] {"role", "title", "name"},
                        new String[] {"company_name", "organization", "cause"}),
                availabilityOf(profile.path("contact_availability")), companyOf(profile.path("company")));
    }

    private static List<ProfileLink> linksOf(JsonNode profile) {
        List<ProfileLink> links = new ArrayList<>();
        String github = text(profile, "github");
        if (github != null) {
            links.add(new ProfileLink("GitHub", urlOf(github, "https://github.com/")));
        }
        String twitter = text(profile, "twitter");
        if (twitter != null) {
            links.add(new ProfileLink("Twitter", urlOf(twitter.replaceFirst("^@", ""), "https://x.com/")));
        }
        return links;
    }

    /** A handle becomes its profile page; a value already a URL is kept as sent. */
    private static String urlOf(String value, String base) {
        return value.startsWith("http://") || value.startsWith("https://") ? value : base + value;
    }

    private static List<ProfileItem> itemsOf(JsonNode items, String[] titleKeys, String[] subtitleKeys) {
        if (!items.isArray()) {
            return List.of();
        }
        List<ProfileItem> read = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.isValueNode()) {
                String title = scalar(item);
                if (title != null) {
                    read.add(new ProfileItem(title, null, null, null, null));
                }
                continue;
            }
            String title = first(item, titleKeys);
            if (title == null) {
                continue;
            }
            read.add(new ProfileItem(title, first(item, subtitleKeys), periodOf(item),
                    first(item, "url", "link"), first(item, "description", "summary")));
        }
        return read;
    }

    private static String periodOf(JsonNode item) {
        String start = first(item, "start_date_year", "start_date", "start_year", "issued_date", "date",
                "published_date");
        String end = first(item, "end_date_year", "end_date", "end_year", "expiry_date");
        if (start == null) {
            return end;
        }
        return end == null || end.equals(start) ? start : start + " – " + end;
    }

    private static ContactAvailability availabilityOf(JsonNode flags) {
        if (!flags.isObject()) {
            return null;
        }
        return new ContactAvailability(flags.path("personal_email").asBoolean(false),
                flags.path("work_email").asBoolean(false), flags.path("phone").asBoolean(false));
    }

    private static CompanyFacts companyOf(JsonNode company) {
        if (!company.isObject()) {
            return null;
        }
        Integer founded = yearOf(company.path("founded_at"));
        return new CompanyFacts(text(company, "name"), text(company, "website"), text(company, "domain"),
                text(company, "industry"), text(company, "size"), text(company, "country"),
                text(company, "headquarter"), founded,
                text(company, "revenue"), text(company, "overview"), text(company, "logo_url"),
                listOf(company.path("specialties")));
    }

    /** An array of words, or one comma-separated line of them — both have been seen for specialties. */
    private static List<String> listOf(JsonNode values) {
        if (values.isArray()) {
            List<String> read = new ArrayList<>();
            for (JsonNode value : values) {
                String text = value.isValueNode() ? scalar(value) : null;
                if (text != null) {
                    read.add(text);
                }
            }
            return read;
        }
        String line = values.isValueNode() ? scalar(values) : null;
        return line == null ? List.of() : Arrays.stream(line.split(","))
                .map(String::strip).filter(word -> !word.isEmpty()).collect(Collectors.toList());
    }

    private static String first(JsonNode node, String... keys) {
        return Stream.of(keys).map(key -> text(node, key)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isValueNode() ? scalar(value) : null;
    }

    private static String scalar(JsonNode value) {
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** A year, or a date that starts with one. */
    private static Integer yearOf(JsonNode value) {
        String text = value.isValueNode() ? scalar(value) : null;
        if (text == null) {
            return null;
        }
        Matcher year = YEAR.matcher(text);
        return year.find() ? Integer.valueOf(year.group()) : null;
    }

    private static Long number(JsonNode value) {
        if (value.isNumber()) {
            return value.asLong();
        }
        String text = value.isValueNode() ? scalar(value) : null;
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        return digits.isEmpty() || digits.length() > 18 ? null : Long.parseLong(digits);
    }
}
