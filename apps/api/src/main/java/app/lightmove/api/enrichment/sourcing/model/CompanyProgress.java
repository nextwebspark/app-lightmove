package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.enrichment.sourcing.constant.SourcingStage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Where one company's search has got to: its step, the people considered so far, the step's {@code done} of
 * {@code total} (both zero for a step that is not counted), and when it started and finished, in epoch millis
 * as Postgres writes them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompanyProgress(SourcingStage stage, int considered, int done, int total, Long startedAtMillis,
                              Long finishedAtMillis) {}
