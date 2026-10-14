package app.lightmove.api.task.dto;

import java.util.List;

/** A page of one tab of My tasks, every tab's count, and whether the tab holds more after this page. */
public record MyTasksResponse(List<TaskResponse> tasks, MyTaskCountsResponse counts, boolean hasMore) {}
