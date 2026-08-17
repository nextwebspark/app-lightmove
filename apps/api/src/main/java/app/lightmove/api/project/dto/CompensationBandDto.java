package app.lightmove.api.project.dto;

/** The band the brief asks for. Either bound may be null; the pair is absent when both are. */
public record CompensationBandDto(Long min, Long max, String currency) {}
