package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Restricts or lifts processing of a person. A restriction needs its reason; {@code suppress} also puts their profile
 * and addresses on the suppression list as an objection, which lifting the restriction leaves in place.
 */
public record ProcessingRestrictionRequest(
        @NotNull Boolean restricted,
        @Size(max = 500) String reason,
        Boolean suppress
) {}
