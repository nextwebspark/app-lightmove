package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "Universe", description = "One stage of a position: every company, each with its executives")
public record PublicUniverse(
        @Schema(description = "The stage read", allowableValues = {"inUniverse", "shortlisted", "declined"},
                example = "inUniverse")
        String stage,
        @Schema(description = "Every company of the stage, by name") List<PublicUniverseCompany> companies,
        @Schema(description = "Executives at no company of the position. Filled on inUniverse only; empty on the "
                + "other stages")
        List<PublicCandidate> unassigned
) {}
