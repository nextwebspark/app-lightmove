package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.company.model.CompanyKey;
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
 * <p>Its identity in the shared company universe is the {@code (companySource, companySourceId)} pair —
 * the same rebuild-stable key Strategy stores for a company list — or both null when the client was
 * typed in as a custom record. The universe (app_lm_companies) is ETL-owned and unwritable from here,
 * so a "new company" lives on this row; the display columns (name, sector, hqCountry, domain) are the
 * editable snapshot, seeded from the universe on a DB pick and owned by the client thereafter.
 */
@Entity
@Table(name = "app_lm_client")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client extends BaseEntity {

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

    @Setter
    @Column(length = 160)
    private String domain;

    /** A free-text protection note the registry keeps — distinct from Strategy's off-limits company list. */
    @Setter
    @Column(name = "off_limits_note")
    private String offLimitsNote;

    /** The universe key's source half; null for a custom record. Provenance only — never re-resolved for display. */
    @Column(name = "company_source")
    private String companySource;

    @Column(name = "company_source_id")
    private String companySourceId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /**
     * A universe-backed client: the display fields are seeded from the resolved snapshot, and the
     * {@code (source, source_id)} key is recorded so the provenance survives an editable rename.
     */
    public static Client fromUniverse(UUID workspaceId, CompanyKey key, String name, String sector,
                                      String hqCountry, String domain, UUID createdBy) {
        Client client = base(workspaceId, name, sector, hqCountry, domain, createdBy);
        client.companySource = key.source();
        client.companySourceId = key.sourceId();
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
        client.hqCountry = hqCountry;
        client.domain = domain;
        client.createdBy = createdBy;
        return client;
    }

    /** Registry edit from the client drawer. The provenance key is deliberately untouched. */
    public void applyDetails(String name, String sector, String hqCountry, String domain,
                             String offLimitsNote) {
        this.name = name.trim();
        this.sector = sector;
        this.hqCountry = hqCountry;
        this.domain = domain;
        this.offLimitsNote = offLimitsNote;
    }
}
