package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.PipelineCatalogResponse;
import app.lightmove.api.candidate.dto.PipelineStageResponse;
import app.lightmove.api.candidate.dto.PipelineStatusResponse;
import app.lightmove.api.candidate.model.PipelineCatalog;

/** A workspace's pipeline as Settings → Pipeline stages reads it: every stage in order, retired ones included. */
final class PipelineCatalogMapper {

    private PipelineCatalogMapper() {
    }

    static PipelineCatalogResponse toResponse(PipelineCatalog catalog) {
        return new PipelineCatalogResponse(catalog.stages().stream()
                .map(stage -> new PipelineStageResponse(stage.getId(), stage.getStageKey(), stage.getLabel(),
                        stage.getColour().value(), stage.getPosition(), stage.isSystem(), stage.isRetired(),
                        catalog.statusesOf(stage).stream()
                                .map(status -> new PipelineStatusResponse(status.getId(), status.getStatusKey(),
                                        status.getLabel(), status.getColour().value(), status.getPosition(),
                                        status.isDefaultOfStage(), status.isSystem(), status.isOutOfProcess(),
                                        status.isRetired()))
                                .toList()))
                .toList());
    }
}
