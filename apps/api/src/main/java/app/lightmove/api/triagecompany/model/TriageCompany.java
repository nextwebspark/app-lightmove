package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.constant.TriageCompanyOrigin;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A company a mandate has taken a position on — the row "Add to Universe" writes and the triage
 * screen moves between stages.
 *
 * <p>Strategy searches a universe of 71,822 companies that belongs to no project; this table is the
 * handful a mandate has actually decided something about, and the decision is the row. There is no
 * untriaged state: a company nobody has acted on has no row here at all.
 *
 * <p>The snapshot columns are the same contract as {@link StrategyCompanyRef}'s — a triage decision
 * that loses its subject when the pipeline next loads is worse than a stale one — with one addition:
 * {@code note} is the consultant's own remark about this company <i>for this mandate</i>, which is a
 * different thing from Apollo's {@code short_description}, the same sentence for every mandate.
 *
 * <p>{@code status} is stored as its enum name rather than its wire token, matching the CHECK
 * constraint in V32. The wire token is the client's vocabulary and is free to change; the stored name
 * is the schema's.
 *
 * <p>A row carries <b>one of two identities</b>, and which one it holds is what {@link #origin} says.
 * A company the Apollo universe publishes is keyed on {@code apolloAccountId} and its snapshot is
 * resolved server-side, so a client cannot file it under a name of its own choosing. A company the
 * universe has never heard of — the ordinary case when the Chrome extension reads a GCC company's own
 * website — is keyed on {@link #captureKey}, its normalised domain, and its snapshot is what the page
 * said. V33 holds that as a CHECK: one of the two is always present.
 */
@Entity
@Table(name = "app_lm_project_triage_company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TriageCompany extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    /** Null for a captured company the Apollo universe does not publish; then {@link #captureKey} identifies it. */
    @Column(name = "apollo_account_id", updatable = false)
    private String apolloAccountId;

    /** The normalised domain. Null when {@link #apolloAccountId} carries the identity instead. */
    @Column(name = "capture_key", updatable = false)
    private String captureKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false, length = 16)
    private TriageCompanyOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TriageCompanyStatus status;

    @Column(name = "note")
    private String note;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "industry")
    private String industry;

    @Column(name = "company_country")
    private String companyCountry;

    @Column(name = "company_city")
    private String companyCity;

    @Column(name = "num_employees")
    private Integer numEmployees;

    @Column(name = "annual_revenue")
    private Long annualRevenue;

    @Column(name = "website")
    private String website;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "logo_url")
    private String logoUrl;

    /** The page a capture was read from. Null for a company Strategy took out of the universe. */
    @Column(name = "source_url", updatable = false)
    private String sourceUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;


    private TriageCompany(UUID projectId, UUID addedBy, TriageCompanyOrigin origin,
                          TriageCompanyStatus status, TriageCompanySnapshot snapshot, String sourceUrl) {
        this.projectId = projectId;
        this.addedBy = addedBy;
        this.origin = origin;
        this.status = status;
        this.companyName = snapshot.companyName();
        this.industry = snapshot.industry();
        this.companyCountry = snapshot.companyCountry();
        this.companyCity = snapshot.companyCity();
        this.numEmployees = snapshot.numEmployees();
        this.annualRevenue = snapshot.annualRevenue();
        this.website = snapshot.website();
        this.linkedinUrl = snapshot.linkedinUrl();
        this.logoUrl = snapshot.logoUrl();
        this.sourceUrl = sourceUrl;
    }

    /**
     * A company the Apollo universe publishes. The snapshot must have been resolved from the universe
     * rather than taken from the request — that is what keeps a client from filing a known company
     * under a name of its own choosing.
     */
    public static TriageCompany fromUniverse(UUID projectId, UUID addedBy, String apolloAccountId,
                                             TriageCompanyStatus status, TriageCompanySnapshot snapshot,
                                             String sourceUrl) {
        TriageCompany company = new TriageCompany(projectId, addedBy, TriageCompanyOrigin.STRATEGY,
                status, snapshot, sourceUrl);
        company.apolloAccountId = apolloAccountId;
        return company;
    }

    /**
     * A company read off a page because the universe had no match. Identified by {@code captureKey} —
     * its normalised domain — and carrying whatever the page said, which is why the popup makes every
     * field editable before this is written.
     */
    public static TriageCompany fromPage(UUID projectId, UUID addedBy, String captureKey,
                                         TriageCompanyStatus status, TriageCompanySnapshot snapshot,
                                         String sourceUrl) {
        TriageCompany company = new TriageCompany(projectId, addedBy, TriageCompanyOrigin.CAPTURE,
                status, snapshot, sourceUrl);
        company.captureKey = captureKey;
        return company;
    }

    public void moveTo(TriageCompanyStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * Sets the note. <b>Null leaves the existing one alone; an empty string clears it.</b>
     *
     * <p>The asymmetry is the point. Written the other way round — null meaning "clear" — a re-capture
     * from the browser popup, whose note box starts empty on every open, silently destroyed a note
     * somebody had written on the triage screen. Omitting a field must never be read as asking to
     * erase it. {@link #retag} treats null the same way, and the two must agree: they are
     * called on adjacent lines.
     *
     * <p>A blank string still stores null rather than "", so "has a note" stays a null check.
     */
    public void annotate(String newNote) {
        if (newNote == null) {
            return;
        }
        this.note = newNote.isBlank() ? null : newNote.trim();
    }

    /**
     * Replaces the tags, trimmed and de-duplicated case-insensitively. Null leaves them alone, the
     * same rule {@link #annotate} follows. An empty result is stored as null rather than an empty
     * array, so "has tags" stays a null check the way "has a note" is one.
     */
    public void retag(List<String> newTags) {
        if (newTags == null) {
            return;
        }
        Map<String, String> byComparisonKey = new LinkedHashMap<>();
        for (String tag : newTags) {
            if (tag != null && !tag.isBlank()) {
                byComparisonKey.putIfAbsent(tag.trim().toLowerCase(Locale.ROOT), tag.trim());
            }
        }
        this.tags = byComparisonKey.isEmpty() ? null : List.copyOf(byComparisonKey.values());
    }
}
