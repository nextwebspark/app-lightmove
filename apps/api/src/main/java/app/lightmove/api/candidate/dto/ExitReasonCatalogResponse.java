package app.lightmove.api.candidate.dto;

import java.util.List;

/** A workspace's reasons for leaving the process, in order, retired ones included and marked. Staff-only. */
public record ExitReasonCatalogResponse(List<ExitReasonResponse> reasons) {}
