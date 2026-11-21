package app.lightmove.api.enrichment.common.service;

import app.lightmove.api.candidate.model.ResearchedCompanyFacts;
import app.lightmove.api.candidate.model.ResearchedContactAvailability;
import app.lightmove.api.candidate.model.ResearchedLanguage;
import app.lightmove.api.candidate.model.ResearchedProfileDetails;
import app.lightmove.api.candidate.model.ResearchedProfileItem;
import app.lightmove.api.candidate.model.ResearchedProfileLink;
import app.lightmove.api.candidate.model.ResearchedRole;
import app.lightmove.api.core.text.service.LinkedInUrls;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads {@link ResearchedProfileDetails} off any research vendor's own record — ContactOut's people record, Bright
 * Data's people dataset and Business Search, HarvestAPI's scrape. Their references name the fields but not every
 * item's shape, so each is read by the first of several plausible keys (snake and camel case alike), a scalar of any
 * type is taken as text, and anything unreadable is simply absent — never a failed filing.
 */
public final class ResearchedProfileDetailsReader {

    private static final Pattern YEAR = Pattern.compile("\\b(1[89]|20)\\d{2}\\b");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private static final String[] ITEM_TITLE = {"title", "name", "role", "position"};
    private static final String[] ITEM_SUBTITLE = {"subtitle", "authority", "issuer", "publisher", "publication",
            "organization", "company_name", "companyName", "cause", "attribution", "interaction"};
    private static final String[] ITEM_URL = {"url", "link", "credential_url", "credentialUrl"};
    private static final String[] ITEM_DESCRIPTION = {"description", "summary", "text"};

    private ResearchedProfileDetailsReader() {
    }

    public static Optional<ResearchedProfileDetails> read(String record, ObjectMapper json) {
        if (record == null || record.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(read(json.readTree(record)));
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    public static ResearchedProfileDetails read(JsonNode profile) {
        return new ResearchedProfileDetails(
                first(profile, "headline"), first(profile, "industry"), first(profile, "job_function", "jobFunction"),
                first(profile, "seniority"), first(profile, "work_status", "workStatus"),
                number(profile, "followers", "followerCount", "follower_count", "followersCount"),
                number(profile, "connections", "connectionsCount", "connection_count", "connectionCount"),
                bool(profile, "influencer"),
                integer(profile, "recommendations_count", "recommendationsCount"),
                first(profile, "updated_at", "updatedAt", "timestamp"),
                linksOf(profile),
                itemsOf(profile, "certifications", "licenses_and_certifications", "certificationsAndLicenses"),
                itemsOf(profile, "publications"),
                itemsOf(profile, "projects"),
                itemsOf(profile, "volunteering_experiences", "volunteer_experience", "volunteering",
                        "volunteerExperience"),
                itemsOf(profile, "honors_and_awards", "honorsAndAwards", "awards", "honors"),
                itemsOf(profile, "organizations"),
                itemsOf(profile, "courses"),
                itemsOf(profile, "patents"),
                itemsOf(profile, "recommendations", "receivedRecommendations"),
                itemsOf(profile, "posts"),
                rolesOf(profile),
                languagesOf(profile),
                slugsOf(profile, new String[] {"similar_profiles", "similarProfiles"},
                        new String[] {"similar_profile_ids"}),
                slugsOf(profile, new String[] {"people_also_viewed", "peopleAlsoViewed"},
                        new String[] {"also_viewed_ids"}),
                availabilityOf(profile.path("contact_availability")),
                companyOf(profile));
    }

    private static List<ResearchedProfileLink> linksOf(JsonNode profile) {
        List<ResearchedProfileLink> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String github = firstOfArrayOrText(profile.path("github"));
        if (github != null) {
            add(links, seen, "GitHub", urlOf(github, "https://github.com/"));
        }
        String twitter = firstOfArrayOrText(profile.path("twitter"));
        if (twitter != null) {
            add(links, seen, "Twitter", urlOf(twitter.replaceFirst("^@", ""), "https://x.com/"));
        }
        for (String key : List.of("bio_links", "bioLinks", "websites")) {
            for (JsonNode link : profile.path(key)) {
                String url = link.isValueNode() ? scalar(link) : first(link, "link", "url");
                if (url != null) {
                    add(links, seen, link.isValueNode() ? "Website" : orDefault(first(link, "title", "label",
                            "category"), "Website"), url);
                }
            }
        }
        return links;
    }

    private static void add(List<ResearchedProfileLink> links, Set<String> seen, String label, String url) {
        if (seen.add(url.toLowerCase())) {
            links.add(new ResearchedProfileLink(label, url));
        }
    }

    /** A handle becomes its profile page; a value already a URL is kept as sent. */
    private static String urlOf(String value, String base) {
        return value.startsWith("http://") || value.startsWith("https://") ? value : base + value;
    }

    private static List<ResearchedProfileItem> itemsOf(JsonNode profile, String... keys) {
        for (String key : keys) {
            JsonNode items = profile.path(key);
            if (items.isArray() && !items.isEmpty()) {
                return itemsOf(items);
            }
        }
        return List.of();
    }

    private static List<ResearchedProfileItem> itemsOf(JsonNode items) {
        List<ResearchedProfileItem> read = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.isValueNode()) {
                String title = scalar(item);
                if (title != null) {
                    read.add(new ResearchedProfileItem(title, null, null, null, null));
                }
                continue;
            }
            String title = first(item, ITEM_TITLE);
            if (title == null) {
                continue;
            }
            read.add(new ResearchedProfileItem(title, first(item, ITEM_SUBTITLE), periodOf(item),
                    first(item, ITEM_URL), plain(first(item, ITEM_DESCRIPTION))));
        }
        return read;
    }

    /** Each career post the person wrote something about — the career's own lines stay on the profile. */
    private static List<ResearchedRole> rolesOf(JsonNode profile) {
        List<ResearchedRole> roles = new ArrayList<>();
        for (JsonNode post : profile.path("experience")) {
            if (!post.isObject()) {
                continue;
            }
            String company = first(post, "company", "company_name", "companyName");
            String location = first(post, "location", "locality");
            JsonNode nested = post.path("positions");
            if (nested.isArray() && !nested.isEmpty()) {
                for (JsonNode held : nested) {
                    addRole(roles, company, held, location);
                }
                continue;
            }
            addRole(roles, company, post, location);
        }
        return roles;
    }

    private static void addRole(List<ResearchedRole> roles, String company, JsonNode post, String location) {
        String description = plain(first(post, "description", "summary", "description_html"));
        if (description == null) {
            return;
        }
        roles.add(new ResearchedRole(company, first(post, "title", "position"), periodOf(post),
                orDefault(first(post, "location", "locality"), location), description));
    }

    private static List<ResearchedLanguage> languagesOf(JsonNode profile) {
        List<ResearchedLanguage> languages = new ArrayList<>();
        for (JsonNode language : profile.path("languages")) {
            String name = language.isValueNode() ? scalar(language) : first(language, "name", "title", "language");
            if (name != null) {
                languages.add(new ResearchedLanguage(name, language.isValueNode() ? null
                        : first(language, "proficiency", "subtitle", "level")));
            }
        }
        return languages;
    }

    /** Profile slugs from a list of people ({@code url}/{@code profile_link}) or from a list of bare slugs. */
    private static List<String> slugsOf(JsonNode profile, String[] peopleKeys, String[] slugKeys) {
        Set<String> slugs = new LinkedHashSet<>();
        for (String key : peopleKeys) {
            for (JsonNode person : profile.path(key)) {
                String url = person.isValueNode() ? scalar(person) : first(person, "url", "profile_link", "link",
                        "linkedinUrl");
                String slug = url == null ? null : LinkedInUrls.profileSlugOrNull(url);
                if (slug != null) {
                    slugs.add(slug);
                }
            }
        }
        for (String key : slugKeys) {
            for (JsonNode slug : profile.path(key)) {
                String value = scalar(slug);
                if (value != null) {
                    slugs.add(value);
                }
            }
        }
        return List.copyOf(slugs);
    }

    private static String periodOf(JsonNode item) {
        String start = yearMonthOf(item, "start_date_year", "start_date_month");
        if (start == null) {
            start = dateOf(item.path("startDate"));
        }
        if (start == null) {
            start = first(item, "start_date", "start_year", "issued_date", "issuedDate", "date", "published_date",
                    "created_at");
        }
        String end = yearMonthOf(item, "end_date_year", "end_date_month");
        if (end == null) {
            end = dateOf(item.path("endDate"));
        }
        if (end == null) {
            end = first(item, "end_date", "end_year", "expiry_date");
        }
        if (start == null) {
            return end;
        }
        return end == null || end.equals(start) ? start : start + " – " + end;
    }

    private static String yearMonthOf(JsonNode item, String yearKey, String monthKey) {
        String year = first(item, yearKey);
        if (year == null) {
            return null;
        }
        String month = first(item, monthKey);
        return month == null ? year : month + "/" + year;
    }

    /** HarvestAPI's dates are {@code {month, year, text}}. */
    private static String dateOf(JsonNode date) {
        if (date.isValueNode()) {
            return scalar(date);
        }
        if (!date.isObject()) {
            return null;
        }
        return orDefault(first(date, "text"), first(date, "year"));
    }

    private static ResearchedContactAvailability availabilityOf(JsonNode flags) {
        if (!flags.isObject()) {
            return null;
        }
        return new ResearchedContactAvailability(flags.path("personal_email").asBoolean(false),
                flags.path("work_email").asBoolean(false), flags.path("phone").asBoolean(false));
    }

    /** ContactOut nests the employer's facts under {@code company}; Business Search spells them flat. */
    private static ResearchedCompanyFacts companyOf(JsonNode profile) {
        JsonNode company = profile.path("company");
        if (company.isObject()) {
            JsonNode funding = company.path("funding");
            return new ResearchedCompanyFacts(first(company, "name"), first(company, "website"),
                    first(company, "domain"), first(company, "email_domain"), first(company, "industry"),
                    first(company, "type"), first(company, "size"), number(company, "employees"),
                    first(company, "country"), first(company, "headquarter"), yearOf(company.path("founded_at")),
                    first(company, "revenue"), first(company, "overview"), first(company, "logo_url"),
                    listOf(company.path("specialties")), listOf(company.path("locations")),
                    first(funding, "funding_status"), integer(funding, "number_of_funding_rounds"),
                    first(funding, "latest_date"));
        }
        String name = first(profile, "current_company_name");
        String industry = first(profile, "current_company_industry");
        String overview = first(profile, "current_company_description");
        if (name == null && industry == null && overview == null) {
            return null;
        }
        return new ResearchedCompanyFacts(name, null, null, null, industry, null,
                first(profile, "current_company_size_from"), null, null,
                first(profile, "current_company_headquarters"), null, null, overview, null,
                listOf(profile.path("current_company_specialties")), List.of(), null, null, null);
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

    private static String firstOfArrayOrText(JsonNode value) {
        if (value.isArray()) {
            for (JsonNode each : value) {
                String text = scalar(each);
                if (text != null) {
                    return text;
                }
            }
            return null;
        }
        return value.isValueNode() ? scalar(value) : null;
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

    /** Text with any markup a vendor sent ({@code description_html}) reduced to its words. */
    private static String plain(String text) {
        if (text == null) {
            return null;
        }
        String stripped = TAG.matcher(text).replaceAll(" ").replaceAll("\\s+", " ").strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }

    /** A year, or a date that starts with one; ContactOut sends {@code 0} for an unknown founding. */
    private static Integer yearOf(JsonNode value) {
        String text = value.isValueNode() ? scalar(value) : null;
        if (text == null) {
            return null;
        }
        Matcher year = YEAR.matcher(text);
        return year.find() ? Integer.valueOf(year.group()) : null;
    }

    private static Boolean bool(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isBoolean() ? value.asBoolean() : null;
    }

    private static Integer integer(JsonNode node, String... keys) {
        Long value = number(node, keys);
        return value == null || value > Integer.MAX_VALUE ? null : value.intValue();
    }

    private static Long number(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isNumber()) {
                return value.asLong();
            }
            String text = value.isValueNode() ? scalar(value) : null;
            if (text == null) {
                continue;
            }
            String digits = text.replaceAll("[^0-9]", "");
            if (!digits.isEmpty() && digits.length() <= 18) {
                return Long.parseLong(digits);
            }
        }
        return null;
    }
}
