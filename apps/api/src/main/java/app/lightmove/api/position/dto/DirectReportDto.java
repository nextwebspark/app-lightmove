package app.lightmove.api.position.dto;

import jakarta.validation.constraints.Size;

/**
 * One seat reporting into this one — the same shape reads and writes. Both fields are optional: a
 * mandate usually knows the seat long before it knows who fills it, and a card with only a title is
 * how the org chart says so.
 */
public record DirectReportDto(
        @Size(max = 160, message = "That title is too long") String title,
        @Size(max = 160, message = "That name is too long") String name
) {}
