package app.lightmove.api.enrichment.common.model;

import java.util.List;

/**
 * What a ContactOut profile carries beyond the Bright Data shape the people cache speaks: read from the
 * provider's own record, so nothing a search paid for is lost to the mapping. Every field may be absent.
 */
public record ContactOutProfileDetails(String headline, String industry, String jobFunction, String seniority,
                                       String workStatus, Long followers, String updatedAt,
                                       List<ProfileLink> links, List<ProfileItem> certifications,
                                       List<ProfileItem> publications, List<ProfileItem> projects,
                                       List<ProfileItem> volunteering, ContactAvailability contactAvailability,
                                       CompanyFacts company) {

    public ContactOutProfileDetails {
        links = links == null ? List.of() : List.copyOf(links);
        certifications = certifications == null ? List.of() : List.copyOf(certifications);
        publications = publications == null ? List.of() : List.copyOf(publications);
        projects = projects == null ? List.of() : List.copyOf(projects);
        volunteering = volunteering == null ? List.of() : List.copyOf(volunteering);
    }

    public record ProfileLink(String label, String url) {}

    /** One certification, publication, project or volunteering role, in whichever terms it came. */
    public record ProfileItem(String title, String subtitle, String period, String url, String description) {}

    /** Whether ContactOut holds each kind of contact — flags only; revealing one is a separate, paid call. */
    public record ContactAvailability(boolean personalEmail, boolean workEmail, boolean phone) {}

    public record CompanyFacts(String name, String website, String domain, String industry, String size,
                               String country, String headquarter, Integer foundedYear, String revenue,
                               String overview, String logoUrl, List<String> specialties) {

        public CompanyFacts {
            specialties = specialties == null ? List.of() : List.copyOf(specialties);
        }
    }
}
