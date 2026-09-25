package app.lightmove.api.candidate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A web page the AI assessment relied on, as the model reported it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssessmentSourceLink(String url, String title) {}
