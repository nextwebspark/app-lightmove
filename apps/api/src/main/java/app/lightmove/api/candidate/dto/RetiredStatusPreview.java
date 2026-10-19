package app.lightmove.api.candidate.dto;

import java.util.UUID;

/** A status a retire would take out of offer, and how many candidates hold it today. */
public record RetiredStatusPreview(UUID statusId, String label, long holders) {}
