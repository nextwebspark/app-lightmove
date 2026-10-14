package app.lightmove.api.task.dto;

import java.util.List;

/** One tab of My tasks, and every tab's count. */
public record MyTasksResponse(List<TaskResponse> tasks, MyTaskCountsResponse counts) {}
