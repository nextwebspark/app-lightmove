-- The strategy's company-size scope: the employee-headcount and revenue bands a project searches within.
-- The second section to attach to app_lm_strategy (V11 built sector scope); ownership, location and the
-- seed/off-limits lists follow onto the same row in later sessions. No new parent — this hangs off the
-- 1:1 app_lm_strategy created in V11.
--
-- The bands are a *fixed catalog*, unlike sectors: they mirror the distinct employee_range / revenue_range
-- values of the company universe (app_lm_companies), which the enums EmployeeBand / RevenueBand own. Because
-- the catalog is finite and rendered from those enums, only the *selected* bands are stored here — presence
-- is selection, so there is no `selected` column and no persisted "off" rows (sectors keep those only
-- because their suggestions must stay visible once rejected).
--
-- band holds the enum *name* (B_51_200, R_5M_25M …), not the display label or the raw range string. axis
-- splits the one collection into its two dimensions, exactly as `kind` splits the sector list.
--
-- No unique (strategy_id, axis, band) index, for the same reason V11 gives: Hibernate rewrites an element
-- collection in place on shrink/reorder, so a non-deferrable unique index can fire on a transient mid-flush
-- state. Duplicates are rejected in the service instead — and cannot arise anyway, the values being enums.
CREATE TABLE app_lm_strategy_company_size (
    strategy_id uuid        NOT NULL REFERENCES app_lm_strategy (id) ON DELETE CASCADE,
    sort_order  integer     NOT NULL,
    axis        varchar(16) NOT NULL
        CONSTRAINT app_lm_strategy_company_size_axis_chk CHECK (axis IN ('EMPLOYEE', 'REVENUE')),
    band        varchar(32) NOT NULL,
    PRIMARY KEY (strategy_id, sort_order)
);
