package app.lightmove.api.project.constant;

/**
 * The two dimensions of a project's company-size scope: headcount and revenue. One value per axis
 * discriminates the shared {@code app_lm_strategy_company_size} collection, exactly as
 * {@link StrategySectorKind} splits the sector list.
 *
 * <p>The two axes' band bounds carry different interval conventions on purpose: {@link EmployeeBand} is
 * inclusive {@code [min, max]} (headcount is discrete, and the labels read as inclusive ranges), while
 * {@link RevenueBand} is half-open {@code [min, max)} (revenue is continuous, so an exclusive upper avoids
 * a boundary dollar belonging to two bands). Whoever writes the count query must apply each axis's
 * convention rather than assuming one.
 */
public enum CompanySizeAxis {
    EMPLOYEE,
    REVENUE
}
