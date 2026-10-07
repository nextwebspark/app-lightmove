package app.lightmove.api.billing.usage.service;

import app.lightmove.api.billing.usage.model.MonthlyUsage;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Every workspace's use and estimated cost per kind per UTC month — the margin side of the finance export. */
@Service
@RequiredArgsConstructor
public class UsageReport {

    private final UsageEventStore events;

    public List<MonthlyUsage> monthly(YearMonth first, YearMonth last) {
        return events.monthlyBetween(first, last);
    }
}
