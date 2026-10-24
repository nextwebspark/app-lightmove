package app.lightmove.api.notetaker.dto;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.notetaker.constant.CoverageState;

/** One competency's chip: how far the call has shown it, and the words that showed it most. */
public record CopilotCoverageResponse(CompetencyPanel panel, String competency, int weight, CoverageState state,
                                      String quote, String at) {}
