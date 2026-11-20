package app.lightmove.api.dashboard.constant;

/** Where a dashboard lives and which half of the query library it may draw from. */
public enum DashboardScope {
    /** One position's dashboard, beside its report. */
    PROJECT,
    /** The workspace's, across every position the member may browse. */
    WORKSPACE
}
