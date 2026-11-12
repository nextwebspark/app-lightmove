package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PipelineColour;
import app.lightmove.api.candidate.model.CandidateStage;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/** The checks and readings stage and status edits share, so each rule is spelled once. */
final class PipelineRules {

    private static final int MAX_KEY = 40;

    private PipelineRules() {
    }

    /**
     * Someone filed with no status starts at a live status in process, so the last of those never leaves: without
     * one, every door would file new people onto a retired status or straight out of the process.
     */
    static void refuseLeavingNoStart(PipelineCatalog catalog, List<CandidateStatusDefinition> leaving) {
        boolean anotherRemains = catalog.liveStages().stream()
                .flatMap(stage -> catalog.liveStatusesOf(stage).stream())
                .filter(CandidateStatusDefinition::isInProcess)
                .anyMatch(status -> leaving.stream().noneMatch(gone -> gone.getId().equals(status.getId())));
        if (!anotherRemains) {
            throw ApiException.of(ErrorCode.PIPELINE_STATUS_REQUIRED);
        }
    }

    static void refuseRetiredStage(CandidateStage stage) {
        if (stage.isRetired()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That stage is retired");
        }
    }

    static int nextPositionIn(PipelineCatalog catalog, CandidateStage stage) {
        return catalog.statusesOf(stage).stream().mapToInt(CandidateStatusDefinition::getPosition).max().orElse(0) + 1;
    }

    static boolean sameMembers(List<UUID> asked, List<UUID> held) {
        return asked.size() == held.size() && new HashSet<>(asked).equals(new HashSet<>(held));
    }

    static PipelineColour colourOf(String token, PipelineColour fallback) {
        return ApiValueEnum.parse(PipelineColour.class, token, fallback, "pipeline colour");
    }

    static PipelineColour requireColour(String token) {
        return ApiValueEnum.require(PipelineColour.class, token, "pipeline colour");
    }

    /**
     * A wire key from a label — "Waiting on visa" is {@code waitingOnVisa} — numbered past any key the
     * workspace already holds, retired ones included. Written once: a rename never changes it.
     */
    static String freeKey(String label, String fallback, Predicate<String> taken) {
        StringBuilder key = new StringBuilder();
        for (String word : label.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.isEmpty()) {
                continue;
            }
            key.append(key.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        String base = key.isEmpty() || !Character.isLetter(key.charAt(0)) ? fallback + key : key.toString();
        base = base.length() > MAX_KEY - 4 ? base.substring(0, MAX_KEY - 4) : base;
        String candidate = base;
        for (int suffix = 2; taken.test(candidate); suffix++) {
            candidate = base + suffix;
        }
        return candidate;
    }
}
