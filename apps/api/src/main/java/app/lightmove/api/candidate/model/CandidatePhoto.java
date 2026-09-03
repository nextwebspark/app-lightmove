package app.lightmove.api.candidate.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The profile photo enrichment downloaded for a candidate — bytes in the row, not a provider URL,
 * because those links carry expiring signatures (V44 spells it out). One per candidate, served only
 * by its own endpoint, so the roster reads never touch it.
 */
@Entity
@Table(name = "app_lm_candidate_photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidatePhoto extends BaseEntity {

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    public static CandidatePhoto of(UUID candidateId, EnrichedPhoto photo) {
        CandidatePhoto stored = new CandidatePhoto();
        stored.candidateId = candidateId;
        stored.content = photo.content();
        stored.contentType = photo.contentType();
        return stored;
    }
}
