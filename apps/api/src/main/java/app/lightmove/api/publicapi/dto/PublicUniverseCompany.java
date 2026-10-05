package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "UniverseCompany", description = "A company of the stage, with the executives mapped at it")
public record PublicUniverseCompany(
        @Schema(description = "The company") PublicCompany company,
        @Schema(description = "Its executives, first mapped first; empty where none is mapped")
        List<PublicCandidate> executives
) {}
