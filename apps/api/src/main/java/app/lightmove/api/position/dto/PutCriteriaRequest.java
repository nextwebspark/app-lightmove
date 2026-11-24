package app.lightmove.api.position.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Snapshot PUT of the brief's whole criteria list. */
public record PutCriteriaRequest(
        @NotNull
        @Size(max = 30, message = "That is too many criteria")
        List<@Valid CriterionRequest> criteria,
        /**
         * The {@code promptVersion} of the document reading this write carries, sent once, with the write that
         * fills it (#1452). The client's word: counted only against a version that has served.
         */
        @Positive Integer readingVersion
) {}
