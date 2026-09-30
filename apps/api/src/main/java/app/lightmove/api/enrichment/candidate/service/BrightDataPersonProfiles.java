package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.common.location.model.LocationLine;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataEducation;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Bright Data people record read as the profile a candidate is filed from — shared by the capture
 * lookup and a Find executives hit, which are the same record. Masked values map to null.
 */
public final class BrightDataPersonProfiles {

    private BrightDataPersonProfiles() {
    }

    public static EnrichedProfile toEnrichedProfile(BrightDataPerson person) {
        return toEnrichedProfile(person, EnrichmentVendor.BRIGHTDATA);
    }

    /** The same reading of a record another vendor's answer was mapped into, credited to that vendor. */
    public static EnrichedProfile toEnrichedProfile(BrightDataPerson person, EnrichmentVendor vendor) {
        return new EnrichedProfile(
                currentTitleOf(person),
                unmasked(person.about()),
                employerNameOf(person),
                employerLinkedinUrlOf(person),
                employerLogoUrlOf(person),
                cityOf(person),
                countryOf(person),
                careerOf(person.experience()),
                educationOf(person.education()),
                namesOf(person.skills()),
                namesOf(person.languages()),
                null,
                vendor);
    }

    /** A flat entry's {@code title} is the position; grouped entries nest them; a masked record may have only {@code position}. */
    private static String currentTitleOf(BrightDataPerson person) {
        for (BrightDataExperience post : listOf(person.experience())) {
            if (post.positions() != null && !post.positions().isEmpty()) {
                String nested = unmasked(post.positions().getFirst().title());
                if (nested != null) {
                    return nested;
                }
                continue;
            }
            String title = unmasked(post.title());
            if (title != null && !title.equals(unmasked(post.company()))) {
                return title;
            }
        }
        return unmasked(person.position());
    }

    private static String employerNameOf(BrightDataPerson person) {
        String name = unmasked(person.currentCompanyName());
        if (name != null) {
            return name;
        }
        return person.currentCompany() == null ? null : unmasked(person.currentCompany().name());
    }

    private static String employerLinkedinUrlOf(BrightDataPerson person) {
        if (person.currentCompany() == null) {
            return null;
        }
        String companyId = person.currentCompany().companyId();
        if (companyId != null && !companyId.isBlank()) {
            return "https://www.linkedin.com/company/" + companyId + "/";
        }
        String link = person.currentCompany().link();
        return link == null ? null : link.split("\\?")[0];
    }

    /** The entry naming the current employer, else the most recent — never any logo, which could be a past employer's. */
    private static String employerLogoUrlOf(BrightDataPerson person) {
        String employer = employerNameOf(person);
        List<BrightDataExperience> experience = listOf(person.experience());
        if (employer != null) {
            for (BrightDataExperience post : experience) {
                if (employer.equalsIgnoreCase(unmasked(post.company())) && post.companyLogoUrl() != null) {
                    return post.companyLogoUrl();
                }
            }
        }
        return experience.isEmpty() ? null : experience.getFirst().companyLogoUrl();
    }

    /** {@code location} is the short city ("Dubai"); {@code city} is the full line with the country. */
    private static String cityOf(BrightDataPerson person) {
        String city = unmasked(person.location());
        if (city != null) {
            return Countries.cityOf(city);
        }
        return LocationLine.of(unmasked(person.city())).city();
    }

    /** Through the catalog: the raw code once put "AE" beside "United Arab Emirates" in one column. */
    private static String countryOf(BrightDataPerson person) {
        return LocationLine.of(unmasked(person.city())).countryOr(unmasked(person.countryCode()));
    }

    private static List<CandidateCareerEntry> careerOf(List<BrightDataExperience> experience) {
        List<CandidateCareerEntry> career = new ArrayList<>();
        for (BrightDataExperience post : listOf(experience)) {
            if (post.positions() != null && !post.positions().isEmpty()) {
                for (BrightDataPosition held : post.positions()) {
                    career.add(new CandidateCareerEntry(unmasked(post.company()),
                            unmasked(held.title()),
                            periodOf(held.startDate(), held.endDate(), null),
                            unmasked(post.location())));
                }
                continue;
            }
            String company = unmasked(post.company());
            String title = unmasked(post.title());
            // A flat entry whose title repeats the company is a grouping header; the position is the subtitle.
            String position = title != null && title.equals(company) ? unmasked(post.subtitle()) : title;
            // Skeleton rows — a bare year range, seen on live records — are not a career line.
            if (company == null && position == null) {
                continue;
            }
            career.add(new CandidateCareerEntry(company, position,
                    periodOf(post.startDate(), post.endDate(), post.duration()), unmasked(post.location())));
        }
        return career;
    }

    private static List<CandidateEducationEntry> educationOf(List<BrightDataEducation> education) {
        return listOf(education).stream()
                .map(school -> new CandidateEducationEntry(
                        unmasked(school.title()),
                        degreeOf(unmasked(school.degree()), unmasked(school.field())),
                        periodOf(school.startYear(), school.endYear(), null)))
                .toList();
    }

    private static String degreeOf(String degree, String field) {
        if (degree == null) {
            return field;
        }
        return field == null ? degree : degree + ", " + field;
    }

    private static String periodOf(String start, String end, String duration) {
        String started = unmasked(start);
        if (started != null) {
            String ended = unmasked(end);
            return started + " – " + (ended == null ? "Present" : ended);
        }
        return unmasked(duration);
    }

    /** Skills and languages arrive as strings, {name}/{title} objects or null, so they are read defensively. */
    private static List<String> namesOf(List<Object> items) {
        return listOf(items).stream()
                .map(BrightDataPersonProfiles::nameOf)
                .map(BrightDataPersonProfiles::unmasked)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String nameOf(Object item) {
        if (item instanceof String text) {
            return text;
        }
        if (item instanceof Map<?, ?> shaped) {
            Object name = shaped.get("name") != null ? shaped.get("name") : shaped.get("title");
            return name == null ? null : name.toString();
        }
        return null;
    }

    /** A masked value ("******* ***") has no letter or digit; it maps to null, never star-soup. */
    private static String unmasked(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        boolean readable = value.chars().anyMatch(Character::isLetterOrDigit);
        return readable ? value.trim() : null;
    }

    private static <T> List<T> listOf(List<T> value) {
        return value == null ? List.of() : value;
    }
}
