package app.lightmove.api.task.dto;

/** How many open tasks each tab of My tasks holds. */
public record MyTaskCountsResponse(int today, int overdue, int upcoming, int assignedByMe) {}
