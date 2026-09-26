package app.lightmove.api.workspace.model;
import app.lightmove.api.workspace.constant.WorkspaceStatus;

import app.lightmove.api.common.constant.DefaultCurrency;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The tenant. Every piece of business data in LightMove hangs off exactly one of these, and every
 * workspace-scoped query filters on its id.
 */
@Entity
@Table(name = "app_lm_workspace")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends BaseEntity {

    @Setter
    @Column(nullable = false, length = 160)
    private String name;

    /** Appears in URLs (lightmove.app/w/{slug}). Unique, case-insensitive (citext). */
    @Column(nullable = false, unique = true)
    private String slug;

    /** The creator's email domain. Not unique: one firm may run several workspaces. */
    @Column(name = "email_domain", nullable = false, updatable = false)
    private String emailDomain;

    /** One or two characters for the sidebar avatar. */
    @Setter
    @Column(name = "logo_mark", length = 4)
    private String logoMark;

    @Column(name = "company_size", length = 32)
    private String companySize;

    @Column(name = "primary_region", length = 32)
    private String primaryRegion;

    @Column(name = "team_focus", length = 32)
    private String teamFocus;

    /** The universe row this firm was picked as at signup; null for a firm typed in by hand (V68). */
    @Column(name = "apollo_account_id")
    private String apolloAccountId;

    @Column(name = "company_industry")
    private String companyIndustry;

    @Column(name = "company_city")
    private String companyCity;

    @Column(name = "company_country")
    private String companyCountry;

    @Column(name = "company_website")
    private String companyWebsite;

    @Column(name = "company_linkedin_url")
    private String companyLinkedinUrl;

    @Column(name = "logo_url")
    private String logoUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "persona", nullable = false)
    private WorkspacePersona persona = WorkspacePersona.empty();

    @Setter
    @Column(name = "default_region", nullable = false, length = 32)
    private String defaultRegion = "GCC";

    @Setter
    @Column(name = "default_currency", nullable = false, length = 8)
    private String defaultCurrency = DefaultCurrency.CODE;

    @Column(nullable = false, length = 32)
    private String plan = "FREE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceStatus status = WorkspaceStatus.ACTIVE;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    public static Workspace create(String name, String slug, String emailDomain, UUID createdBy,
                                   WorkspaceCompany company,
                                   String companySize, String primaryRegion, String teamFocus) {
        Workspace workspace = new Workspace();
        workspace.name = name;
        workspace.slug = slug;
        workspace.emailDomain = emailDomain.toLowerCase(Locale.ROOT);
        workspace.createdBy = createdBy;
        workspace.companySize = companySize;
        workspace.primaryRegion = primaryRegion;
        workspace.teamFocus = teamFocus;
        workspace.logoMark = deriveLogoMark(name);
        workspace.identifyAs(company);
        workspace.persona = WorkspacePersona.seededFrom(company);

        workspace.defaultRegion = primaryRegion != null ? primaryRegion : "GCC";
        return workspace;
    }

    /** Never the slug (in URLs) or the email domain: a workspace can be re-described, not re-identified. */
    public void describe(String name, WorkspaceCompany company,
                         String companySize, String primaryRegion, String teamFocus) {
        this.name = name;
        this.companySize = companySize;
        this.primaryRegion = primaryRegion;
        this.teamFocus = teamFocus;
        this.logoMark = deriveLogoMark(name);
        this.persona = persona.refiledFrom(getCompany(), company);
        identifyAs(company);
    }

    public void describePersona(WorkspacePersona persona) {
        this.persona = persona == null ? WorkspacePersona.empty() : persona;
    }

    /** Null when the firm was typed in by hand rather than picked from the universe. */
    public WorkspaceCompany getCompany() {
        if (apolloAccountId == null) {
            return null;
        }
        return new WorkspaceCompany(apolloAccountId, companyIndustry, companyCity, companyCountry,
                companyWebsite, companyLinkedinUrl, logoUrl);
    }

    /** A null company clears the whole snapshot: a typed name must not keep another firm's logo. */
    private void identifyAs(WorkspaceCompany company) {
        this.apolloAccountId = company == null ? null : company.apolloAccountId();
        this.companyIndustry = company == null ? null : company.industry();
        this.companyCity = company == null ? null : company.city();
        this.companyCountry = company == null ? null : company.country();
        this.companyWebsite = company == null ? null : company.website();
        this.companyLinkedinUrl = company == null ? null : company.linkedinUrl();
        this.logoUrl = company == null ? null : company.logoUrl();
    }

    /** Settings → General: re-files the company snapshot and the persona's sectors and country; identity stays. */
    public void applySettings(String name, WorkspaceCompany company, String defaultRegion, String defaultCurrency) {
        this.name = name;
        this.logoMark = deriveLogoMark(name);
        this.persona = persona.refiledFrom(getCompany(), company);
        identifyAs(company);
        if (defaultRegion != null) {
            this.defaultRegion = defaultRegion;
        }
        if (defaultCurrency != null) {
            this.defaultCurrency = defaultCurrency;
        }
    }

    /** Soft delete — the row stays for the audit trail; the ACTIVE-filtered indexes stop seeing it. */
    public void delete() {
        if (status == WorkspaceStatus.DELETED) {
            throw new ApiException(ErrorCode.CONFLICT, "Workspace is already deleted");
        }
        this.status = WorkspaceStatus.DELETED;
    }

    /** First letter of the name, upper-cased. */
    private static String deriveLogoMark(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
