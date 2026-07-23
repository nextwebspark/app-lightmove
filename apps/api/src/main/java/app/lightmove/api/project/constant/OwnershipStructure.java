package app.lightmove.api.project.constant;

/**
 * The organization types a project's ownership scope selects from — a fixed catalog of the distinct
 * {@code app_lm_companies.org_type} buckets the company universe actually holds (LinkedIn's org-type
 * taxonomy, copied verbatim from the brightdata warehouse). Like {@link GeographyMarket}, this enum is
 * the source of truth: {@link #value} is the {@code org_type} string verbatim, which is both the wire
 * value and the exact join key a later sourcing filter runs against {@code org_type} — no translation
 * layer. Display labels live in the frontend catalog, so UI copy can change without an API break or a
 * data migration.
 *
 * <p>Declared in descending frequency of the universe's distinct values, which is the order the
 * response emits and the chips render. The NULL {@code org_type} bucket is deliberately absent: it is
 * not a selectable type — a company with no type simply matches no ownership filter.
 *
 * <p>The frontend keeps a mirror of the same values for instant rendering; a drift test on each side
 * keeps the two in step.
 */
public enum OwnershipStructure {

    PRIVATELY_HELD("Privately Held"),
    PARTNERSHIP("Partnership"),
    PUBLIC_COMPANY("Public Company"),
    SELF_OWNED("Self-Owned"),
    EDUCATIONAL("Educational"),
    SELF_EMPLOYED("Self-Employed"),
    GOVERNMENT_AGENCY("Government Agency"),
    NONPROFIT("Nonprofit");

    private final String orgType;

    OwnershipStructure(String orgType) {
        this.orgType = orgType;
    }

    /** The {@code org_type} string — the wire value, and {@code app_lm_companies.org_type} verbatim. */
    public String value() {
        return orgType;
    }

    /** Resolve a wire value (the {@code org_type} string) to its structure, or {@code null} if unknown. */
    public static OwnershipStructure fromValue(String value) {
        for (OwnershipStructure structure : values()) {
            if (structure.orgType.equals(value)) {
                return structure;
            }
        }
        return null;
    }
}
