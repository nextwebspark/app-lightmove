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
 * The hiring entity a mandate is run for, and the record the Clients screen edits.
 *
 * <p>Its provenance in the company universe is the {@code (companySource, companySourceId)} pair, or
 * both null when the client was typed in as a custom record. The universe is ETL-owned and unwritable
 * from here, so a "new company" lives on this row; the display columns (name, sector, hqCountry,
 * hqCity, domain, logoUrl) are the write-time snapshot, seeded from the universe on a DB pick and
 * owned by the client thereafter.
 *
 * <p>The pair is <b>deliberately still two loose columns</b> rather than a typed key, and it now holds
 * two vintages. Records created from today on carry {@code ('apollo', apollo_account_id)}; ones
 * created before the universe changed carry the brightdata warehouse's {@code (source, source_id)},
 * which nothing can resolve any more. They are kept rather than migrated because this pair is
 * provenance and nothing else — it is never re-resolved for display, so a stale one costs nothing,
 * while a best-effort re-match on company name would silently repoint a client at a different
 * company.
 */
@Entity
@Table(name = "app_lm_client")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client extends BaseEntity {

    /** The only universe there is. Recorded per row so an older vintage stays recognisable as one. */
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

    /**
     * The city and the company's mark, snapshotted from the universe on a DB pick like the four fields
     * above and left alone by the drawer's edit — they are how a client row renders, not what it says
     * about itself. Null for a custom record, where {@code CompanyLogo}'s initial is the fallback.
     */
    @Column(name = "hq_city")
    private String hqCity;

    @Column(name = "logo_url")
    private String logoUrl;

    @Setter
    @Column(length = 160)
    private String domain;

    /** A free-text protection note the registry keeps — distinct from Strategy's off-limits company list. */
    @Setter
    @Column(name = "off_limits_note")
    private String offLimitsNote;

    /** Which universe the record came from ('apollo' today). Null for a custom record. */
    @Column(name = "company_source")
    private String companySource;

    @Column(name = "company_source_id")
    private String companySourceId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /**
     * A universe-backed client: the display fields are seeded from the resolved snapshot, and the
     * source pair is recorded so the provenance survives an editable rename.
     */
    public static Client fromUniverse(UUID workspaceId, String apolloAccountId, String name,
                                      String sector, String hqCountry, String hqCity, String domain,
                                      String logoUrl, UUID createdBy) {
        Client client = base(workspaceId, name, sector, hqCountry, domain, createdBy);
        client.companySource = UNIVERSE_SOURCE;
        client.companySourceId = apolloAccountId;
        client.hqCity = hqCity;
        client.logoUrl = logoUrl;
        return client;
    }

    /** A custom client typed into the registry: no universe provenance. */
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

    /** Registry edit from the client drawer. The provenance key is deliberately untouched. */
    public void applyDetails(String name, String sector, String hqCountry, String domain,
                             String offLimitsNote) {
        this.name = name.trim();
        this.sector = sector;
        this.hqCountry = Countries.nameOf(hqCountry);
        this.domain = domain;
        this.offLimitsNote = offLimitsNote;
    }
}
