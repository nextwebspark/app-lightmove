package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.dto.SuppressionResponse;
import java.util.List;

/** An admin's addition to the suppression list: the entries now holding its keys, and how many of them are new. */
public record SuppressionAddition(List<SuppressionResponse> entries, int added) {
}
