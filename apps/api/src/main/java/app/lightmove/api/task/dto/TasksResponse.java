package app.lightmove.api.task.dto;

import java.util.List;

/**
 * One executive's tasks on one position: every open one, soonest first, and the done ones, latest first — the
 * last three unless every one was asked for. {@code doneCount} is how many are done in all.
 */
public record TasksResponse(List<TaskResponse> open, List<TaskResponse> done, long doneCount) {}
