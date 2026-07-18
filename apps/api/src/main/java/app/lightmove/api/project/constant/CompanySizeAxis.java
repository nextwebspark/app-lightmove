package app.lightmove.api.project.constant;

/**
 * The two dimensions of a project's company-size scope: headcount and revenue. One value per axis
 * discriminates the shared {@code app_lm_strategy_company_size} collection, exactly as
 * {@link StrategySectorKind} splits the sector list.
 */
public enum CompanySizeAxis {
    EMPLOYEE,
    REVENUE
}
