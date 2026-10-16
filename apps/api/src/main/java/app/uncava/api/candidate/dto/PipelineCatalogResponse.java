package app.uncava.api.candidate.dto;

import java.util.List;

/** A workspace's pipeline: its stages in order, each with its statuses, retired ones included and marked. */
public record PipelineCatalogResponse(List<PipelineStageResponse> stages) {}
