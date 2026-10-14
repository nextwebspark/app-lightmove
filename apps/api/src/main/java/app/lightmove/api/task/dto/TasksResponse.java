package app.lightmove.api.task.dto;

import java.util.List;

/** One executive's tasks on one position: every open one, soonest first, and the latest five done. */
public record TasksResponse(List<TaskResponse> open, List<TaskResponse> done) {}
