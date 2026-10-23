package app.lightmove.api.notetaker.model;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.notetaker.constant.CoverageState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** How far a live call has shown one of the brief's competencies, and the words that showed it most. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetencyCoverage(CompetencyPanel panel, String competency, CoverageState state, String quote,
                                 String at) {}
