package app.lightmove.api.workspace.model;
import app.lightmove.api.workspace.constant.WorkspaceStatus;

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

    /**
     * The domain of the address that created this workspace, e.g. {@code nextwebspark.com}.
     *
     * <p>Not unique: one firm may run several workspaces. This is how colleagues <i>find</i> each other
     * — at signup we show which workspaces already exist on your domain — not a claim on the domain.
     */
    @Column(name = "email_domain", nullable = false, updatable = false)
    private String emailDomain;

    /** One or two characters for the sidebar avatar, e.g. "L". */
    @Setter
    @Column(name = "logo_mark", length = 4)
    private String logoMark;

    @Column(name = "company_size", length = 32)
    private String companySize;

    @Column(name = "primary_region", length = 32)
    private String primaryRegion;

    @Column(name = "team_focus", length = 32)
    private String teamFocus;

    @Setter
    @Column(name = "default_region", nullable = false, length = 32)
    private String defaultRegion = "GCC";

    @Setter
    @Column(name = "default_currency", nullable = false, length = 8)
    private String defaultCurrency = "USD";

    @Column(nullable = false, length = 32)
    private String plan = "FREE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceStatus status = WorkspaceStatus.ACTIVE;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    public static Workspace create(String name, String slug, String emailDomain, UUID createdBy,
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
        // The region they told us they work in is the only sensible default for the region their
        // projects will be in.
        workspace.defaultRegion = primaryRegion != null ? primaryRegion : "GCC";
        return workspace;
    }

    /**
     * Corrects the details the organisation was described with.
     *
     * <p>Notably <b>not</b> the slug, and not the email domain. The slug is in URLs and in anything
     * anyone has bookmarked, so renaming "Acme Search" to "Acme Executive" must not silently break
     * every link to it; the domain was never the user's to choose in the first place. A workspace can
     * be re-described. It cannot be re-identified.
     */
    public void describe(String name, String companySize, String primaryRegion, String teamFocus) {
        this.name = name;
        this.companySize = companySize;
        this.primaryRegion = primaryRegion;
        this.teamFocus = teamFocus;
        this.logoMark = deriveLogoMark(name);
    }

    /**
     * Whether an address belongs to this organisation.
     *
     * <p>The single place that question is answered, so that signup, invitation and any future
     * membership path cannot each arrive at a slightly different answer.
     */
    public boolean owns(String email) {
        if (email == null || !email.contains("@")) {
            return false;
        }
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return emailDomain.equalsIgnoreCase(domain);
    }

    /** First letter of the name, upper-cased — matches the "L" tile in the mockups. */
    private static String deriveLogoMark(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
