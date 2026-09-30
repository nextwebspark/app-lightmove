package app.lightmove.api.enrichment.common.service;

import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataCurrentCompany;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataEducation;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * ContactOut's People Search answer read into Bright Data's record shape, which the people cache, the
 * ranking and the filing all speak. A search keyed on one company files everyone under that company's
 * slug, so a later search there finds them; a people-first search files each person under the employer
 * their own profile names. The employer's current role is put first in the career, where the ranking
 * and the profile read the title held now.
 */
public final class ContactOutPeopleRecords {

    private ContactOutPeopleRecords() {
    }

    /** {@code profiles} is an object keyed by profile URL, but an empty answer sends {@code []}, which no Map binds. */
    public static BrightDataPeopleHits toHits(ContactOutSearchAnswer answer, ObjectMapper json) {
        return toHits(answer, null, json);
    }

    /** {@code searched} null files each person under their own profile's employer. */
    public static BrightDataPeopleHits toHits(ContactOutSearchAnswer answer, ContactOutEmployerKey searched,
                                              ObjectMapper json) {
        if (answer == null || answer.profiles() == null || !answer.profiles().isObject()) {
            return BrightDataPeopleHits.of(List.of(), answer == null || answer.metadata() == null ? 0L
                    : answer.metadata().totalResults());
        }
        List<BrightDataPerson> people = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        answer.profiles().properties().forEach(entry -> {
            BrightDataPerson person = toPerson(entry.getKey(),
                    json.treeToValue(entry.getValue(), ContactOutPerson.class), searched);
            if (person != null) {
                people.add(person);
                sources.add(entry.getValue().toString());
            }
        });
        Long total = answer.metadata() == null ? null : answer.metadata().totalResults();
        return BrightDataPeopleHits.mappedFrom(people, sources, total);
    }

    /** Null for a profile whose URL names no {@code /in/} slug — nothing to key or file it on. */
    static BrightDataPerson toPerson(String profileUrl, ContactOutPerson profile, ContactOutEmployerKey searched) {
        String slug = LinkedInUrls.profileSlugOrNull(profileUrl);
        if (slug == null || profile == null) {
            return null;
        }
        List<ContactOutExperience> experience = profile.experience() == null ? List.of() : profile.experience();
        ContactOutEmployerKey employer = searched != null ? searched : ownEmployerOf(profile, experience);
        String companyName = profile.company() == null || isBlank(profile.company().name()) ? employer.name()
                : profile.company().name();
        String photo = isBlank(profile.profilePictureUrl()) ? null : profile.profilePictureUrl();
        String companyLink = employer.linkedinSlug() == null ? null
                : "https://www.linkedin.com/company/" + employer.linkedinSlug() + "/";
        return new BrightDataPerson(slug, slug, profile.fullName(), "https://www.linkedin.com/in/" + slug + "/",
                profile.summary(), profile.title(), null, profile.location(), Countries.codeOf(profile.country()),
                companyName, new BrightDataCurrentCompany(companyName, employer.linkedinSlug(), companyLink),
                photo, photo == null,
                careerOf(experience, employer.linkedinSlug()), educationOf(profile.education()),
                profile.skills() == null ? List.of() : List.copyOf(profile.skills()),
                profile.languages() == null ? List.of() : List.copyOf(profile.languages()));
    }

    /**
     * The profile's own {@code company}, or — ContactOut leaves it blank for someone holding several
     * current roles — the current role whose title is the profile's title, else the first current one.
     */
    private static ContactOutEmployerKey ownEmployerOf(ContactOutPerson profile,
                                                       List<ContactOutExperience> experience) {
        ContactOutEmployer company = profile.company();
        if (company != null && !isBlank(company.name())) {
            return new ContactOutEmployerKey(LinkedInUrls.companySlugOrNull(company.url()), company.name().strip());
        }
        List<ContactOutExperience> current = experience.stream()
                .filter(role -> role != null && Boolean.TRUE.equals(role.isCurrent()) && !isBlank(role.companyName()))
                .toList();
        return current.stream()
                .filter(role -> profile.title() != null && profile.title().strip().equalsIgnoreCase(
                        role.title() == null ? "" : role.title().strip()))
                .findFirst()
                .or(() -> current.stream().findFirst())
                .map(role -> new ContactOutEmployerKey(LinkedInUrls.companySlugOrNull(role.linkedinUrl()),
                        role.companyName().strip()))
                .orElse(ContactOutEmployerKey.NONE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Current roles at the employer first, then the rest in ContactOut's order. */
    private static List<BrightDataExperience> careerOf(List<ContactOutExperience> experience, String companySlug) {
        List<ContactOutExperience> ordered = new ArrayList<>(experience.stream().filter(Objects::nonNull).toList());
        ordered.sort(Comparator.comparing(role -> !isCurrentAt(role, companySlug)));
        return ordered.stream()
                .map(role -> new BrightDataExperience(role.companyName(), role.title(), null, null, role.logoUrl(),
                        periodOf(role.startDateYear(), role.startDateMonth()),
                        Boolean.TRUE.equals(role.isCurrent()) ? "Present"
                                : periodOf(role.endDateYear(), role.endDateMonth()),
                        role.locality(), null))
                .toList();
    }

    private static boolean isCurrentAt(ContactOutExperience role, String companySlug) {
        return Boolean.TRUE.equals(role.isCurrent()) && companySlug != null
                && companySlug.equals(LinkedInUrls.companySlugOrNull(role.linkedinUrl()));
    }

    private static List<BrightDataEducation> educationOf(List<ContactOutEducation> education) {
        return education == null ? List.of() : education.stream()
                .filter(Objects::nonNull)
                .map(school -> new BrightDataEducation(school.schoolName(), school.degree(), school.fieldOfStudy(),
                        textOf(school.startDateYear()), textOf(school.endDateYear())))
                .toList();
    }

    /** "Mar 2014", or "2014" without a month — the spelling Bright Data's own dates arrive in. */
    private static String periodOf(Integer year, Integer month) {
        if (year == null) {
            return null;
        }
        if (month == null || month < 1 || month > 12) {
            return year.toString();
        }
        return Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;
    }

    /** Education years arrive as strings in one answer and numbers in another. */
    private static String textOf(Object value) {
        return value == null ? null : value.toString();
    }

    /** The company a person is filed under: its LinkedIn slug, which the cache keys on, and its name. */
    public record ContactOutEmployerKey(String linkedinSlug, String name) {

        static final ContactOutEmployerKey NONE = new ContactOutEmployerKey(null, null);
    }

    /** One page of People Search; profiles are keyed by their LinkedIn URL. */
    public record ContactOutSearchAnswer(ContactOutSearchMetadata metadata, JsonNode profiles) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ContactOutSearchMetadata(Integer page, Integer pageSize, Long totalResults) {}

    /** {@code title} is the current role's; {@code experience} the detailed shape {@code detailed_experience} asks for. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ContactOutPerson(String fullName, String title, ContactOutEmployer company, String location,
                            String country, String summary, List<ContactOutExperience> experience,
                            List<ContactOutEducation> education, List<Object> skills, List<Object> languages,
                            String profilePictureUrl) {}

    record ContactOutEmployer(String name, String url, String domain) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ContactOutExperience(String title, String companyName, String locality, Integer startDateYear,
                                Integer startDateMonth, Integer endDateYear, Integer endDateMonth,
                                Boolean isCurrent, String linkedinUrl, String logoUrl) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ContactOutEducation(String schoolName, String degree, String fieldOfStudy, Object startDateYear,
                               Object endDateYear) {}
}
