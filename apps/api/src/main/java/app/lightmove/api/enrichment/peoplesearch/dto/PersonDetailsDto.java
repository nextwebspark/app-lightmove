package app.lightmove.api.enrichment.peoplesearch.dto;

import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails;
import java.util.List;

/** What ContactOut said about a person beyond the candidate-shaped profile; any part may be absent. */
public record PersonDetailsDto(String headline, String industry, String jobFunction, String seniority,
                               String workStatus, Long followers, String updatedAt, List<Link> links,
                               List<Item> certifications, List<Item> publications, List<Item> projects,
                               List<Item> volunteering, ContactAvailability contactAvailability,
                               Company company) {

    public record Link(String label, String url) {}

    public record Item(String title, String subtitle, String period, String url, String description) {}

    public record ContactAvailability(boolean personalEmail, boolean workEmail, boolean phone) {}

    public record Company(String website, String domain, String industry, String size, String country,
                          String headquarter, Integer foundedYear, String revenue, String overview,
                          List<String> specialties) {}

    public static PersonDetailsDto of(ContactOutProfileDetails details) {
        ContactOutProfileDetails.CompanyFacts company = details.company();
        ContactOutProfileDetails.ContactAvailability flags = details.contactAvailability();
        return new PersonDetailsDto(details.headline(), details.industry(), details.jobFunction(),
                details.seniority(), details.workStatus(), details.followers(), details.updatedAt(),
                details.links().stream().map(link -> new Link(link.label(), link.url())).toList(),
                itemsOf(details.certifications()), itemsOf(details.publications()), itemsOf(details.projects()),
                itemsOf(details.volunteering()),
                flags == null ? null : new ContactAvailability(flags.personalEmail(), flags.workEmail(), flags.phone()),
                company == null ? null : new Company(company.website(), company.domain(), company.industry(),
                        company.size(), company.country(), company.headquarter(), company.foundedYear(),
                        company.revenue(), company.overview(), company.specialties()));
    }

    private static List<Item> itemsOf(List<ContactOutProfileDetails.ProfileItem> items) {
        return items.stream()
                .map(item -> new Item(item.title(), item.subtitle(), item.period(), item.url(), item.description()))
                .toList();
    }
}
