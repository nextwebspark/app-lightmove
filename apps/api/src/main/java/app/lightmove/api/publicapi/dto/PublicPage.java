package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of rows, and how many there are in all")
public record PublicPage<T>(
        @Schema(description = "The rows of this page") List<T> data,
        @Schema(description = "This page's number, from 0", example = "0") int page,
        @Schema(description = "Rows per page", example = "25") int size,
        @Schema(description = "Rows across every page", example = "142") long totalCount
) {}
