package app.lightmove.api.core.ratelimit.service;

/**
 * A workload whose daily cost is capped per workspace by {@link WorkspaceDailySpend}.
 *
 * <p>The wire token is what lands in {@code app_lm_workspace_daily_spend.meter} and in the audit
 * trail, so it is stable: renaming one loses a firm's history for that day and reopens its budget.
 */
public enum WorkspaceSpendMeter {

    /** Strategy's AI Research — one grounded search, which may be two billed model calls. */
    COMPANY_DISCOVERY("company-discovery");

    private final String meter;

    WorkspaceSpendMeter(String meter) {
        this.meter = meter;
    }

    public String meter() {
        return meter;
    }
}
