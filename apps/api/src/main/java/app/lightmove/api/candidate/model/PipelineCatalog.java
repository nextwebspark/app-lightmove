package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateSystemStatus;
import app.lightmove.api.candidate.constant.PipelineStageKind;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A workspace's whole pipeline as one read: its stages in order and every status under them, retired
 * ones included, since a retired status still stands on whoever holds it.
 */
public record PipelineCatalog(List<CandidateStage> stages, List<CandidateStatusDefinition> statuses) {

    public PipelineCatalog {
        stages = stages.stream().sorted(Comparator.comparingInt(CandidateStage::getPosition)).toList();
        statuses = statuses.stream().sorted(CandidateStatusDefinition.PIPELINE_ORDER).toList();
    }

    public Optional<CandidateStatusDefinition> byKey(String statusKey) {
        return statuses.stream().filter(status -> status.getStatusKey().equals(statusKey)).findFirst();
    }

    public Optional<CandidateStage> stageByKey(String stageKey) {
        return stages.stream().filter(stage -> stage.getStageKey().equals(stageKey)).findFirst();
    }

    /** A seeded status is in every workspace's catalog, retired or not, so it is always here. */
    public CandidateStatusDefinition bySystemKey(CandidateSystemStatus system) {
        return statuses.stream().filter(status -> status.is(system)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Pipeline catalog lacks system status " + system));
    }

    public List<CandidateStatusDefinition> statusesOf(CandidateStage stage) {
        return statuses.stream().filter(status -> status.getStage().getId().equals(stage.getId())).toList();
    }

    public List<CandidateStatusDefinition> liveStatusesOf(CandidateStage stage) {
        return statusesOf(stage).stream().filter(status -> !status.isRetired()).toList();
    }

    public Optional<CandidateStatusDefinition> defaultOf(CandidateStage stage) {
        return liveStatusesOf(stage).stream().filter(CandidateStatusDefinition::isDefaultOfStage).findFirst();
    }

    public List<CandidateStage> liveStages() {
        return stages.stream().filter(stage -> !stage.isRetired()).toList();
    }

    /**
     * Where someone filed with no status stands: the first live active stage's default — Identified, in
     * the seeded catalog, which is where every profile has always started.
     */
    public CandidateStatusDefinition defaultStatus() {
        return liveStages().stream()
                .filter(stage -> stage.getKind() == PipelineStageKind.ACTIVE)
                .map(this::defaultOf)
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> bySystemKey(CandidateSystemStatus.IDENTIFIED));
    }

    /** Retired stages are renumbered behind the live ones, so an anchor is read only while its stage is live. */
    public PipelineProgressAnchors progressAnchors() {
        CandidateStage interested = bySystemKey(CandidateSystemStatus.INTERESTED).getStage();
        return new PipelineProgressAnchors(defaultStatus().getStage().getPosition(),
                interested.isRetired() ? Integer.MAX_VALUE : interested.getPosition());
    }

    public List<UUID> statusIdsOfStage(String stageKey) {
        return stageByKey(stageKey)
                .map(stage -> statusesOf(stage).stream().map(CandidateStatusDefinition::getId).toList())
                .orElse(List.of());
    }
}
