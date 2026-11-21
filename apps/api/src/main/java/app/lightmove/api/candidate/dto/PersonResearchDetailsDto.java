package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.ResearchedCompanyFacts;
import app.lightmove.api.candidate.model.ResearchedProfileDetails;
import app.lightmove.api.candidate.model.ResearchedProfileItem;
import java.util.List;

/**
 * What research said about a person beyond the candidate-shaped profile; any part may be absent. Staff-only: the
 * Strategy preview and the person record carry it, never {@code CandidateResponse}.
 */
public record PersonResearchDetailsDto(String headline, String industry, String jobFunction, String seniority,
                                       String workStatus, Long followers, Long connections, Boolean influencer,
                                       Integer recommendationsCount, String updatedAt, List<Link> links,
                                       List<Item> certifications, List<Item> publications, List<Item> projects,
                                       List<Item> volunteering, List<Item> honorsAndAwards,
                                       List<Item> organizations, List<Item> courses, List<Item> patents,
                                       List<Item> recommendations, List<Item> posts, List<Role> roles,
                                       List<Language> languages, List<String> similarProfileSlugs,
                                       List<String> alsoViewedSlugs, ContactAvailability contactAvailability,
                                       Company company) {

    public record Link(String label, String url) {}

    public record Item(String title, String subtitle, String period, String url, String description) {}

    public record Role(String company, String title, String period, String location, String description) {}

    public record Language(String name, String proficiency) {}

    public record ContactAvailability(boolean personalEmail, boolean workEmail, boolean phone) {}

    public record Company(String website, String domain, String industry, String type, String size, Long employees,
                          String country, String headquarter, Integer foundedYear, String revenue, String overview,
                          List<String> specialties, List<String> locations, String fundingStatus,
                          Integer fundingRounds, String latestFundingDate) {}

    public static PersonResearchDetailsDto of(ResearchedProfileDetails details) {
        if (details == null) {
            return null;
        }
        ResearchedCompanyFacts company = details.company();
        return new PersonResearchDetailsDto(details.headline(), details.industry(), details.jobFunction(),
                details.seniority(), details.workStatus(), details.followers(), details.connections(),
                details.influencer(), details.recommendationsCount(), details.updatedAt(),
                details.links().stream().map(link -> new Link(link.label(), link.url())).toList(),
                itemsOf(details.certifications()), itemsOf(details.publications()), itemsOf(details.projects()),
                itemsOf(details.volunteering()), itemsOf(details.honorsAndAwards()), itemsOf(details.organizations()),
                itemsOf(details.courses()), itemsOf(details.patents()), itemsOf(details.recommendations()),
                itemsOf(details.posts()),
                details.roles().stream().map(role -> new Role(role.company(), role.title(), role.period(),
                        role.location(), role.description())).toList(),
                details.languages().stream().map(language -> new Language(language.name(), language.proficiency()))
                        .toList(),
                details.similarProfileSlugs(), details.alsoViewedSlugs(),
                details.contactAvailability() == null ? null : new ContactAvailability(
                        details.contactAvailability().personalEmail(), details.contactAvailability().workEmail(),
                        details.contactAvailability().phone()),
                company == null ? null : new Company(company.website(), company.domain(), company.industry(),
                        company.type(), company.size(), company.employees(), company.country(),
                        company.headquarter(), company.foundedYear(), company.revenue(), company.overview(),
                        company.specialties(), company.locations(), company.fundingStatus(), company.fundingRounds(),
                        company.latestFundingDate()));
    }

    private static List<Item> itemsOf(List<ResearchedProfileItem> items) {
        return items.stream()
                .map(item -> new Item(item.title(), item.subtitle(), item.period(), item.url(), item.description()))
                .toList();
    }
}
