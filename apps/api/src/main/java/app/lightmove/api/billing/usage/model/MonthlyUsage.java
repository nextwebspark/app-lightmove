package app.lightmove.api.billing.usage.model;

import app.lightmove.api.billing.usage.constant.UsageKind;
import java.time.YearMonth;
import java.util.UUID;

/** A workspace's use of one kind in one UTC calendar month, and what it is estimated to have cost us. */
public record MonthlyUsage(UUID workspaceId, YearMonth month, UsageKind kind, long units, long estCostFils) {}
