package app.lightmove.api.project.constant;

/**
 * The holding structures a project's ownership scope selects from — a fixed catalog. The wire value is
 * the constant name itself: unlike {@link GeographyMarket} there is no external key to carry, so the
 * name is the stable token and display labels ("Publicly listed", "Family-owned / private") live only
 * in the frontend catalog, free to change without an API break or a data migration.
 *
 * <p>Deliberately carries no mapping onto the company universe's ownership columns. None of
 * {@code org_type}, {@code ownership} or {@code ipo_status} lines up cleanly with this business
 * taxonomy (is "State-linked / sovereign" {@code org_type = 'Government Agency'}, or
 * {@code ownership = 'Government'}, or both?), so encoding a guess here would bake a sourcing decision
 * into a catalog enum. The mapping belongs to the session that builds the sourcing filter.
 *
 * <p>The frontend keeps a mirror of the same values for instant rendering; a drift test on each side
 * keeps the two in step.
 */
public enum OwnershipStructure {

    PUBLICLY_LISTED,
    FAMILY_OWNED_PRIVATE,
    STATE_LINKED_SOVEREIGN,
    PE_VC_BACKED,
    FOREIGN_MULTINATIONAL_SUBSIDIARY;

    /** The wire value — the constant name, which is its own stable token. */
    public String value() {
        return name();
    }

    /** Resolve a wire value (the constant name) to its structure, or {@code null} if unknown. */
    public static OwnershipStructure fromValue(String value) {
        for (OwnershipStructure structure : values()) {
            if (structure.name().equals(value)) {
                return structure;
            }
        }
        return null;
    }
}
