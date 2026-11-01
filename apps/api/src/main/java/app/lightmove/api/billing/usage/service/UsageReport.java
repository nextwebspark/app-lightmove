package app.lightmove.api.billing.usage.service;

import app.lightmove.api.billing.usage.model.MonthlyUsage;
import app.lightmove.api.core.tenant.EveryWorkspace;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Every workspace's use and estimated cost per kind per UTC month — the margin side of the finance export. Read a
 * workspace at a time: the usage events are under row-level security.
 */
@Service
@RequiredArgsConstructor
public class UsageReport {

    private final UsageEventStore events;
    private final EveryWorkspace workspaces;

    public List<MonthlyUsage> monthly(YearMonth first, YearMonth last) {
        List<MonthlyUsage> rows = new ArrayList<>();
        workspaces.forEach(workspaceId -> rows.addAll(events.monthlyBetween(first, last)));
        return rows;
    }
}
