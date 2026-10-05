package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Every row, unpaged")
public record PublicList<T>(@Schema(description = "The rows") List<T> data) {}
