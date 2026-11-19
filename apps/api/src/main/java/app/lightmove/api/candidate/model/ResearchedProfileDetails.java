package app.lightmove.api.candidate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * What research says of a person beyond the candidate-shaped profile, read off a vendor's own record. Staff-only:
 * never on {@code CandidateResponse}. Every field may be absent; {@link #orElse} lays two vendors' readings together.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResearchedProfileDetails(String headline, String industry, String jobFunction, String seniority,
                                       String workStatus, Long followers, Long connections, Boolean influencer,
                                       Integer recommendationsCount, String updatedAt,
                                       List<ResearchedProfileLink> links,
                                       List<ResearchedProfileItem> certifications,
                                       List<ResearchedProfileItem> publications,
                                       List<ResearchedProfileItem> projects,
                                       List<ResearchedProfileItem> volunteering,
                                       List<ResearchedProfileItem> honorsAndAwards,
                                       List<ResearchedProfileItem> organizations,
                                       List<ResearchedProfileItem> courses,
                                       List<ResearchedProfileItem> patents,
                                       List<ResearchedProfileItem> recommendations,
                                       List<ResearchedProfileItem> posts,
                                       List<ResearchedRole> roles,
                                       List<ResearchedLanguage> languages,
                                       List<String> similarProfileSlugs,
                                       List<String> alsoViewedSlugs,
                                       ResearchedContactAvailability contactAvailability,
                                       ResearchedCompanyFacts company) {

    public ResearchedProfileDetails {
        links = copy(links);
        certifications = copy(certifications);
        publications = copy(publications);
        projects = copy(projects);
        volunteering = copy(volunteering);
        honorsAndAwards = copy(honorsAndAwards);
        organizations = copy(organizations);
        courses = copy(courses);
        patents = copy(patents);
        recommendations = copy(recommendations);
        posts = copy(posts);
        roles = copy(roles);
        languages = copy(languages);
        similarProfileSlugs = copy(similarProfileSlugs);
        alsoViewedSlugs = copy(alsoViewedSlugs);
    }

    public static ResearchedProfileDetails empty() {
        return new ResearchedProfileDetails(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Each field of this reading, else {@code fallback}'s — a list is taken whole from whichever has one. */
    public ResearchedProfileDetails orElse(ResearchedProfileDetails fallback) {
        if (fallback == null) {
            return this;
        }
        return new ResearchedProfileDetails(first(headline, fallback.headline), first(industry, fallback.industry),
                first(jobFunction, fallback.jobFunction), first(seniority, fallback.seniority),
                first(workStatus, fallback.workStatus), first(followers, fallback.followers),
                first(connections, fallback.connections), first(influencer, fallback.influencer),
                first(recommendationsCount, fallback.recommendationsCount), first(updatedAt, fallback.updatedAt),
                either(links, fallback.links), either(certifications, fallback.certifications),
                either(publications, fallback.publications), either(projects, fallback.projects),
                either(volunteering, fallback.volunteering), either(honorsAndAwards, fallback.honorsAndAwards),
                either(organizations, fallback.organizations), either(courses, fallback.courses),
                either(patents, fallback.patents), either(recommendations, fallback.recommendations),
                either(posts, fallback.posts), either(roles, fallback.roles), either(languages, fallback.languages),
                either(similarProfileSlugs, fallback.similarProfileSlugs),
                either(alsoViewedSlugs, fallback.alsoViewedSlugs),
                first(contactAvailability, fallback.contactAvailability),
                company == null ? fallback.company : company.orElse(fallback.company));
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static <T> List<T> either(List<T> values, List<T> fallback) {
        return values.isEmpty() ? fallback : values;
    }

    private static <T> T first(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
