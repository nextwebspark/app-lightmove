package app.lightmove.api.candidate.dto;

import java.util.List;

/**
 * What retiring a stage or a status would touch: each live status it retires with how many candidates hold
 * it (they keep it), and every automation that would stop for want of its status.
 */
public record PipelineRetirePreviewResponse(List<RetiredStatusPreview> statuses,
                                            List<StatusAutomationResponse> automations) {}
