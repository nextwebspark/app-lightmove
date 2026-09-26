package app.lightmove.api.project.model;

import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The hiring entity a mandate is run for, with a write-time company snapshot. The
 * {@code (companySource, companySourceId)} pair is provenance, never re-resolved: older rows hold
 * brightdata ids nothing can resolve, and re-matching by name would repoint a client silently.
 */
@Entity
@Table(name = "app_lm_client")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client extends BaseEntity {

    /** Recorded per row so an older vintage stays recognisable as one. */
    public static final String UNIVERSE_SOURCE = "apollo";

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Setter
    @Column(nullable = false, length = 160)
    private String name;

    @Setter
    @Column(length = 96)
    private String sector;

    @Setter
    @Column(name = "hq_country", length = 64)
    private String hqCountry;

    /** Snapshotted on a DB pick and left alone by the drawer's edit; null for a custom record. */
    @Column(name = "hq_city")
    private String hqCity;

    @Column(name = "logo_url")
    private String logoUrl;

    @Setter
    @Column(length = 160)
    private String domain;

    /** Free text — distinct from Strategy's off-limits company list. */
    @Setter
    @Column(name = "off_limits_note")
    private String offLimitsNote;

    @Column(name = "notes")
    private String notes;

    /** Null for a custom record. */
    @Column(name = "company_source")
    private String companySource;

    @Column(name = "company_source_id")
    private String companySourceId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /** The source pair is recorded so provenance survives an editable rename. */
    public static Client fromUniverse(UUID workspaceId, String apolloAccountId, String name,
                                      String sector, String hqCountry, String hqCity, String domain,
                                      String logoUrl, UUID createdBy) {
        Client client = base(workspaceId, name, sector, hqCountry, domain, createdBy);
        client.companySource = UNIVERSE_SOURCE;
        client.companySourceId = apolloAccountId;
        client.hqCity = Countries.cityOf(hqCity);
        client.logoUrl = logoUrl;
        return client;
    }

    public static Client custom(UUID workspaceId, String name, String sector, String hqCountry,
                                String domain, UUID createdBy) {
        return base(workspaceId, name, sector, hqCountry, domain, createdBy);
    }

    private static Client base(UUID workspaceId, String name, String sector, String hqCountry,
                               String domain, UUID createdBy) {
        Client client = new Client();
        client.workspaceId = workspaceId;
        client.name = name.trim();
        client.sector = sector;
        client.hqCountry = Countries.nameOf(hqCountry);
        client.domain = domain;
        client.createdBy = createdBy;
        return client;
    }

    /** A null field is left as it is and a blank one cleared; provenance is untouched. */
    public void applyDetails(String name, String sector, String hqCountry, String domain,
                             String offLimitsNote, String notes) {
        this.name = name.trim();
        this.sector = patched(sector, this.sector);
        this.hqCountry = hqCountry == null ? this.hqCountry : Countries.nameOf(hqCountry);
        this.domain = patched(domain, this.domain);
        this.offLimitsNote = patched(offLimitsNote, this.offLimitsNote);
        this.notes = patched(notes, this.notes);
    }

    private static String patched(String incoming, String current) {
        if (incoming == null) return current;
        return incoming.isBlank() ? null : incoming.trim();
    }
}
